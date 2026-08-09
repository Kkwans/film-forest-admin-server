package com.filmforest.crawler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import com.filmforest.crawler.entity.CrawlerScheduleGenre;
import com.filmforest.crawler.mapper.CrawlerScheduleGenreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CrawlerScheduleGenreService {

    private final CrawlerScheduleGenreMapper genreMapper;
    private final TagService tagService;
    private final ObjectMapper objectMapper;

    public CrawlerScheduleGenreService(CrawlerScheduleGenreMapper genreMapper,
                                       TagService tagService,
                                       ObjectMapper objectMapper) {
        this.genreMapper = genreMapper;
        this.tagService = tagService;
        this.objectMapper = objectMapper;
    }

    public Selection validate(String contentType, List<Long> rawTagIds) {
        List<Long> tagIds = rawTagIds == null ? List.of() : new LinkedHashSet<>(rawTagIds).stream().toList();
        List<Tag> standardGenres = tagService.getStandardGenres(contentType);
        Set<Long> allowed = standardGenres.stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(tagIds)) {
            throw new IllegalArgumentException("爬虫题材只能选择当前内容类型的系统标准题材");
        }
        List<String> names = standardGenres.stream()
                .filter(tag -> tagIds.contains(tag.getId()))
                .map(Tag::getName)
                .toList();
        try {
            return new Selection(tagIds, names.isEmpty() ? null : objectMapper.writeValueAsString(names));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("无法生成题材兼容投影", impossible);
        }
    }

    @Transactional
    public void replace(Long scheduleId, List<Long> tagIds) {
        genreMapper.delete(new LambdaQueryWrapper<CrawlerScheduleGenre>()
                .eq(CrawlerScheduleGenre::getScheduleId, scheduleId));
        for (Long tagId : tagIds) {
            CrawlerScheduleGenre relation = new CrawlerScheduleGenre();
            relation.setScheduleId(scheduleId);
            relation.setTagId(tagId);
            genreMapper.insert(relation);
        }
    }

    public List<Long> listTagIds(Long scheduleId) {
        return genreMapper.selectList(new LambdaQueryWrapper<CrawlerScheduleGenre>()
                        .eq(CrawlerScheduleGenre::getScheduleId, scheduleId)
                        .orderByAsc(CrawlerScheduleGenre::getTagId))
                .stream()
                .map(CrawlerScheduleGenre::getTagId)
                .toList();
    }

    public record Selection(List<Long> tagIds, String compatibilityJson) {
    }
}
