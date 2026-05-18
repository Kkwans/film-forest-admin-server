package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.mapper.TagMapper;
import com.filmforest.content.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Autowired
    private ContentTagMapper contentTagMapper;

    @Override
    public List<Tag> getAllTags() {
        return list(new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getUsageCount)
                .orderByAsc(Tag::getSortOrder));
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
        tag.setColor(color);
        tag.setSortOrder(0);
        tag.setUsageCount(0);
        save(tag);
        return tag;
    }

    @Override
    @Transactional
    public Tag updateTag(Long id, String name, String color) {
        Tag tag = getById(id);
        if (tag == null) throw new RuntimeException("标签不存在");
        if (name != null) tag.setName(name);
        if (color != null) tag.setColor(color);
        updateById(tag);
        return tag;
    }

    @Override
    @Transactional
    public void setContentTags(Long contentId, String contentType, List<Long> tagIds) {
        // 删除旧关联
        contentTagMapper.delete(new LambdaQueryWrapper<ContentTag>()
                .eq(ContentTag::getContentId, contentId)
                .eq(ContentTag::getContentType, contentType));
        // 添加新关联
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                ContentTag ct = new ContentTag();
                ct.setContentId(contentId);
                ct.setContentType(contentType);
                ct.setTagId(tagId);
                contentTagMapper.insert(ct);
            }
        }
        // 更新使用次数
        updateAllUsageCounts();
    }

    @Override
    public List<Tag> getContentTags(Long contentId, String contentType) {
        List<ContentTag> cts = contentTagMapper.selectList(
                new LambdaQueryWrapper<ContentTag>()
                        .eq(ContentTag::getContentId, contentId)
                        .eq(ContentTag::getContentType, contentType));
        if (cts.isEmpty()) return Collections.emptyList();
        List<Long> tagIds = cts.stream().map(ContentTag::getTagId).collect(Collectors.toList());
        return listByIds(tagIds);
    }

    private void updateAllUsageCounts() {
        List<Tag> tags = list();
        for (Tag tag : tags) {
            Long count = contentTagMapper.selectCount(
                    new LambdaQueryWrapper<ContentTag>().eq(ContentTag::getTagId, tag.getId()));
            tag.setUsageCount(count.intValue());
            updateById(tag);
        }
    }
}
