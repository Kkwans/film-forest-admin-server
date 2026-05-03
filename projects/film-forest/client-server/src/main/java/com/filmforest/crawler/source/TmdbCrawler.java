package com.filmforest.crawler.source;

import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.http.CrawlerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TMDB (The Movie Database) API 爬虫
 * 文档: https://developer.themoviedb.org/
 * 免费 API，支持电影/剧集/综艺等数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbCrawler {

    private final CrawlerEngine crawlerEngine;

    /** TMDB API 基础地址 */
    private static final String TMDB_BASE = "https://api.themoviedb.org/3";

    /** TMDB API Key（需替换为真实 Key，可从 https://www.themoviedb.org/settings/api 注册免费获取） */
    private static final String API_KEY = "YOUR_TMDB_API_KEY";

    /** 海报基础 URL */
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    /** 映射表：TMDB genre_id -> 中文类型 */
    private static final Map<Integer, String> GENRE_MAP = Map.ofEntries(
            Map.entry(28, "动作"), Map.entry(12, "冒险"), Map.entry(16, "动画"),
            Map.entry(35, "喜剧"), Map.entry(80, "犯罪"), Map.entry(99, "纪录片"),
            Map.entry(18, "剧情"), Map.entry(10751, "家庭"), Map.entry(14, "奇幻"),
            Map.entry(36, "历史"), Map.entry(27, "恐怖"), Map.entry(10402, "音乐"),
            Map.entry(9648, "悬疑"), Map.entry(10749, "爱情"), Map.entry(878, "科幻"),
            Map.entry(10770, "电视电影"), Map.entry(53, "惊悚"), Map.entry(37, "西部"),
            Map.entry(10759, "动作冒险"), Map.entry(10762, "儿童"), Map.entry(10763, "新闻"),
            Map.entry(10764, "真人秀"), Map.entry(10765, "Sci-Fi & Fantasy"),
            Map.entry(10766, "肥皂剧"), Map.entry(10767, "脱口秀"), Map.entry(10768, "War & Politics")
    );

    /**
     * 抓取电影列表（TMDB_now playing 作为示例数据源）
     */
    public void crawlMovies(CrawlerSchedule schedule) {
        log.info("[TMDB] 开始抓取电影列表");
        try {
            // 1. 拉取 now_playing 电影（院线热映）
            String nowPlayingUrl = TMDB_BASE + "/movie/now_playing?api_key=" + API_KEY + "&language=zh-CN&page=1";
            Map<String, Object> nowPlaying = fetchTmdb(nowPlayingUrl);
            if (nowPlaying == null) {
                log.error("[TMDB] 获取 now_playing 失败");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) nowPlaying.getOrDefault("results", List.of());
            log.info("[TMDB] 获取到 {} 部正在热映的电影", results.size());

            for (Map<String, Object> item : results) {
                try {
                    Movie movie = parseTmdbMovie(item);
                    movie.setStatus(1);
                    movie.setCreatedAt(LocalDateTime.now());
                    // TODO: 入库逻辑调用 movieService.save(movie)
                    log.debug("[TMDB] 解析电影: {}", movie.getTitle());
                } catch (Exception e) {
                    log.error("[TMDB] 解析电影异常: {}", e.getMessage());
                }
            }

            // 2. 额外拉取 popular 补充数据量
            String popularUrl = TMDB_BASE + "/movie/popular?api_key=" + API_KEY + "&language=zh-CN&page=1";
            Map<String, Object> popular = fetchTmdb(popularUrl);
            if (popular != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> popularResults = (List<Map<String, Object>>) popular.getOrDefault("results", List.of());
                log.info("[TMDB] 获取到 {} 部 popular 电影", popularResults.size());
            }

        } catch (Exception e) {
            log.error("[TMDB] 抓取电影异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 抓取剧集列表（TMDB TV 系列）
     */
    public void crawlDramas(CrawlerSchedule schedule) {
        log.info("[TMDB] 开始抓取剧集列表");
        try {
            String url = TMDB_BASE + "/tv/on_the_air?api_key=" + API_KEY + "&language=zh-CN&page=1";
            Map<String, Object> response = fetchTmdb(url);
            if (response == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getOrDefault("results", List.of());
            log.info("[TMDB] 获取到 {} 部 TV 剧集", results.size());

            for (Map<String, Object> item : results) {
                try {
                    Drama drama = parseTmdbTv(item, "drama");
                    drama.setStatus(1);
                    log.debug("[TMDB] 解析剧集: {}", drama.getTitle());
                } catch (Exception e) {
                    log.error("[TMDB] 解析剧集异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[TMDB] 抓取剧集异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 抓取动漫列表（TMDB Animation 分类）
     */
    public void crawlAnimes(CrawlerSchedule schedule) {
        log.info("[TMDB] 开始抓取动漫列表");
        try {
            // TMDB 动画电影
            String movieUrl = TMDB_BASE + "/discover/movie?api_key=" + API_KEY + "&language=zh-CN&with_genres=16&page=1&sort_by=popularity.desc";
            Map<String, Object> movieResponse = fetchTmdb(movieUrl);
            if (movieResponse != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) movieResponse.getOrDefault("results", List.of());
                log.info("[TMDB] 获取到 {} 部动画电影", results.size());
                for (Map<String, Object> item : results) {
                    try {
                        Anime anime = parseTmdbMovieAsAnime(item);
                        anime.setStatus(1);
                        log.debug("[TMDB] 解析动漫: {}", anime.getTitle());
                    } catch (Exception e) {
                        log.error("[TMDB] 解析动漫异常: {}", e.getMessage());
                    }
                }
            }

            // TMDB 动画 TV 系列
            String tvUrl = TMDB_BASE + "/discover/tv?api_key=" + API_KEY + "&language=zh-CN&with_genres=16&page=1&sort_by=popularity.desc";
            Map<String, Object> tvResponse = fetchTmdb(tvUrl);
            if (tvResponse != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) tvResponse.getOrDefault("results", List.of());
                log.info("[TMDB] 获取到 {} 部动画 TV", results.size());
                for (Map<String, Object> item : results) {
                    try {
                        Anime anime = parseTmdbTvAsAnime(item);
                        anime.setStatus(1);
                        log.debug("[TMDB] 解析动漫TV: {}", anime.getTitle());
                    } catch (Exception e) {
                        log.error("[TMDB] 解析动漫TV异常: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[TMDB] 抓取动漫异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 抓取综艺列表（TMDB Reality TV / Talk TV）
     */
    public void crawlVarieties(CrawlerSchedule schedule) {
        log.info("[TMDB] 开始抓取综艺列表");
        try {
            String url = TMDB_BASE + "/tv/popular?api_key=" + API_KEY + "&language=zh-CN&page=1";
            Map<String, Object> response = fetchTmdb(url);
            if (response == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getOrDefault("results", List.of());
            log.info("[TMDB] 获取到 {} 部 TV popular（用于综艺筛选）", results.size());

            for (Map<String, Object> item : results) {
                try {
                    Variety variety = parseTmdbTvAsVariety(item);
                    variety.setStatus(1);
                    log.debug("[TMDB] 解析综艺: {}", variety.getTitle());
                } catch (Exception e) {
                    log.error("[TMDB] 解析综艺异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[TMDB] 抓取综艺异常: {}", e.getMessage(), e);
        }
    }

    // ==================== 解析方法 ====================

    /**
     * 解析 TMDB movie 对象 -> Movie Entity
     */
    public Movie parseTmdbMovie(Map<String, Object> item) {
        Movie movie = new Movie();
        movie.setTitle((String) item.getOrDefault("title", ""));
        movie.setAlias("[]");
        movie.setPosterUrl(IMAGE_BASE + item.getOrDefault("poster_path", ""));
        movie.setYear(parseYearFromDate((String) item.getOrDefault("release_date", "")));

        // 类型解析
        @SuppressWarnings("unchecked")
        List<Integer> genreIds = (List<Integer>) item.getOrDefault("genre_ids", List.of());
        String genres = genreIds.stream()
                .map(GENRE_MAP::get)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
        movie.setGenre(genres);

        // 地区
        @SuppressWarnings("unchecked")
        List<String> originCountry = (List<String>) item.getOrDefault("origin_country", List.of());
        if (!originCountry.isEmpty()) {
            movie.setRegion(String.join(",", originCountry));
        }

        movie.setStoryline((String) item.getOrDefault("overview", ""));
        Object voteAvg = item.getOrDefault("vote_average", null);
        if (voteAvg != null) {
            movie.setScoreDouban(new BigDecimal(voteAvg.toString()));
        }

        return movie;
    }

    /**
     * 解析 TMDB TV 对象 -> Drama Entity
     */
    public Drama parseTmdbTv(Map<String, Object> item, String type) {
        Drama drama = new Drama();
        drama.setTitle((String) item.getOrDefault("name", ""));
        drama.setAlias("[]");
        drama.setPosterUrl(IMAGE_BASE + item.getOrDefault("poster_path", ""));
        drama.setYear(parseYearFromDate((String) item.getOrDefault("first_air_date", "")));
        drama.setStoryline((String) item.getOrDefault("overview", ""));
        Object voteAvg = item.getOrDefault("vote_average", null);
        if (voteAvg != null) {
            drama.setScoreDouban(new BigDecimal(voteAvg.toString()));
        }
        return drama;
    }

    /**
     * 解析 TMDB movie -> Anime（动画电影）
     */
    public Anime parseTmdbMovieAsAnime(Map<String, Object> item) {
        Anime anime = new Anime();
        anime.setTitle((String) item.getOrDefault("title", ""));
        anime.setPosterUrl(IMAGE_BASE + item.getOrDefault("poster_path", ""));
        anime.setYear(parseYearFromDate((String) item.getOrDefault("release_date", "")));
        anime.setStoryline((String) item.getOrDefault("overview", ""));
        return anime;
    }

    /**
     * 解析 TMDB TV -> Anime（动画剧集）
     */
    public Anime parseTmdbTvAsAnime(Map<String, Object> item) {
        Anime anime = new Anime();
        anime.setTitle((String) item.getOrDefault("name", ""));
        anime.setPosterUrl(IMAGE_BASE + item.getOrDefault("poster_path", ""));
        anime.setYear(parseYearFromDate((String) item.getOrDefault("first_air_date", "")));
        anime.setStoryline((String) item.getOrDefault("overview", ""));
        return anime;
    }

    /**
     * 解析 TMDB TV -> Variety（综艺）
     */
    public Variety parseTmdbTvAsVariety(Map<String, Object> item) {
        Variety variety = new Variety();
        variety.setTitle((String) item.getOrDefault("name", ""));
        variety.setPosterUrl(IMAGE_BASE + item.getOrDefault("poster_path", ""));
        variety.setYear(parseYearFromDate((String) item.getOrDefault("first_air_date", "")));
        variety.setStoryline((String) item.getOrDefault("overview", ""));
        return variety;
    }

    // ==================== 工具方法 ====================

    /**
     * 发送 TMDB API 请求
     */
    private Map<String, Object> fetchTmdb(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate rt = new RestTemplate();
            ResponseEntity<Map> response = rt.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("[TMDB] 请求失败: {}, url: {}", e.getMessage(), url);
            return null;
        }
    }

    /**
     * 从日期字符串提取年份
     */
    private Integer parseYearFromDate(String dateStr) {
        if (dateStr != null && dateStr.length() >= 4) {
            try {
                return Integer.parseInt(dateStr.substring(0, 4));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
