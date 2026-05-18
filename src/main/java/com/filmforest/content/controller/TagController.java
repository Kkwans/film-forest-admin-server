package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端标签管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/tags")
public class TagController {

    @Autowired
    private TagService tagService;

    /** 获取所有标签 */
    @GetMapping
    public Result<?> list() {
        return Result.ok(tagService.getAllTags());
    }

    /** 创建标签 */
    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String color = (String) body.get("color");
        if (name == null || name.trim().isEmpty()) {
            return Result.fail(400, "标签名称不能为空");
        }
        try {
            return Result.ok(tagService.createTag(name.trim(), color));
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 更新标签 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            return Result.ok(tagService.updateTag(id, (String) body.get("name"), (String) body.get("color")));
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 删除标签 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.ok();
    }

    /** 获取内容的标签 */
    @GetMapping("/content/{contentType}/{contentId}")
    public Result<?> getContentTags(@PathVariable String contentType, @PathVariable Long contentId) {
        return Result.ok(tagService.getContentTags(contentId, contentType));
    }

    /** 设置内容的标签 */
    @PutMapping("/content/{contentType}/{contentId}")
    public Result<?> setContentTags(
            @PathVariable String contentType,
            @PathVariable Long contentId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> tagIdNums = (List<Number>) body.get("tagIds");
        List<Long> tagIds = tagIdNums != null
                ? tagIdNums.stream().map(Number::longValue).toList()
                : List.of();
        tagService.setContentTags(contentId, contentType, tagIds);
        return Result.ok();
    }
}
