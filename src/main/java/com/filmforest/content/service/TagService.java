package com.filmforest.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    List<Tag> getAllTags();

    Tag createTag(String name, String color);

    Tag updateTag(Long id, String name, String color);

    void setContentTags(Long contentId, String contentType, List<Long> tagIds);

    List<Tag> getContentTags(Long contentId, String contentType);
}
