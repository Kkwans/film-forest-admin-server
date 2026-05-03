package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.service.ShortDramaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 短剧 Controller
 */
@RestController
@RequestMapping("/api/short-dramas")
public class ShortDramaController {

    @Autowired
    private ShortDramaService shortDramaService;

    /**
     * 短剧列表（分页）
     */
    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre) {
        return Result.ok(shortDramaService.pageList(page, size, year, region, genre));
    }

    /**
     * 短剧详情
     */
    @GetMapping("/{id}")
    public Result<ShortDrama> detail(@PathVariable Long id) {
        ShortDrama shortDrama = shortDramaService.getDetail(id);
        return shortDrama != null ? Result.ok(shortDrama) : Result.fail("短剧不存在");
    }

    /**
     * 新增短剧（管理端）
     */
    @PostMapping
    public Result<?> add(@RequestBody ShortDrama shortDrama) {
        shortDramaService.save(shortDrama);
        return Result.ok();
    }

    /**
     * 更新短剧（管理端）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody ShortDrama shortDrama) {
        shortDrama.setId(id);
        shortDramaService.updateById(shortDrama);
        return Result.ok();
    }

    /**
     * 删除短剧（管理端）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        shortDramaService.removeById(id);
        return Result.ok();
    }
}
