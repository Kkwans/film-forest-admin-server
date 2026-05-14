package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端内容管理 API
 * 对应 admin-ui /content 页面
 *
 * 提供电影、剧集、综艺、动漫、短剧的 CRUD 操作，
 * 以及 genre 列表查询、内容统计、合并列表等辅助接口。
 */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 内容类型 → 数据库表名映射 */
    private static final Map<String, String> CONTENT_TYPE_TABLE_MAP = Map.of(
            "movie", "movie",
            "drama", "drama",
            "variety", "variety",
            "anime", "anime",
            "short", "short_drama"
    );

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

    // ==================== Genre 列表（爬虫配置用） ====================

    /**
     * 获取指定内容类型的所有 genre 标签（去重）
     * 用于爬虫配置的 genre_filter 多选
     *
     * 直接从数据库中提取 genre JSON 字段，解析后去重返回。
     * 不做白名单过滤，保留数据库中实际存在的所有类型。
     */
    @GetMapping("/genres")
    public Result<List<String>> getGenres(@RequestParam String contentType) {
        String table = CONTENT_TYPE_TABLE_MAP.get(contentType);
        if (table == null) {
            return Result.ok(Collections.emptyList());
        }

        try {
            List<String> genreJsons = jdbcTemplate.queryForList(
                    "SELECT genre FROM " + table + " WHERE genre IS NOT NULL AND genre != '[]'",
                    String.class
            );

            Set<String> genres = new TreeSet<>();
            for (String json : genreJsons) {
                try {
                    List<String> arr = JSON_MAPPER.readValue(json, new TypeReference<>() {});
                    genres.addAll(arr);
                } catch (Exception ignored) {
                    // 跳过无法解析的 JSON
                }
            }
            return Result.ok(new ArrayList<>(genres));
        } catch (Exception e) {
            return Result.fail("获取 genre 失败: " + e.getMessage());
        }
    }

    // ==================== 统计 ====================

    /** 获取各类型内容的数量统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("movies", movieService.count());
        stats.put("dramas", dramaService.count());
        stats.put("varieties", varietyService.count());
        stats.put("animes", animeService.count());
        stats.put("shortDramas", shortDramaService.count());
        return Result.ok(stats);
    }

    // ==================== 合并列表（支持类型筛选） ====================

    /**
     * 获取所有类型内容的合并列表（支持按类型筛选）
     * 返回精简的摘要信息，用于管理端列表展示
     */
    @GetMapping("/all")
    public Result<List<Map<String, Object>>> listAll(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Map<String, Object>> results = new ArrayList<>();

        if (type == null || "movie".equals(type)) {
            IPage<Movie> p = movieService.pageList(page, size, null, null, null);
            for (Movie m : p.getRecords()) {
                results.add(toSummaryMap(m.getId(), "movie", m.getTitle(),
                        m.getPosterUrl(), m.getYear(), m.getScoreDouban(), m.getStatus(), m.getCreatedAt()));
            }
        }
        if (type == null || "drama".equals(type)) {
            IPage<Drama> p = dramaService.pageList(page, size, null, null, null);
            for (Drama d : p.getRecords()) {
                results.add(toSummaryMap(d.getId(), "drama", d.getTitle(),
                        d.getPosterUrl(), d.getYear(), d.getScoreDouban(), d.getStatus(), d.getCreatedAt()));
            }
        }
        if (type == null || "variety".equals(type)) {
            IPage<Variety> p = varietyService.pageList(page, size, null, null, null);
            for (Variety v : p.getRecords()) {
                results.add(toSummaryMap(v.getId(), "variety", v.getTitle(),
                        v.getPosterUrl(), v.getYear(), v.getScoreDouban(), v.getStatus(), v.getCreatedAt()));
            }
        }
        if (type == null || "anime".equals(type)) {
            IPage<Anime> p = animeService.pageList(page, size, null, null, null);
            for (Anime a : p.getRecords()) {
                results.add(toSummaryMap(a.getId(), "anime", a.getTitle(),
                        a.getPosterUrl(), a.getYear(), a.getScoreDouban(), a.getStatus(), a.getCreatedAt()));
            }
        }
        if (type == null || "short_drama".equals(type) || "short".equals(type)) {
            IPage<ShortDrama> p = shortDramaService.pageList(page, size, null, null, null);
            for (ShortDrama s : p.getRecords()) {
                results.add(toSummaryMap(s.getId(), "short_drama", s.getTitle(),
                        s.getPosterUrl(), s.getYear(), null, s.getStatus(), s.getCreatedAt()));
            }
        }

        return Result.ok(results);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 将内容记录转换为摘要 Map（用于合并列表接口）
     *
     * @param id        内容 ID
     * @param type      内容类型标识
     * @param title     标题
     * @param posterUrl 海报 URL
     * @param year      年份
     * @param scoreDouban 豆瓣评分（可为 null）
     * @param status    状态
     * @param createdAt 创建时间
     * @return 摘要 Map
     */
    private Map<String, Object> toSummaryMap(Long id, String type, String title,
                                              String posterUrl, Integer year,
                                              Object scoreDouban, String status,
                                              Object createdAt) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("type", type);
        item.put("posterUrl", posterUrl);
        item.put("year", year);
        item.put("scoreDouban", scoreDouban);
        item.put("status", status);
        item.put("createdAt", createdAt);
        return item;
    }
}
