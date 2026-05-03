package com.filmforest.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    @Autowired
    private MovieService movieService;

    /**
     * 电影列表（分页）
     */
    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre) {
        return Result.ok(movieService.pageList(page, size, year, region, genre));
    }

    /**
     * 电影详情
     */
    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        Movie movie = movieService.getDetail(id);
        return movie != null ? Result.ok(movie) : Result.fail("电影不存在");
    }

    /**
     * 新增电影（管理端）
     */
    @PostMapping
    public Result<?> add(@RequestBody Movie movie) {
        movieService.save(movie);
        return Result.ok();
    }

    /**
     * 更新电影（管理端）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Movie movie) {
        movie.setId(id);
        movieService.updateById(movie);
        return Result.ok();
    }

    /**
     * 删除电影（管理端）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        movieService.removeById(id);
        return Result.ok();
    }
}
