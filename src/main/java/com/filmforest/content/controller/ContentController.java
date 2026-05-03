package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端内容管理 API
 * 对应 admin-ui /content 页面
 */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;

    // ==================== 电影 ====================

    @GetMapping("/movies")
    public Result<IPage<Movie>> listMovies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        return Result.ok(movieService.pageList(page, size, year, genre, keyword));
    }

    @GetMapping("/movies/{id}")
    public Result<Movie> getMovie(@PathVariable Long id) {
        Movie m = movieService.getDetail(id);
        return m != null ? Result.ok(m) : Result.fail("电影不存在");
    }

    @PostMapping("/movies")
    public Result<Movie> createMovie(@RequestBody Movie movie) {
        movieService.save(movie);
        return Result.ok(movie);
    }

    @PutMapping("/movies/{id}")
    public Result<Movie> updateMovie(@PathVariable Long id, @RequestBody Movie movie) {
        movie.setId(id);
        movieService.updateById(movie);
        return Result.ok(movie);
    }

    @DeleteMapping("/movies/{id}")
    public Result<Boolean> deleteMovie(@PathVariable Long id) {
        return Result.ok(movieService.removeById(id));
    }

    // ==================== 剧集 ====================

    @GetMapping("/dramas")
    public Result<IPage<Drama>> listDramas(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        return Result.ok(dramaService.pageList(page, size, year, genre, keyword));
    }

    @GetMapping("/dramas/{id}")
    public Result<Drama> getDrama(@PathVariable Long id) {
        Drama d = dramaService.getDetail(id);
        return d != null ? Result.ok(d) : Result.fail("剧集不存在");
    }

    @PostMapping("/dramas")
    public Result<Drama> createDrama(@RequestBody Drama drama) {
        dramaService.save(drama);
        return Result.ok(drama);
    }

    @PutMapping("/dramas/{id}")
    public Result<Drama> updateDrama(@PathVariable Long id, @RequestBody Drama drama) {
        drama.setId(id);
        dramaService.updateById(drama);
        return Result.ok(drama);
    }

    @DeleteMapping("/dramas/{id}")
    public Result<Boolean> deleteDrama(@PathVariable Long id) {
        return Result.ok(dramaService.removeById(id));
    }

    // ==================== 综艺 ====================

    @GetMapping("/varieties")
    public Result<IPage<Variety>> listVarieties(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        return Result.ok(varietyService.pageList(page, size, year, genre, keyword));
    }

    @GetMapping("/varieties/{id}")
    public Result<Variety> getVariety(@PathVariable Long id) {
        Variety v = varietyService.getDetail(id);
        return v != null ? Result.ok(v) : Result.fail("综艺不存在");
    }

    @PostMapping("/varieties")
    public Result<Variety> createVariety(@RequestBody Variety variety) {
        varietyService.save(variety);
        return Result.ok(variety);
    }

    @PutMapping("/varieties/{id}")
    public Result<Variety> updateVariety(@PathVariable Long id, @RequestBody Variety variety) {
        variety.setId(id);
        varietyService.updateById(variety);
        return Result.ok(variety);
    }

    @DeleteMapping("/varieties/{id}")
    public Result<Boolean> deleteVariety(@PathVariable Long id) {
        return Result.ok(varietyService.removeById(id));
    }

    // ==================== 动漫 ====================

    @GetMapping("/animes")
    public Result<IPage<Anime>> listAnimes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        return Result.ok(animeService.pageList(page, size, year, genre, keyword));
    }

    @GetMapping("/animes/{id}")
    public Result<Anime> getAnime(@PathVariable Long id) {
        Anime a = animeService.getDetail(id);
        return a != null ? Result.ok(a) : Result.fail("动漫不存在");
    }

    @PostMapping("/animes")
    public Result<Anime> createAnime(@RequestBody Anime anime) {
        animeService.save(anime);
        return Result.ok(anime);
    }

    @PutMapping("/animes/{id}")
    public Result<Anime> updateAnime(@PathVariable Long id, @RequestBody Anime anime) {
        anime.setId(id);
        animeService.updateById(anime);
        return Result.ok(anime);
    }

    @DeleteMapping("/animes/{id}")
    public Result<Boolean> deleteAnime(@PathVariable Long id) {
        return Result.ok(animeService.removeById(id));
    }

    // ==================== 短剧 ====================

    @GetMapping("/short-dramas")
    public Result<IPage<ShortDrama>> listShortDramas(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String keyword) {
        return Result.ok(shortDramaService.pageList(page, size, year, genre, keyword));
    }

    @GetMapping("/short-dramas/{id}")
    public Result<ShortDrama> getShortDrama(@PathVariable Long id) {
        ShortDrama s = shortDramaService.getDetail(id);
        return s != null ? Result.ok(s) : Result.fail("短剧不存在");
    }

    @PostMapping("/short-dramas")
    public Result<ShortDrama> createShortDrama(@RequestBody ShortDrama shortDrama) {
        shortDramaService.save(shortDrama);
        return Result.ok(shortDrama);
    }

    @PutMapping("/short-dramas/{id}")
    public Result<ShortDrama> updateShortDrama(@PathVariable Long id, @RequestBody ShortDrama shortDrama) {
        shortDrama.setId(id);
        shortDramaService.updateById(shortDrama);
        return Result.ok(shortDrama);
    }

    @DeleteMapping("/short-dramas/{id}")
    public Result<Boolean> deleteShortDrama(@PathVariable Long id) {
        return Result.ok(shortDramaService.removeById(id));
    }

    // ==================== 统计 ====================

    @GetMapping("/stats")
    public Result<java.util.Map<String, Object>> getStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("movies", movieService.count());
        stats.put("dramas", dramaService.count());
        stats.put("varieties", varietyService.count());
        stats.put("animes", animeService.count());
        stats.put("shortDramas", shortDramaService.count());
        return Result.ok(stats);
    }

    // ==================== 合并列表（支持类型筛选） ====================

    @GetMapping("/all")
    public Result<java.util.List<java.util.Map<String, Object>>> listAll(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        // 分类型查询
        if (type == null || type.equals("movie")) {
            IPage<Movie> mp = movieService.pageList(page, size, null, null, null);
            for (Movie m : mp.getRecords()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", m.getId());
                item.put("title", m.getTitle());
                item.put("type", "movie");
                item.put("posterUrl", m.getPosterUrl());
                item.put("year", m.getYear());
                item.put("scoreDouban", m.getScoreDouban());
                item.put("status", m.getStatus());
                item.put("createdAt", m.getCreatedAt());
                results.add(item);
            }
        }
        if (type == null || type.equals("drama")) {
            IPage<Drama> dp = dramaService.pageList(page, size, null, null, null);
            for (Drama d : dp.getRecords()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", d.getId());
                item.put("title", d.getTitle());
                item.put("type", "drama");
                item.put("posterUrl", d.getPosterUrl());
                item.put("year", d.getYear());
                item.put("scoreDouban", d.getScoreDouban());
                item.put("status", d.getStatus());
                item.put("createdAt", d.getCreatedAt());
                results.add(item);
            }
        }
        if (type == null || type.equals("variety")) {
            IPage<Variety> vp = varietyService.pageList(page, size, null, null, null);
            for (Variety v : vp.getRecords()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", v.getId());
                item.put("title", v.getTitle());
                item.put("type", "variety");
                item.put("posterUrl", v.getPosterUrl());
                item.put("year", v.getYear());
                item.put("scoreDouban", v.getScoreDouban());
                item.put("status", v.getStatus());
                item.put("createdAt", v.getCreatedAt());
                results.add(item);
            }
        }
        if (type == null || type.equals("anime")) {
            IPage<Anime> ap = animeService.pageList(page, size, null, null, null);
            for (Anime a : ap.getRecords()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", a.getId());
                item.put("title", a.getTitle());
                item.put("type", "anime");
                item.put("posterUrl", a.getPosterUrl());
                item.put("year", a.getYear());
                item.put("scoreDouban", a.getScoreDouban());
                item.put("status", a.getStatus());
                item.put("createdAt", a.getCreatedAt());
                results.add(item);
            }
        }
        if (type == null || type.equals("short")) {
            IPage<ShortDrama> sp = shortDramaService.pageList(page, size, null, null, null);
            for (ShortDrama s : sp.getRecords()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", s.getId());
                item.put("title", s.getTitle());
                item.put("type", "short");
                item.put("posterUrl", s.getPosterUrl());
                item.put("year", s.getYear());
                item.put("status", s.getStatus());
                item.put("createdAt", s.getCreatedAt());
                results.add(item);
            }
        }
        return Result.ok(results);
    }
}