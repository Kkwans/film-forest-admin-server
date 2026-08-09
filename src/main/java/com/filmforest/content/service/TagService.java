package com.filmforest.content.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.filmforest.content.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    List<Tag> getAllTags();

    List<Tag> getStandardGenres(String contentType);

    List<Tag> resolveSourceGenres(String sourceCode, String contentType, List<String> sourceGenres);

    Tag createTag(String name, String color);

    Tag updateTag(Long id, String name, String color);

    void setContentTags(Long contentId, String contentType, List<Long> tagIds);

    List<Tag> getContentTags(Long contentId, String contentType);
}
