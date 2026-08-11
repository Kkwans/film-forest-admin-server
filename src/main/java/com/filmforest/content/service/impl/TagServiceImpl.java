package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.dto.ContentTagTarget;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.entity.TagContentType;
import com.filmforest.content.entity.TagSourceAlias;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.mapper.TagContentTypeMapper;
import com.filmforest.content.mapper.TagMapper;
import com.filmforest.content.mapper.TagSourceAliasMapper;
import com.filmforest.content.service.TagService;
import com.filmforest.content.service.ContentRecordGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final ContentTagMapper contentTagMapper;
    private final TagContentTypeMapper tagContentTypeMapper;
    private final TagSourceAliasMapper tagSourceAliasMapper;
    private final ContentRecordGuard contentRecordGuard;

    public TagServiceImpl(ContentTagMapper contentTagMapper,
                          TagContentTypeMapper tagContentTypeMapper,
                          TagSourceAliasMapper tagSourceAliasMapper,
                          ContentRecordGuard contentRecordGuard) {
        this.contentTagMapper = contentTagMapper;
        this.tagContentTypeMapper = tagContentTypeMapper;
        this.tagSourceAliasMapper = tagSourceAliasMapper;
        this.contentRecordGuard = contentRecordGuard;
    }

    @Override
    public List<Tag> getAllTags() {
        return list(new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getUsageCount)
                .orderByAsc(Tag::getSortOrder));
    }

    @Override
    public List<Tag> getStandardGenres(String contentType) {
        String canonicalType = canonicalContentType(contentType);
        List<Long> tagIds = tagContentTypeMapper.selectList(
                        new LambdaQueryWrapper<TagContentType>()
                                .eq(TagContentType::getContentType, canonicalType))
                .stream()
                .map(TagContentType::getTagId)
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, tagIds)
                .eq(Tag::getSystemFlag, 1)
                .orderByAsc(Tag::getSortOrder)
                .orderByAsc(Tag::getId));
    }

    @Override
    public List<Tag> resolveSourceGenres(String sourceCode, String contentType, List<String> sourceGenres) {
        if (sourceGenres == null || sourceGenres.isEmpty()) {
            return List.of();
        }
        String canonicalType = canonicalContentType(contentType);
        Map<String, Tag> canonicalByName = getStandardGenres(canonicalType).stream()
                .collect(Collectors.toMap(Tag::getName, tag -> tag));
        Map<String, Tag> aliasMap = new HashMap<>();
        List<TagSourceAlias> aliases = tagSourceAliasMapper.selectList(
                new LambdaQueryWrapper<TagSourceAlias>()
                        .eq(TagSourceAlias::getSourceCode, sourceCode)
                        .eq(TagSourceAlias::getContentType, canonicalType)
                        .in(TagSourceAlias::getAlias, sourceGenres));
        if (!aliases.isEmpty()) {
            Map<Long, Tag> tagsById = listByIds(aliases.stream().map(TagSourceAlias::getTagId).toList())
                    .stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
            aliases.forEach(alias -> aliasMap.put(alias.getAlias(), tagsById.get(alias.getTagId())));
        }
        LinkedHashMap<Long, Tag> resolved = new LinkedHashMap<>();
        for (String rawGenre : sourceGenres) {
            if (rawGenre == null || rawGenre.isBlank()) continue;
            String genre = rawGenre.trim();
            Tag tag = canonicalByName.getOrDefault(genre, aliasMap.get(genre));
            if (tag != null) resolved.putIfAbsent(tag.getId(), tag);
        }
        return new ArrayList<>(resolved.values());
    }

    @Override
    public List<Tag> requireStandardGenres(String contentType, List<Long> tagIds) {
        String canonicalType = canonicalContentType(contentType);
        List<Long> normalizedIds = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<Tag> allowed = getStandardGenres(canonicalType);
        Set<Long> requested = new LinkedHashSet<>(normalizedIds);
        Set<Long> allowedIds = allowed.stream().map(Tag::getId).collect(Collectors.toSet());
        if (!allowedIds.containsAll(requested)) {
            throw new IllegalArgumentException("题材必须来自当前内容类型的系统标准选项");
        }
        return allowed.stream().filter(tag -> requested.contains(tag.getId())).toList();
    }

    @Override
    @Transactional
    public Tag createTag(String name, String color) {
        Tag existing = getOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
        if (existing != null) {
            throw new RuntimeException("标签「" + name + "」已存在");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setCode("custom-" + UUID.randomUUID().toString().replace("-", ""));
        tag.setColor(color);
        tag.setSortOrder(0);
        tag.setUsageCount(0);
        tag.setSystem(0);
        save(tag);
        return tag;
    }

    @Override
    @Transactional
    public Tag updateTag(Long id, String name, String color) {
        Tag tag = getById(id);
        if (tag == null) throw new RuntimeException("标签不存在");
        if (Integer.valueOf(1).equals(tag.getSystem()) && name != null && !name.equals(tag.getName())) {
            throw new RuntimeException("系统题材名称不可修改");
        }
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        updateById(tag);
        return tag;
    }

    @Override
    @Transactional
    public void setContentTags(Long contentId, String contentType, List<Long> tagIds) {
        String canonicalType = canonicalContentType(contentType);
        contentRecordGuard.requireActiveRecord(canonicalType, contentId);
        List<Long> normalizedTagIds = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        Set<Long> allowedIds = getStandardGenres(canonicalType).stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());
        List<Tag> requestedTags = normalizedTagIds.isEmpty() ? List.of() : listByIds(normalizedTagIds);
        if (requestedTags.size() != normalizedTagIds.size()) {
            throw new IllegalArgumentException("包含不存在的标签");
        }
        boolean containsInapplicableSystemGenre = requestedTags.stream()
                .anyMatch(tag -> Integer.valueOf(1).equals(tag.getSystem()) && !allowedIds.contains(tag.getId()));
        if (containsInapplicableSystemGenre) {
            throw new IllegalArgumentException("题材必须来自当前内容类型的系统标准选项");
        }
        // 删除旧关联
        contentTagMapper.delete(new LambdaQueryWrapper<ContentTag>()
                .eq(ContentTag::getContentId, contentId)
                .eq(ContentTag::getContentType, canonicalType));
        // 添加新关联
        for (Long tagId : normalizedTagIds) {
            ContentTag ct = new ContentTag();
            ct.setContentId(contentId);
            ct.setContentType(canonicalType);
            ct.setTagId(tagId);
            contentTagMapper.insert(ct);
        }
        // 更新使用次数
        updateAllUsageCounts();
    }

    @Override
    @Transactional
    public void setContentGenres(Long contentId, String contentType, List<Long> tagIds) {
        String canonicalType = canonicalContentType(contentType);
        contentRecordGuard.requireActiveRecord(canonicalType, contentId);
        List<Tag> selectedGenres = requireStandardGenres(canonicalType, tagIds);
        List<Long> allSystemTagIds = list(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getSystemFlag, 1))
                .stream()
                .map(Tag::getId)
                .toList();
        if (!allSystemTagIds.isEmpty()) {
            contentTagMapper.delete(new LambdaQueryWrapper<ContentTag>()
                    .eq(ContentTag::getContentId, contentId)
                    .eq(ContentTag::getContentType, canonicalType)
                    .in(ContentTag::getTagId, allSystemTagIds));
        }
        for (Tag genre : selectedGenres) {
            ContentTag contentTag = new ContentTag();
            contentTag.setContentId(contentId);
            contentTag.setContentType(canonicalType);
            contentTag.setTagId(genre.getId());
            contentTagMapper.insert(contentTag);
        }
        updateAllUsageCounts();
    }

    @Override
    public List<Tag> getContentTags(Long contentId, String contentType) {
        String canonicalType = canonicalContentType(contentType);
        List<ContentTag> cts = contentTagMapper.selectList(
                new LambdaQueryWrapper<ContentTag>()
                        .eq(ContentTag::getContentId, contentId)
                        .eq(ContentTag::getContentType, canonicalType));
        if (cts.isEmpty()) return Collections.emptyList();
        List<Long> tagIds = cts.stream().map(ContentTag::getTagId).collect(Collectors.toList());
        return listByIds(tagIds);
    }

    @Override
    public Map<String, List<Tag>> getContentTagsBatch(List<ContentTagTarget> targets) {
        if (targets == null || targets.isEmpty()) return Map.of();
        if (targets.size() > 100) {
            throw new IllegalArgumentException("批量查询内容不能超过 100 项");
        }

        LinkedHashMap<String, NormalizedTarget> normalized = new LinkedHashMap<>();
        for (ContentTagTarget target : targets) {
            if (target == null || target.contentId() == null || target.contentId() <= 0) {
                throw new IllegalArgumentException("内容 ID 必须为正整数");
            }
            String canonicalType = canonicalContentType(target.contentType());
            String responseKey = target.contentType().trim() + "-" + target.contentId();
            normalized.putIfAbsent(responseKey,
                    new NormalizedTarget(responseKey, canonicalType, target.contentId()));
        }

        Map<String, List<Long>> idsByType = normalized.values().stream()
                .collect(Collectors.groupingBy(
                        NormalizedTarget::contentType,
                        LinkedHashMap::new,
                        Collectors.mapping(NormalizedTarget::contentId, Collectors.toList())));
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContentTag> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.and(nested -> {
            boolean first = true;
            for (Map.Entry<String, List<Long>> entry : idsByType.entrySet()) {
                if (!first) nested.or();
                nested.nested(group -> group
                        .eq("content_type", entry.getKey())
                        .in("content_id", entry.getValue()));
                first = false;
            }
        });
        List<ContentTag> relations = contentTagMapper.selectList(wrapper);
        List<Long> tagIds = relations.stream().map(ContentTag::getTagId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Tag> tagsById = tagIds.isEmpty()
                ? Map.of()
                : listByIds(tagIds).stream().collect(Collectors.toMap(Tag::getId, tag -> tag));

        Map<String, Map<Long, List<Tag>>> grouped = relations.stream()
                .filter(relation -> relation.getContentType() != null && relation.getContentId() != null)
                .collect(Collectors.groupingBy(
                        ContentTag::getContentType,
                        Collectors.groupingBy(
                                ContentTag::getContentId,
                                Collectors.mapping(ContentTag::getTagId,
                                        Collectors.collectingAndThen(Collectors.toList(), ids -> ids.stream()
                                                .map(tagsById::get)
                                                .filter(Objects::nonNull)
                                                .distinct()
                                                .toList())))));

        LinkedHashMap<String, List<Tag>> result = new LinkedHashMap<>();
        normalized.values().forEach(target -> result.put(
                target.responseKey(),
                grouped.getOrDefault(target.contentType(), Map.of())
                        .getOrDefault(target.contentId(), List.of())));
        return result;
    }

    private static String canonicalContentType(String contentType) {
        String canonical = "short".equals(contentType) ? "short_drama" : contentType;
        if (!Set.of("movie", "drama", "variety", "anime", "short_drama").contains(canonical)) {
            throw new IllegalArgumentException("不支持的内容类型: " + contentType);
        }
        return canonical;
    }

    private record NormalizedTarget(String responseKey, String contentType, Long contentId) {
    }

    private void updateAllUsageCounts() {
        // 使用 GROUP BY 单次查询获取各 tag 的使用次数（替代 N+1 查询）
        List<java.util.Map<String, Object>> rows = contentTagMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContentTag>()
                        .select("tag_id", "COUNT(*) AS cnt")
                        .groupBy("tag_id"));
        java.util.Map<Long, Long> countMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : rows) {
            Long tagId = ((Number) row.get("tag_id")).longValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            countMap.put(tagId, cnt);
        }
        List<Tag> tags = list();
        for (Tag tag : tags) {
            Long count = countMap.getOrDefault(tag.getId(), 0L);
            if (tag.getUsageCount() == null || tag.getUsageCount() != count.intValue()) {
                tag.setUsageCount(count.intValue());
                updateById(tag);
            }
        }
    }
}
