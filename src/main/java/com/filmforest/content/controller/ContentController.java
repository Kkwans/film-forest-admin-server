package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.common.dto.PageResult;
import com.filmforest.content.dto.AdminContentItem;
import com.filmforest.content.entity.*;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ContentController.class);

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdminContentQueryService adminContentQueryService;
    @Autowired private AdminContentMutationService adminContentMutationService;
    @Autowired private TagService tagService;

    /** 内容类型 → 数据库表名映射 */
    private static final Map<String, String> CONTENT_TYPE_TABLE_MAP = Map.of(
            "movie", "movie",
            "drama", "drama",
            "variety", "variety",
            "anime", "anime",
            "short", "short_drama"
    );

    // ==================== 状态切换（通用） ====================

    /**
     * 通用状态切换接口
     * 只更新 status 字段，不影响其他数据
     */
    @PatchMapping("/{type}/{id}/status")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> toggleStatus(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam int status) {
        String table = CONTENT_TYPE_TABLE_MAP.get(type);
        if (table == null) {
            return Result.fail("不支持的内容类型: " + type);
        }
        if (!CONTENT_TYPE_TABLE_MAP.containsValue(table)) {
            return Result.fail("不支持的内容类型: " + type);
        }
        if (!ContentStatus.isValid(status)) {
            return Result.fail("非法的状态值: " + status + "，只允许 0、1 或 2");
        }
        try {
            int rows = jdbcTemplate.update(
                    "UPDATE " + table + " SET status = ?, updated_at = NOW() WHERE id = ?",
                    status, id
            );
            if (rows > 0) {
                log.info("切换内容状态: type={}, id={}, status={}", type, id, status);
                return Result.ok(true);
            }
            return Result.fail("内容不存在");
        } catch (Exception e) {
            log.error("切换状态失败: type={}, id={}", type, id, e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }

    // ==================== 内容管理 API（管理端） ====================

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
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Movie> createMovie(@Valid @RequestBody Movie movie) {
        movie = adminContentMutationService.createMovie(movie);
        log.info("创建电影: id={}, title={}", movie.getId(), movie.getTitle());
        return Result.ok(movie);
    }

    @PutMapping("/movies/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Movie> updateMovie(@PathVariable Long id, @Valid @RequestBody Movie movie) {
        Movie updated = adminContentMutationService.updateMovie(id, movie);
        log.info("更新电影: id={}, title={}", id, movie.getTitle());
        return Result.ok(updated);
    }

    @DeleteMapping("/movies/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> deleteMovie(@PathVariable Long id) {
        log.info("删除电影: id={}", id);
        boolean ok = movieService.removeById(id);
        return ok ? Result.ok(true) : Result.fail("电影不存在或删除失败");
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
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Drama> createDrama(@Valid @RequestBody Drama drama) {
        drama = adminContentMutationService.createDrama(drama);
        log.info("创建剧集: id={}, title={}", drama.getId(), drama.getTitle());
        return Result.ok(drama);
    }

    @PutMapping("/dramas/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Drama> updateDrama(@PathVariable Long id, @Valid @RequestBody Drama drama) {
        Drama updated = adminContentMutationService.updateDrama(id, drama);
        log.info("更新剧集: id={}, title={}", id, drama.getTitle());
        return Result.ok(updated);
    }

    @DeleteMapping("/dramas/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> deleteDrama(@PathVariable Long id) {
        log.info("删除剧集: id={}", id);
        boolean ok = dramaService.removeById(id);
        return ok ? Result.ok(true) : Result.fail("剧集不存在或删除失败");
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
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Variety> createVariety(@Valid @RequestBody Variety variety) {
        variety = adminContentMutationService.createVariety(variety);
        log.info("创建综艺: id={}, title={}", variety.getId(), variety.getTitle());
        return Result.ok(variety);
    }

    @PutMapping("/varieties/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Variety> updateVariety(@PathVariable Long id, @Valid @RequestBody Variety variety) {
        Variety updated = adminContentMutationService.updateVariety(id, variety);
        log.info("更新综艺: id={}, title={}", id, variety.getTitle());
        return Result.ok(updated);
    }

    @DeleteMapping("/varieties/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> deleteVariety(@PathVariable Long id) {
        log.info("删除综艺: id={}", id);
        boolean ok = varietyService.removeById(id);
        return ok ? Result.ok(true) : Result.fail("综艺不存在或删除失败");
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
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Anime> createAnime(@Valid @RequestBody Anime anime) {
        anime = adminContentMutationService.createAnime(anime);
        log.info("创建动漫: id={}, title={}", anime.getId(), anime.getTitle());
        return Result.ok(anime);
    }

    @PutMapping("/animes/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Anime> updateAnime(@PathVariable Long id, @Valid @RequestBody Anime anime) {
        Anime updated = adminContentMutationService.updateAnime(id, anime);
        log.info("更新动漫: id={}, title={}", id, anime.getTitle());
        return Result.ok(updated);
    }

    @DeleteMapping("/animes/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> deleteAnime(@PathVariable Long id) {
        log.info("删除动漫: id={}", id);
        boolean ok = animeService.removeById(id);
        return ok ? Result.ok(true) : Result.fail("动漫不存在或删除失败");
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
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<ShortDrama> createShortDrama(@Valid @RequestBody ShortDrama shortDrama) {
        shortDrama = adminContentMutationService.createShortDrama(shortDrama);
        log.info("创建短剧: id={}, title={}", shortDrama.getId(), shortDrama.getTitle());
        return Result.ok(shortDrama);
    }

    @PutMapping("/short-dramas/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<ShortDrama> updateShortDrama(@PathVariable Long id, @Valid @RequestBody ShortDrama shortDrama) {
        ShortDrama updated = adminContentMutationService.updateShortDrama(id, shortDrama);
        log.info("更新短剧: id={}, title={}", id, shortDrama.getTitle());
        return Result.ok(updated);
    }

    @DeleteMapping("/short-dramas/{id}")
    @CacheEvict(value = {"stats", "genres"}, allEntries = true)
    public Result<Boolean> deleteShortDrama(@PathVariable Long id) {
        log.info("删除短剧: id={}", id);
        boolean ok = shortDramaService.removeById(id);
        return ok ? Result.ok(true) : Result.fail("短剧不存在或删除失败");
    }

    // ==================== Genre 列表（爬虫配置用） ====================

    /**
     * 获取指定内容类型的所有 genre 标签（去重）
     * 用于爬虫配置的 genre_filter 多选
     *
     * 仅返回系统标准题材，不再从历史 genre JSON 推导自由文本选项。
     */
    @GetMapping("/genres")
    @Cacheable(value = "genres", key = "#contentType")
    public Result<List<String>> getGenres(@RequestParam String contentType) {
        String table = CONTENT_TYPE_TABLE_MAP.get(contentType);
        if (table == null) {
            return Result.ok(Collections.emptyList());
        }

        // 二次校验表名必须在白名单内，防止 SQL 注入
        if (!CONTENT_TYPE_TABLE_MAP.containsValue(table)) {
            log.warn("非法的 contentType 请求: {}", contentType);
            return Result.ok(Collections.emptyList());
        }

        try {
            List<String> genres = tagService.getStandardGenres(contentType).stream()
                    .map(Tag::getName)
                    .toList();
            log.debug("获取标准 genre 列表: contentType={}, 共 {} 个", contentType, genres.size());
            return Result.ok(genres);
        } catch (Exception e) {
            log.error("获取 genre 列表失败: contentType={}", contentType, e);
            return Result.fail("获取 genre 失败: " + e.getMessage());
        }
    }

    // ==================== 统计 ====================

    /** 获取各类型内容的数量统计 */
    @GetMapping("/stats")
    @Cacheable(value = "stats", key = "'content_stats'")
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
    public Result<PageResult<AdminContentItem>> listAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return Result.ok(adminContentQueryService.search(
                    type, status, keyword, sort, sortDir, page, size));
        } catch (IllegalArgumentException exception) {
            return Result.fail(400, exception.getMessage());
        }
    }
}
