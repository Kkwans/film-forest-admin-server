package com.filmforest.resource.service.impl;

import com.filmforest.common.type.ContentType;
import com.filmforest.resource.dto.ResourcePageQuery;
import com.filmforest.resource.dto.ResourceContentContext;
import com.filmforest.resource.entity.ResourceAdminStatus;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import com.filmforest.resource.service.ResourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceOnlineMapper, ResourceOnline>
        implements ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "contentId", "title", "sort");

    private final ResourceMagnetMapper magnetMapper;
    private final ResourceCloudMapper cloudMapper;
    private final ResourceSourceMapper sourceMapper;
    private final JdbcTemplate jdbcTemplate;

    public ResourceServiceImpl(ResourceOnlineMapper onlineMapper,
                              ResourceMagnetMapper magnetMapper, ResourceCloudMapper cloudMapper,
                              ResourceSourceMapper sourceMapper) {
        this(onlineMapper, magnetMapper, cloudMapper, sourceMapper, null);
    }

    @Autowired
    public ResourceServiceImpl(ResourceOnlineMapper onlineMapper,
                              ResourceMagnetMapper magnetMapper, ResourceCloudMapper cloudMapper,
                              ResourceSourceMapper sourceMapper, JdbcTemplate jdbcTemplate) {
        this.baseMapper = onlineMapper;
        this.magnetMapper = magnetMapper;
        this.cloudMapper = cloudMapper;
        this.sourceMapper = sourceMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== 在线资源 =====
    @Override
    public List<ResourceOnline> listOnlineResources(String contentType, Long contentId) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(StringUtils.isNotBlank(contentType), ResourceOnline::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceOnline::getContentId, contentId)
                .eq(ResourceOnline::getEnabled, 1)
                .isNull(ResourceOnline::getRemovedAt)
                .orderByAsc(ResourceOnline::getSort));
    }

    @Override
    public IPage<ResourceOnline> pageOnline(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceOnline> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        LambdaQueryWrapper<ResourceOnline> wrapper = new LambdaQueryWrapper<ResourceOnline>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceOnline::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceOnline::getContentId, query.getContentId())
                .eq(source != null, ResourceOnline::getSourceCode, source)
                .and(keyword != null, nested -> nested
                        .like(ResourceOnline::getEpisodeTitle, keyword)
                        .or().like(ResourceOnline::getSourceName, keyword)
                        .or().like(ResourceOnline::getSourceUrl, keyword));
        applyOnlineStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyOnlineSort(wrapper, query);
        IPage<ResourceOnline> result = page(page, wrapper);
        enrichOnline(result.getRecords());
        return result;
    }

    @Override
    public List<ResourceOnline> listOnlineByContentType(String contentType) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, normalizeContentType(contentType))
                .eq(ResourceOnline::getEnabled, 1)
                .isNull(ResourceOnline::getRemovedAt)
                .orderByDesc(ResourceOnline::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    @Transactional
    public ResourceOnline saveOnlineResource(ResourceOnline resource) {
        resource.setContentType(normalizeContentType(resource.getContentType()));
        resource.setSourceCode(normalizeSourceCode(resource.getSourceCode()));
        resource.setSourceName(requireText(resource.getSourceName(), "来源名称"));
        resource.setSourceUrl(normalizeHttpUrl(resource.getSourceUrl(), true, "播放 URL"));
        validateEnabled(resource.getEnabled());
        if (resource.getId() == null) {
            resource.setSourcePageUrl(normalizeHttpUrl(resource.getSourcePageUrl(), false, "来源详情页 URL"));
            resource.setPlaybackType(normalizePlaybackType(resource.getPlaybackType(), resource.getSourceUrl()));
            if ("EXTERNAL_PAGE".equals(resource.getPlaybackType()) && resource.getSourcePageUrl() == null) {
                resource.setSourcePageUrl(resource.getSourceUrl());
            }
            if (resource.getEnabled() == null) resource.setEnabled(1);
            if (resource.getSort() == null) resource.setSort(0);
            clearCrawlerMetadata(resource);
            resource.setCreatedAt(LocalDateTime.now());
            save(resource);
        } else {
            ResourceOnline stored = requireOnline(resource.getId());
            if (resource.getSourceCode() == null) resource.setSourceCode(stored.getSourceCode());
            String sourcePageUrl = resource.getSourcePageUrl() == null
                    ? stored.getSourcePageUrl()
                    : normalizeHttpUrl(resource.getSourcePageUrl(), false, "来源详情页 URL");
            String playbackType = resource.getPlaybackType() == null
                    ? normalizePlaybackType(stored.getPlaybackType(), resource.getSourceUrl())
                    : normalizePlaybackType(resource.getPlaybackType(), resource.getSourceUrl());
            if ("EXTERNAL_PAGE".equals(playbackType) && sourcePageUrl == null) {
                sourcePageUrl = resource.getSourceUrl();
            }
            resource.setSourcePageUrl(sourcePageUrl);
            resource.setPlaybackType(playbackType);
            UpdateWrapper<ResourceOnline> update = new UpdateWrapper<ResourceOnline>()
                    .eq("id", resource.getId())
                    .set("content_type", resource.getContentType())
                    .set("content_id", resource.getContentId())
                    .set("source_code", resource.getSourceCode())
                    .set("season", resource.getSeason())
                    .set("episode_number", resource.getEpisodeNumber())
                    .set("episode_title", trimToNull(resource.getEpisodeTitle()))
                    .set("source_name", resource.getSourceName())
                    .set("source_url", resource.getSourceUrl())
                    .set("source_page_url", sourcePageUrl)
                    .set("playback_type", playbackType)
                    .set("sort", resource.getSort() == null ? 0 : resource.getSort())
                    .set(resource.getEnabled() != null, "enabled", resource.getEnabled())
                    .set("updated_at", LocalDateTime.now());
            update(update);
        }
        return resource;
    }

    @Override
    public boolean deleteOnlineResource(Long id) {
        return removeById(id);
    }

    @Override
    public boolean setOnlineEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceOnline> update = new UpdateWrapper<ResourceOnline>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return update(update);
    }

    // ===== 磁力资源 =====
    @Override
    public List<ResourceMagnet> listMagnetResources(String contentType, Long contentId) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(StringUtils.isNotBlank(contentType), ResourceMagnet::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceMagnet::getContentId, contentId)
                .eq(ResourceMagnet::getEnabled, 1)
                .isNull(ResourceMagnet::getRemovedAt)
                .orderByAsc(ResourceMagnet::getSort));
    }

    @Override
    public List<ResourceMagnet> listMagnetByContentType(String contentType) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, normalizeContentType(contentType))
                .eq(ResourceMagnet::getEnabled, 1)
                .isNull(ResourceMagnet::getRemovedAt)
                .orderByDesc(ResourceMagnet::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    @Transactional
    public ResourceMagnet saveMagnetResource(ResourceMagnet resource) {
        resource.setContentType(normalizeContentType(resource.getContentType()));
        resource.setSourceCode(normalizeSourceCode(resource.getSourceCode()));
        validateEnabled(resource.getEnabled());
        if (resource.getId() == null) {
            if (resource.getEnabled() == null) resource.setEnabled(1);
            if (resource.getSort() == null) resource.setSort(0);
            clearCrawlerMetadata(resource);
            resource.setCreatedAt(LocalDateTime.now());
            magnetMapper.insert(resource);
        } else {
            ResourceMagnet stored = requireMagnet(resource.getId());
            if (resource.getSourceCode() == null) resource.setSourceCode(stored.getSourceCode());
            UpdateWrapper<ResourceMagnet> update = new UpdateWrapper<ResourceMagnet>()
                    .eq("id", resource.getId())
                    .set("content_type", resource.getContentType())
                    .set("content_id", resource.getContentId())
                    .set("source_code", resource.getSourceCode())
                    .set("title", trimToNull(resource.getTitle()))
                    .set("magnet_url", resource.getMagnetUrl().trim())
                    .set("resolution", trimToNull(resource.getResolution()))
                    .set("has_subtitle", Boolean.TRUE.equals(resource.getHasSubtitle()) ? 1 : 0)
                    .set("is_special_sub", Boolean.TRUE.equals(resource.getIsSpecialSub()) ? 1 : 0)
                    .set("sort", resource.getSort() == null ? 0 : resource.getSort())
                    .set(resource.getEnabled() != null, "enabled", resource.getEnabled())
                    .set("updated_at", LocalDateTime.now());
            magnetMapper.update(null, update);
        }
        return resource;
    }

    @Override
    public boolean deleteMagnetResource(Long id) {
        return magnetMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<ResourceMagnet> pageMagnet(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceMagnet> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        String resolution = trimToNull(query.getResolution());
        LambdaQueryWrapper<ResourceMagnet> wrapper = new LambdaQueryWrapper<ResourceMagnet>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceMagnet::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceMagnet::getContentId, query.getContentId())
                .eq(source != null, ResourceMagnet::getSourceCode, source)
                .eq(resolution != null, ResourceMagnet::getResolution, resolution)
                .and(keyword != null, nested -> nested.like(ResourceMagnet::getTitle, keyword)
                        .or().like(ResourceMagnet::getMagnetUrl, keyword)
                        .or().like(ResourceMagnet::getRawText, keyword));
        applyMagnetStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyMagnetSort(wrapper, query);
        IPage<ResourceMagnet> result = magnetMapper.selectPage(page, wrapper);
        enrichMagnets(result.getRecords());
        return result;
    }

    @Override
    public boolean setMagnetEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceMagnet> update = new UpdateWrapper<ResourceMagnet>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return magnetMapper.update(null, update) > 0;
    }

    // ===== 网盘资源 =====
    @Override
    public List<ResourceCloud> listCloudResources(String contentType, Long contentId) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(StringUtils.isNotBlank(contentType), ResourceCloud::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceCloud::getContentId, contentId)
                .eq(ResourceCloud::getEnabled, 1)
                .isNull(ResourceCloud::getRemovedAt)
                .orderByAsc(ResourceCloud::getSort));
    }

    @Override
    public List<ResourceCloud> listCloudByContentType(String contentType) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, normalizeContentType(contentType))
                .eq(ResourceCloud::getEnabled, 1)
                .isNull(ResourceCloud::getRemovedAt)
                .orderByDesc(ResourceCloud::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    @Transactional
    public ResourceCloud saveCloudResource(ResourceCloud resource) {
        resource.setContentType(normalizeContentType(resource.getContentType()));
        resource.setSourceCode(normalizeSourceCode(resource.getSourceCode()));
        validateEnabled(resource.getEnabled());
        if (resource.getId() == null) {
            if (resource.getEnabled() == null) resource.setEnabled(1);
            if (resource.getSort() == null) resource.setSort(0);
            clearCrawlerMetadata(resource);
            resource.setCreatedAt(LocalDateTime.now());
            cloudMapper.insert(resource);
        } else {
            ResourceCloud stored = requireCloud(resource.getId());
            if (resource.getSourceCode() == null) resource.setSourceCode(stored.getSourceCode());
            UpdateWrapper<ResourceCloud> update = new UpdateWrapper<ResourceCloud>()
                    .eq("id", resource.getId())
                    .set("content_type", resource.getContentType())
                    .set("content_id", resource.getContentId())
                    .set("source_code", resource.getSourceCode())
                    .set("disk_type", trimToNull(resource.getDiskType()))
                    .set("title", trimToNull(resource.getTitle()))
                    .set("url", resource.getUrl().trim())
                    .set("password", trimToNull(resource.getPassword()))
                    .set("sort", resource.getSort() == null ? 0 : resource.getSort())
                    .set(resource.getEnabled() != null, "enabled", resource.getEnabled())
                    .set("updated_at", LocalDateTime.now());
            cloudMapper.update(null, update);
        }
        return resource;
    }

    @Override
    public boolean deleteCloudResource(Long id) {
        return cloudMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<ResourceCloud> pageCloud(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceCloud> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        String diskType = trimToNull(query.getDiskType());
        LambdaQueryWrapper<ResourceCloud> wrapper = new LambdaQueryWrapper<ResourceCloud>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceCloud::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceCloud::getContentId, query.getContentId())
                .eq(source != null, ResourceCloud::getSourceCode, source)
                .eq(diskType != null, ResourceCloud::getDiskType, diskType)
                .and(keyword != null, nested -> nested.like(ResourceCloud::getTitle, keyword)
                        .or().like(ResourceCloud::getUrl, keyword)
                        .or().like(ResourceCloud::getRawText, keyword));
        applyCloudStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyCloudSort(wrapper, query);
        IPage<ResourceCloud> result = cloudMapper.selectPage(page, wrapper);
        enrichClouds(result.getRecords());
        return result;
    }

    @Override
    public boolean setCloudEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceCloud> update = new UpdateWrapper<ResourceCloud>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return cloudMapper.update(null, update) > 0;
    }

    /** 给管理端分页结果补齐关联内容摘要；失败时保留资源本身，不影响资源操作。 */
    private void enrichOnline(Collection<ResourceOnline> records) {
        Set<ContentRef> refs = records.stream()
                .filter(item -> item.getContentId() != null)
                .map(item -> new ContentRef(item.getContentType(), item.getContentId()))
                .collect(java.util.stream.Collectors.toSet());
        Map<ContentRef, ResourceContentContext> contexts = loadContentContexts(refs);
        records.forEach(item -> apply(item, contexts.get(new ContentRef(item.getContentType(), item.getContentId()))));
    }

    private void enrichMagnets(Collection<ResourceMagnet> records) {
        Set<ContentRef> refs = records.stream()
                .filter(item -> item.getContentId() != null)
                .map(item -> new ContentRef(item.getContentType(), item.getContentId()))
                .collect(java.util.stream.Collectors.toSet());
        Map<ContentRef, ResourceContentContext> contexts = loadContentContexts(refs);
        records.forEach(item -> apply(item, contexts.get(new ContentRef(item.getContentType(), item.getContentId()))));
    }

    private void enrichClouds(Collection<ResourceCloud> records) {
        Set<ContentRef> refs = records.stream()
                .filter(item -> item.getContentId() != null)
                .map(item -> new ContentRef(item.getContentType(), item.getContentId()))
                .collect(java.util.stream.Collectors.toSet());
        Map<ContentRef, ResourceContentContext> contexts = loadContentContexts(refs);
        records.forEach(item -> apply(item, contexts.get(new ContentRef(item.getContentType(), item.getContentId()))));
    }

    private Map<ContentRef, ResourceContentContext> loadContentContexts(Collection<ContentRef> refs) {
        Map<ContentRef, ResourceContentContext> result = new HashMap<>();
        if (jdbcTemplate == null || refs.isEmpty()) return result;
        Map<String, List<Long>> grouped = new HashMap<>();
        refs.forEach(ref -> grouped.computeIfAbsent(ref.contentType(), ignored -> new ArrayList<>())
                .add(ref.contentId()));
        grouped.forEach((contentType, rawIds) -> {
            String table = contentTable(contentType);
            if (table == null) return;
            List<Long> ids = rawIds.stream().distinct().toList();
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            String sql = "SELECT id, title, alias, poster_url, year, release_date FROM " + table
                    + " WHERE is_deleted = 0 AND id IN (" + placeholders + ")";
            try {
                jdbcTemplate.query(sql, ids.toArray(), (rs, rowNum) -> {
                    Object yearValue = rs.getObject("year");
                    result.put(new ContentRef(contentType, rs.getLong("id")),
                            new ResourceContentContext(rs.getString("title"),
                                    rs.getString("alias"), rs.getString("poster_url"),
                                    yearValue instanceof Number number ? number.intValue() : null,
                                    rs.getString("release_date")));
                    return null;
                });
            } catch (RuntimeException error) {
                log.warn("加载资源关联内容摘要失败: contentType={}, count={}, error={}",
                        contentType, ids.size(), error.getClass().getSimpleName());
            }
        });
        return result;
    }

    private static String contentTable(String contentType) {
        return switch (contentType) {
            case "movie", "drama", "variety", "anime", "short_drama" -> contentType;
            default -> null;
        };
    }

    private static void apply(ResourceOnline resource, ResourceContentContext context) {
        if (context == null) return;
        resource.setContentTitle(context.title());
        resource.setContentAlias(context.alias());
        resource.setContentPosterUrl(context.posterUrl());
        resource.setContentYear(context.year());
        resource.setContentReleaseDate(context.releaseDate());
    }

    private static void apply(ResourceMagnet resource, ResourceContentContext context) {
        if (context == null) return;
        resource.setContentTitle(context.title());
        resource.setContentAlias(context.alias());
        resource.setContentPosterUrl(context.posterUrl());
        resource.setContentYear(context.year());
        resource.setContentReleaseDate(context.releaseDate());
    }

    private static void apply(ResourceCloud resource, ResourceContentContext context) {
        if (context == null) return;
        resource.setContentTitle(context.title());
        resource.setContentAlias(context.alias());
        resource.setContentPosterUrl(context.posterUrl());
        resource.setContentYear(context.year());
        resource.setContentReleaseDate(context.releaseDate());
    }

    private record ContentRef(String contentType, Long contentId) {
    }

    // ===== 资源来源 =====
    @Override
    public List<ResourceSource> listSources() {
        return sourceMapper.selectList(new LambdaQueryWrapper<ResourceSource>()
                .orderByAsc(ResourceSource::getSort));
    }

    @Override
    @Transactional
    public ResourceSource saveSource(ResourceSource source) {
        String normalizedCode = normalizeRequiredSourceCode(source.getCode());
        source.setCode(normalizedCode);
        validateEnabled(source.getEnabled());
        if (source.getId() == null) {
            if (source.getEnabled() == null) source.setEnabled(0);
            if (source.getSort() == null) source.setSort(0);
            source.setCreatedAt(LocalDateTime.now());
            sourceMapper.insert(source);
        } else {
            ResourceSource stored = requireSource(source.getId());
            if (!stored.getCode().equals(normalizedCode)) {
                throw new IllegalArgumentException("来源编码保存后不可修改");
            }
            UpdateWrapper<ResourceSource> update = new UpdateWrapper<ResourceSource>()
                    .eq("id", source.getId())
                    .set("name", source.getName().trim())
                    .set("url", source.getUrl().trim())
                    .set("enabled", source.getEnabled() == null ? stored.getEnabled() : source.getEnabled())
                    .set("sort", source.getSort() == null ? 0 : source.getSort())
                    .set("updated_at", LocalDateTime.now());
            sourceMapper.update(null, update);
        }
        return source;
    }

    @Override
    @Transactional
    public boolean deleteSource(Long id) {
        ResourceSource source = requireSource(id);
        if ("pkmp4".equals(source.getCode())) {
            throw new IllegalArgumentException("七味网生产来源不可删除，可按需禁用");
        }
        try {
            return sourceMapper.deleteById(id) > 0;
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("来源已被爬虫适配器、计划或资源引用，无法删除", exception);
        }
    }

    @Override
    public boolean toggleSource(Long id, boolean enabled) {
        ResourceSource source = sourceMapper.selectById(id);
        if (source == null) return false;
        source.setEnabled(enabled ? 1 : 0);
        return sourceMapper.updateById(source) > 0;
    }

    // ===== 统计 =====
    @Override
    public long countOnline() {
        return count();
    }

    @Override
    public long countMagnet() {
        return magnetMapper.selectCount(null);
    }

    @Override
    public long countCloud() {
        return cloudMapper.selectCount(null);
    }

    @Override
    public long countTodayNew() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        // 3 次 COUNT 查询（已是 count 语义，无 N+1 问题）
        long online = count(new LambdaQueryWrapper<ResourceOnline>()
                .ge(ResourceOnline::getCreatedAt, start));
        long magnet = magnetMapper.selectCount(new LambdaQueryWrapper<ResourceMagnet>()
                .ge(ResourceMagnet::getCreatedAt, start));
        long cloud = cloudMapper.selectCount(new LambdaQueryWrapper<ResourceCloud>()
                .ge(ResourceCloud::getCreatedAt, start));
        return online + magnet + cloud;
    }

    @Override
    public java.util.Map<String, Long> countOnlineByContentType() {
        // 使用 GROUP BY 单次查询获取各类型数量（替代 5 次 list().size()，每类型最多 200 条 → 单次 COUNT）
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        result.put("movie", 0L);
        result.put("drama", 0L);
        result.put("variety", 0L);
        result.put("anime", 0L);
        result.put("short_drama", 0L);
        try {
            // 使用 QueryWrapper（非 Lambda）支持原始 SQL 列名
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResourceOnline> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.select("content_type", "COUNT(*) AS cnt").groupBy("content_type");
            java.util.List<java.util.Map<String, Object>> rows = getBaseMapper().selectMaps(wrapper);
            for (java.util.Map<String, Object> row : rows) {
                String type = (String) row.get("content_type");
                Long cnt = ((Number) row.get("cnt")).longValue();
                result.put(type, cnt);
            }
        } catch (Exception e) {
            // fallback: 逐个 count（不含完整记录加载）
            result.put("movie", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "movie")));
            result.put("drama", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "drama")));
            result.put("variety", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "variety")));
            result.put("anime", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "anime")));
            result.put("short_drama", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "short_drama")));
        }
        return result;
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) return null;
        String canonical = "short".equals(value.trim()) ? "short_drama" : value.trim();
        return ContentType.fromValue(canonical)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + value))
                .value();
    }

    private static void validatePageQuery(ResourcePageQuery query) {
        if (query == null) throw new IllegalArgumentException("资源分页参数不能为空");
        if (query.getPage() < 1) throw new IllegalArgumentException("页码必须大于 0");
        if (query.getSize() < 1 || query.getSize() > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String normalizeSourceCode(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequiredSourceCode(String value) {
        String normalized = normalizeSourceCode(value);
        if (normalized == null || !normalized.matches("[a-z0-9][a-z0-9_-]{1,49}")) {
            throw new IllegalArgumentException("来源编码需为 2-50 位小写字母、数字、下划线或短横线");
        }
        return normalized;
    }

    private static void validateEnabled(Integer enabled) {
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("启用状态只允许 0 或 1");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(field + "不能为空");
        return normalized;
    }

    private static String normalizeHttpUrl(String value, boolean required, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            if (required) throw new IllegalArgumentException(field + "不能为空");
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getRawAuthority() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(field + "只允许有效的 HTTP/HTTPS 地址");
            }
            return normalized;
        } catch (IllegalArgumentException invalid) {
            if (invalid.getMessage() != null && invalid.getMessage().startsWith(field)) throw invalid;
            throw new IllegalArgumentException(field + "只允许有效的 HTTP/HTTPS 地址");
        }
    }

    private static String normalizePlaybackType(String value, String sourceUrl) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            String lowerUrl = sourceUrl.toLowerCase(Locale.ROOT);
            if (lowerUrl.contains(".m3u8")) return "HLS";
            if (lowerUrl.matches(".*\\.(mp4|webm|ogg)(?:[?#].*)?$")) return "VIDEO";
            return "EXTERNAL_PAGE";
        }
        String type = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("HLS", "VIDEO", "EMBED", "EXTERNAL_PAGE").contains(type)) {
            throw new IllegalArgumentException("播放类型只允许 HLS、VIDEO、EMBED 或 EXTERNAL_PAGE");
        }
        return type;
    }

    private ResourceOnline requireOnline(Long id) {
        ResourceOnline stored = getById(id);
        if (stored == null) throw new IllegalArgumentException("在线资源不存在: " + id);
        return stored;
    }

    private ResourceMagnet requireMagnet(Long id) {
        ResourceMagnet stored = magnetMapper.selectById(id);
        if (stored == null) throw new IllegalArgumentException("磁力资源不存在: " + id);
        return stored;
    }

    private ResourceCloud requireCloud(Long id) {
        ResourceCloud stored = cloudMapper.selectById(id);
        if (stored == null) throw new IllegalArgumentException("网盘资源不存在: " + id);
        return stored;
    }

    private ResourceSource requireSource(Long id) {
        ResourceSource source = sourceMapper.selectById(id);
        if (source == null) throw new IllegalArgumentException("资源来源不存在: " + id);
        return source;
    }

    private static void clearCrawlerMetadata(ResourceOnline resource) {
        resource.setResourceKey(null);
        resource.setRawText(null);
        resource.setLastSeenAt(null);
        resource.setRemovedAt(null);
        resource.setDeleted(null);
    }

    private static void clearCrawlerMetadata(ResourceMagnet resource) {
        resource.setResourceKey(null);
        resource.setRawText(null);
        resource.setLastSeenAt(null);
        resource.setRemovedAt(null);
        resource.setDeleted(null);
    }

    private static void clearCrawlerMetadata(ResourceCloud resource) {
        resource.setResourceKey(null);
        resource.setRawText(null);
        resource.setLastSeenAt(null);
        resource.setRemovedAt(null);
        resource.setDeleted(null);
    }

    private static boolean ascending(ResourcePageQuery query) {
        if (!"asc".equalsIgnoreCase(query.getOrder()) && !"desc".equalsIgnoreCase(query.getOrder())) {
            throw new IllegalArgumentException("排序方向只允许 asc 或 desc");
        }
        return "asc".equalsIgnoreCase(query.getOrder());
    }

    private static String sortField(ResourcePageQuery query) {
        String field = StringUtils.defaultIfBlank(query.getSort(), "createdAt");
        if (!SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("不支持的资源排序字段: " + field);
        }
        return field;
    }

    private static void applyOnlineStatus(LambdaQueryWrapper<ResourceOnline> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceOnline::getEnabled, 1).isNull(ResourceOnline::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceOnline::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceOnline::getRemovedAt);
    }

    private static void applyMagnetStatus(LambdaQueryWrapper<ResourceMagnet> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceMagnet::getEnabled, 1).isNull(ResourceMagnet::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceMagnet::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceMagnet::getRemovedAt);
    }

    private static void applyCloudStatus(LambdaQueryWrapper<ResourceCloud> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceCloud::getEnabled, 1).isNull(ResourceCloud::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceCloud::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceCloud::getRemovedAt);
    }

    private static void applyOnlineSort(LambdaQueryWrapper<ResourceOnline> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceOnline::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceOnline::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceOnline::getEpisodeTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceOnline::getSort);
            default -> wrapper.orderBy(true, asc, ResourceOnline::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceOnline::getId);
    }

    private static void applyMagnetSort(LambdaQueryWrapper<ResourceMagnet> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceMagnet::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceMagnet::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceMagnet::getTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceMagnet::getSort);
            default -> wrapper.orderBy(true, asc, ResourceMagnet::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceMagnet::getId);
    }

    private static void applyCloudSort(LambdaQueryWrapper<ResourceCloud> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceCloud::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceCloud::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceCloud::getTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceCloud::getSort);
            default -> wrapper.orderBy(true, asc, ResourceCloud::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceCloud::getId);
    }
}
