package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.service.AnimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 动漫 Controller
 */
@RestController
@RequestMapping("/api/animes")
public class AnimeController {

    @Autowired
    private AnimeService animeService;

    /**
     * 动漫列表（分页）
     */
    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre) {
        return Result.ok(animeService.pageList(page, size, year, region, genre));
    }

    /**
     * 动漫详情
     */
    @GetMapping("/{id}")
    public Result<Anime> detail(@PathVariable Long id) {
        Anime anime = animeService.getDetail(id);
        return anime != null ? Result.ok(anime) : Result.fail("动漫不存在");
    }

    /**
     * 新增动漫（管理端）
     */
    @PostMapping
    public Result<?> add(@RequestBody Anime anime) {
        animeService.save(anime);
        return Result.ok();
    }

    /**
     * 更新动漫（管理端）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Anime anime) {
        anime.setId(id);
        animeService.updateById(anime);
        return Result.ok();
    }

    /**
     * 删除动漫（管理端）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        animeService.removeById(id);
        return Result.ok();
    }
}
