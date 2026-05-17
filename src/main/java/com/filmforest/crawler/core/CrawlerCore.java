package com.filmforest.crawler.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.filmforest.common.util.StorylineCleaner;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class CrawlerCore {

    private static final String BASE_URL = "https://www.pkmp4.xyz";
    private static final int TIMEOUT_MS = 15000;
    private static final int RETRY_TIMES = 2;
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 7890;

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;
    @Autowired private CrawlerScheduleService scheduleService;
    @Autowired private CrawlerTaskLogMapper taskLogMapper;
    @Autowired private ResourceMagnetMapper magnetMapper;
    @Autowired private ResourceOnlineMapper onlineMapper;
    @Autowired private ResourceCloudMapper cloudMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");
    private final Pattern ID_PATTERN = Pattern.compile("/mv/(\\d+)");
    private final ConcurrentHashMap<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();
    private volatile Boolean proxyAvailable = null;

    /** 速率限制：请求间隔毫秒数（由 schedule.rateLimitMs 配置） */
    private volatile int rateLimitMs = 0;

    // ========== Async Entry Point ==========

    public void executeCrawl(Long scheduleId, Long logId, AtomicBoolean stopFlag) {
        CrawlerSchedule schedule = scheduleService.getSchedule(scheduleId);
        CrawlerTaskLog taskLog = taskLogMapper.selectById(logId);
        if (schedule == null || taskLog == null) return;


        try {
            int added = 0, updated = 0, total = 0;
            String type = schedule.getContentType();
            int batchSize = schedule.getBatchSize() != null ? schedule.getBatchSize() : 20;

            if (stopFlag != null && stopFlag.get()) {
                log.info("[CrawlerCore] Stop requested for schedule {}", scheduleId);
                taskLog.setStatus("stopped");
                taskLog.setFinishedAt(LocalDateTime.now());
                taskLogMapper.updateById(taskLog);
                CrawlerSchedule s2 = scheduleService.getSchedule(scheduleId);
                if (s2 != null) { s2.setStatus("idle"); scheduleService.saveSchedule(s2); }
                return;
            }

            // 断点续爬：从上次停止的页码继续
            int startPage = schedule.getLastCrawledPage() != null ? schedule.getLastCrawledPage() : 1;
            if (startPage < 1) startPage = 1;
            // 应用速率限制
            this.rateLimitMs = schedule.getRateLimitMs() != null ? Math.max(0, schedule.getRateLimitMs()) : 0;
            // 解析 genreFilter（JSON 数组字符串）
            Set<String> genreFilter = parseGenreFilter(schedule.getGenreFilter());
            log.info("[CrawlerCore] Starting crawl for type={}, startPage={}, batchSize={}, rateLimitMs={}, genreFilter={}", type, startPage, batchSize, rateLimitMs, genreFilter);

            if ("movie".equals(type)) {
                int[] r = crawlMovieList(startPage, batchSize, stopFlag, scheduleId, genreFilter);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("drama".equals(type)) {
                int[] r = crawlDramaList(startPage, batchSize, stopFlag, scheduleId, genreFilter);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("variety".equals(type)) {
                int[] r = crawlVarietyList(startPage, batchSize, stopFlag, scheduleId, genreFilter);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("anime".equals(type)) {
                int[] r = crawlAnimeList(startPage, batchSize, stopFlag, scheduleId, genreFilter);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("short_drama".equals(type) || "short".equals(type)) {
                int[] r = crawlShortDramaList(startPage, batchSize, stopFlag, scheduleId, genreFilter);
                added = r[0]; updated = r[1]; total = r[2];
            }

            taskLog.setItemsCrawled(total);
            taskLog.setItemsAdded(added);
            taskLog.setItemsUpdated(updated);
            taskLog.setStatus("success");
            taskLog.setDurationMs((int) java.time.Duration.between(taskLog.getStartedAt(), LocalDateTime.now()).toMillis());
            taskLog.setFinishedAt(LocalDateTime.now());
            taskLogMapper.updateById(taskLog);

            schedule.setStatus("idle");
            schedule.setLastRunTime(LocalDateTime.now());
            schedule.setTotalRuns(schedule.getTotalRuns() + 1);
            schedule.setTotalItems(schedule.getTotalItems() + total);
            // 爬取成功，重置断点
            resetCrawlProgress(scheduleId);
            scheduleService.saveSchedule(schedule);

        } catch (Exception e) {
            log.error("Crawl error scheduleId={}", scheduleId, e);
            taskLog.setStatus("failed");
            taskLog.setErrorMessage(e.getMessage());
            taskLog.setFinishedAt(LocalDateTime.now());
            taskLogMapper.updateById(taskLog);

            CrawlerSchedule s2 = scheduleService.getSchedule(scheduleId);
            if (s2 != null) {
                s2.setStatus("idle");
                scheduleService.saveSchedule(s2);
            }
        } finally {
            runningTasks.remove(scheduleId);
        }
    }

    // ========== Movie Crawler ==========

    // 七味网真实 URL 规则
    // 类型页: /vt/{type}.html (1=电影,2=剧集,3=综艺,4=动漫,30=短剧)
    // 分页: /vt/{type}-{page}.html
    // 详情页: /mv/{id}.html

    private String getListUrl(String type, int page) {
        return BASE_URL + "/vt/" + type + (page > 1 ? "-" + page : "") + ".html";
    }

    public int[] crawlMovieList(int startPage, int maxItems, AtomicBoolean stopFlag, Long scheduleId, Set<String> genreFilter) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            String listUrl = getListUrl("1", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            Set<String> seenUrls = new HashSet<>();
            Elements links = listDoc.select("a[href^='/mv/']");
            if (links.isEmpty()) break;

            for (Element link : links) {
                if (total >= maxItems) break;
                String href = link.attr("href");
                if (!href.matches("/mv/\\d+\\.html")) continue;
                if (seenUrls.contains(href)) continue;
                seenUrls.add(href);
                String detailUrl = BASE_URL + href;
                if (stopFlag != null && stopFlag.get()) break;
                int[] r = crawlMovieDetail(detailUrl, stopFlag, genreFilter);
                log.debug("crawlMovieDetail completed for: {} -> added:{} updated:{}", detailUrl, r[0], r[1]);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            // 断点续爬：保存当前页码
            saveCrawlProgress(scheduleId, page, 0L);
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlMovieDetail(String detailUrl, AtomicBoolean stopFlag, Set<String> genreFilter) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            if (title.isEmpty()) {
                log.warn("Empty title for {}, skipping", detailUrl);
                return new int[]{0, 0, 0};
            }

            String posterUrl = null;
            Element img = doc.selectFirst("div.img img");
            if (img != null) posterUrl = img.attr("abs:src");
            if (posterUrl == null) {
                Element ogImg = doc.selectFirst("meta[property=og:image]");
                if (ogImg != null) posterUrl = ogImg.attr("content");
            }

            Integer year = null;
            String storyline = null;
            Element h1El = doc.selectFirst("h1");
            if (h1El != null) {
                String h1Text = h1El.text();
                Matcher ym = YEAR_PATTERN.matcher(h1Text);
                if (ym.find()) year = Integer.parseInt(ym.group(1));
            }
            storyline = extractStoryline(doc);

            // 主演/导演: 从 .text-overflow 区域提取
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");

            // 类型/地区: 从 tag 链接提取 (<a href="/ms/1---剧情--------.html">剧情</a>)
            String genre = extractGenresFromTags(doc);
            // 类型筛选：如果配置了 genreFilter 且当前内容的类型不在过滤列表中，跳过
            if (genreFilter != null && !genreFilter.isEmpty() && !matchesGenreFilter(genre, genreFilter)) {
                log.debug("Skipping {} due to genre filter", detailUrl);
                return new int[]{0, 0, 0};
            }
            String region = toJsonArray(extractRegionFromTags(doc));

            // 评分: 优先从豆瓣链接提取，fallback 到 meta description
            BigDecimal score = extractScore(doc);
            BigDecimal imdbScore = extractImdbScore(doc);

            // 新增字段
            String language = extractLanguage(doc);
            Integer duration = extractDuration(doc);
            String releaseDate = extractReleaseDate(doc);
            String alias = extractAlias(doc);
            String writer = extractWriter(doc);

            Long contentId = extractContentId(detailUrl);
            Movie existing = movieService.getById(contentId);
            boolean isNew = (existing == null);

            Movie movie = new Movie();
            movie.setId(contentId);
            movie.setTitle(title);
            movie.setPosterUrl(posterUrl);
            movie.setYear(year);
            movie.setStoryline(storyline);
            movie.setActor(toJsonArray(actor));
            movie.setDirector(toJsonArray(director));
            movie.setWriter(toJsonArray(writer));
            movie.setGenre(toJsonArray(genre));
            movie.setRegion(region);
            movie.setLanguage(language);
            movie.setDuration(duration);
            movie.setReleaseDate(releaseDate);
            movie.setAlias(alias);
            movie.setScoreDouban(score);
            movie.setScoreImdb(imdbScore);
            movie.setStatus(1);

            if (isNew) {
                movieService.save(movie);
            } else {
                movieService.updateById(movie);
            }
            Long dbId = movie.getId();
            extractMovieResources(doc, "movie", dbId);

            return new int[]{isNew ? 1 : 0, isNew ? 0 : 1, 0};
        } catch (Exception e) {
            log.error("Movie detail parse error: {} - {}", detailUrl, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    // ========== Drama Crawler ==========

    // type=2 剧集列表: /vt/2.html, /vt/2-2.html
    public int[] crawlDramaList(int startPage, int maxItems, AtomicBoolean stopFlag, Long scheduleId, Set<String> genreFilter) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            String listUrl = getListUrl("2", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            Set<String> seenUrls = new HashSet<>();
            Elements links = listDoc.select("a[href^='/mv/']");
            if (links.isEmpty()) break;

            for (Element link : links) {
                if (total >= maxItems) break;
                String href = link.attr("href");
                if (!href.matches("/mv/\\d+\\.html")) continue;
                if (seenUrls.contains(href)) continue;
                seenUrls.add(href);
                String detailUrl = BASE_URL + href;
                if (stopFlag != null && stopFlag.get()) break;
                int[] r = crawlDramaDetail(detailUrl, stopFlag, genreFilter);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            saveCrawlProgress(scheduleId, page, 0L);
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlDramaDetail(String detailUrl, AtomicBoolean stopFlag, Set<String> genreFilter) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();

            String posterUrl = null;
            Element img = doc.selectFirst("div.img img");
            if (img == null) img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");

            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            String region = toJsonArray(extractRegionFromTags(doc));
            // 类型筛选：如果配置了 genreFilter 且当前内容的类型不在过滤列表中，跳过
            if (genreFilter != null && !genreFilter.isEmpty() && !matchesGenreFilter(genre, genreFilter)) {
                log.debug("Skipping {} due to genre filter", detailUrl);
                return new int[]{0, 0, 0};
            }
            BigDecimal score = extractScore(doc);
            BigDecimal imdbScore = extractImdbScore(doc);
            Integer totalEpisode = extractEpisodeCount(doc);
            String language = extractLanguage(doc);
            Integer duration = extractDuration(doc);
            String releaseDate = extractReleaseDate(doc);
            String alias = extractAlias(doc);
            String writer = extractWriter(doc);

            Long contentId = extractContentId(detailUrl);
            Drama existing = dramaService.getById(contentId);
            boolean isNew = (existing == null);

            Drama drama = new Drama();
            drama.setId(contentId);
            drama.setTitle(title);
            drama.setPosterUrl(posterUrl);
            drama.setYear(year);
            drama.setStoryline(storyline);
            drama.setActor(toJsonArray(actor));
            drama.setDirector(toJsonArray(director));
            drama.setWriter(toJsonArray(writer));
            drama.setGenre(toJsonArray(genre));
            drama.setRegion(region);
            drama.setLanguage(language);
            drama.setDuration(duration);
            drama.setReleaseDate(releaseDate);
            drama.setAlias(alias);
            drama.setScoreDouban(score);
            drama.setScoreImdb(imdbScore);
            drama.setTotalEpisode(totalEpisode);
            drama.setStatus(1);

            if (isNew) {
                dramaService.save(drama);
            } else {
                dramaService.updateById(drama);
            }
            extractMovieResources(doc, "drama", drama.getId());
            extractEpisodes(doc, "drama", drama.getId());
            return new int[]{isNew ? 1 : 0, isNew ? 0 : 1, 0};
        } catch (Exception e) {
            log.error("Drama detail parse error: {} - {}", detailUrl, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    // ========== Variety Crawler ==========

    // type=3 综艺列表
    public int[] crawlVarietyList(int startPage, int maxItems, AtomicBoolean stopFlag, Long scheduleId, Set<String> genreFilter) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            String listUrl = getListUrl("3", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            Set<String> seenUrls = new HashSet<>();
            Elements links = listDoc.select("a[href^='/mv/']");
            if (links.isEmpty()) break;

            for (Element link : links) {
                if (total >= maxItems) break;
                String href = link.attr("href");
                if (!href.matches("/mv/\\d+\\.html")) continue;
                if (seenUrls.contains(href)) continue;
                seenUrls.add(href);
                String detailUrl = BASE_URL + href;
                if (stopFlag != null && stopFlag.get()) break;
                int[] r = crawlVarietyDetail(detailUrl, stopFlag, genreFilter);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            saveCrawlProgress(scheduleId, page, 0L);
            page++;
        }
        return new int[]{added, updated, total};
    }

    // type=4 动漫列表
    public int[] crawlAnimeList(int startPage, int maxItems, AtomicBoolean stopFlag, Long scheduleId, Set<String> genreFilter) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            String listUrl = getListUrl("4", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            Set<String> seenUrls = new HashSet<>();
            Elements links = listDoc.select("a[href^='/mv/']");
            if (links.isEmpty()) break;

            for (Element link : links) {
                if (total >= maxItems) break;
                String href = link.attr("href");
                if (!href.matches("/mv/\\d+\\.html")) continue;
                if (seenUrls.contains(href)) continue;
                seenUrls.add(href);
                String detailUrl = BASE_URL + href;
                if (stopFlag != null && stopFlag.get()) break;
                int[] r = crawlAnimeDetail(detailUrl, stopFlag, genreFilter);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            saveCrawlProgress(scheduleId, page, 0L);
            page++;
        }
        return new int[]{added, updated, total};
    }

    // type=30 短剧列表
    public int[] crawlShortDramaList(int startPage, int maxItems, AtomicBoolean stopFlag, Long scheduleId, Set<String> genreFilter) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            String listUrl = getListUrl("30", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            Set<String> seenUrls = new HashSet<>();
            Elements links = listDoc.select("a[href^='/mv/']");
            if (links.isEmpty()) break;

            for (Element link : links) {
                if (total >= maxItems) break;
                String href = link.attr("href");
                if (!href.matches("/mv/\\d+\\.html")) continue;
                if (seenUrls.contains(href)) continue;
                seenUrls.add(href);
                String detailUrl = BASE_URL + href;
                if (stopFlag != null && stopFlag.get()) break;
                int[] r = crawlShortDramaDetail(detailUrl, stopFlag, genreFilter);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            saveCrawlProgress(scheduleId, page, 0L);
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlVarietyDetail(String detailUrl, AtomicBoolean stopFlag, Set<String> genreFilter) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.img img");
            if (img == null) img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            String region = toJsonArray(extractRegionFromTags(doc));
            if (genreFilter != null && !genreFilter.isEmpty() && !matchesGenreFilter(genre, genreFilter)) {
                log.debug("Skipping {} due to genre filter", detailUrl);
                return new int[]{0, 0, 0};
            }
            BigDecimal score = extractScore(doc);
            BigDecimal imdbScore = extractImdbScore(doc);
            Integer totalEpisode = extractEpisodeCount(doc);
            String language = extractLanguage(doc);
            Integer duration = extractDuration(doc);
            String releaseDate = extractReleaseDate(doc);
            String alias = extractAlias(doc);
            String writer = extractWriter(doc);

            Long contentId = extractContentId(detailUrl);
            Variety existing = varietyService.getById(contentId);
            boolean isNew = (existing == null);

            Variety variety = new Variety();
            variety.setId(contentId);
            variety.setTitle(title);
            variety.setPosterUrl(posterUrl);
            variety.setYear(year);
            variety.setStoryline(storyline);
            variety.setActor(toJsonArray(actor));
            variety.setDirector(toJsonArray(director));
            variety.setWriter(toJsonArray(writer));
            variety.setGenre(toJsonArray(genre));
            variety.setRegion(region);
            variety.setLanguage(language);
            variety.setDuration(duration);
            variety.setReleaseDate(releaseDate);
            variety.setAlias(alias);
            variety.setScoreDouban(score);
            variety.setScoreImdb(imdbScore);
            variety.setTotalEpisode(totalEpisode);
            variety.setStatus(1);

            if (isNew) {
                varietyService.save(variety);
            } else {
                varietyService.updateById(variety);
            }
            extractMovieResources(doc, "variety", variety.getId());
            extractEpisodes(doc, "variety", variety.getId());
            return new int[]{isNew ? 1 : 0, isNew ? 0 : 1, 0};
        } catch (Exception e) {
            log.error("Variety detail parse error: {}", detailUrl, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    // ========== Resource Extraction ==========

    public int[] crawlAnimeDetail(String detailUrl, AtomicBoolean stopFlag, Set<String> genreFilter) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.img img");
            if (img == null) img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            String region = toJsonArray(extractRegionFromTags(doc));
            if (genreFilter != null && !genreFilter.isEmpty() && !matchesGenreFilter(genre, genreFilter)) {
                log.debug("Skipping {} due to genre filter", detailUrl);
                return new int[]{0, 0, 0};
            }
            Integer totalEpisode = extractEpisodeCount(doc);
            String language = extractLanguage(doc);
            Integer duration = extractDuration(doc);
            String releaseDate = extractReleaseDate(doc);
            String alias = extractAlias(doc);
            String writer = extractWriter(doc);
            BigDecimal imdbScore = extractImdbScore(doc);

            Long contentId = extractContentId(detailUrl);
            Anime existing = animeService.getById(contentId);
            boolean isNew = (existing == null);

            Anime anime = new Anime();
            anime.setId(contentId);
            anime.setTitle(title);
            anime.setPosterUrl(posterUrl);
            anime.setYear(year);
            anime.setStoryline(storyline);
            anime.setActor(toJsonArray(actor));
            anime.setDirector(toJsonArray(director));
            anime.setWriter(toJsonArray(writer));
            anime.setGenre(toJsonArray(genre));
            anime.setRegion(region);
            anime.setLanguage(language);
            anime.setDuration(duration);
            anime.setReleaseDate(releaseDate);
            anime.setAlias(alias);
            anime.setScoreImdb(imdbScore);
            anime.setTotalEpisode(totalEpisode);
            anime.setStatus(1);

            if (isNew) {
                animeService.save(anime);
            } else {
                animeService.updateById(anime);
            }
            extractMovieResources(doc, "anime", anime.getId());
            extractEpisodes(doc, "anime", anime.getId());
            return new int[]{isNew ? 1 : 0, isNew ? 0 : 1, 0};
        } catch (Exception e) {
            log.error("Anime detail parse error: {}", detailUrl, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    public int[] crawlShortDramaDetail(String detailUrl, AtomicBoolean stopFlag, Set<String> genreFilter) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.img img");
            if (img == null) img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            String region = toJsonArray(extractRegionFromTags(doc));
            if (genreFilter != null && !genreFilter.isEmpty() && !matchesGenreFilter(genre, genreFilter)) {
                log.debug("Skipping {} due to genre filter", detailUrl);
                return new int[]{0, 0, 0};
            }
            Integer totalEpisode = extractEpisodeCount(doc);
            String language = extractLanguage(doc);
            Integer duration = extractDuration(doc);
            String releaseDate = extractReleaseDate(doc);
            String alias = extractAlias(doc);
            String writer = extractWriter(doc);
            BigDecimal imdbScore = extractImdbScore(doc);

            Long contentId = extractContentId(detailUrl);
            ShortDrama existing = shortDramaService.getById(contentId);
            boolean isNew = (existing == null);

            ShortDrama shortDrama = new ShortDrama();
            shortDrama.setId(contentId);
            shortDrama.setTitle(title);
            shortDrama.setPosterUrl(posterUrl);
            shortDrama.setYear(year);
            shortDrama.setStoryline(storyline);
            shortDrama.setActor(toJsonArray(actor));
            shortDrama.setDirector(toJsonArray(director));
            shortDrama.setGenre(toJsonArray(genre));
            shortDrama.setRegion(region);
            shortDrama.setLanguage(language);
            shortDrama.setDuration(duration);
            shortDrama.setReleaseDate(releaseDate);
            shortDrama.setAlias(alias);
            shortDrama.setScoreImdb(imdbScore);
            shortDrama.setTotalEpisode(totalEpisode);
            shortDrama.setStatus(1);

            if (isNew) {
                shortDramaService.save(shortDrama);
            } else {
                shortDramaService.updateById(shortDrama);
            }
            extractMovieResources(doc, "short_drama", shortDrama.getId());
            extractEpisodes(doc, "short_drama", shortDrama.getId());
            return new int[]{isNew ? 1 : 0, isNew ? 0 : 1, 0};
        } catch (Exception e) {
            log.error("Short drama detail parse error: {}", detailUrl, e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    // ========== Episode -> ResourceOnline ==========

    /**
     * 解析播放链接，写入 resource_online（含剧集信息）
     * 替代原 episode 表，播放链接 + 剧集元信息合一
     */
    private void extractEpisodes(Document doc, String contentType, Long contentId) {
        // 增量更新：删除旧的在线播放记录
        onlineMapper.delete(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, contentType)
                .eq(ResourceOnline::getContentId, contentId));

        // 解析在线播放区域的剧集链接
        // 格式: <li><a href="/py/475547-7-1.html" target="blank">第01集</a></li>
        Elements episodeLinks = doc.select("a[href^=/py/]");
        int count = 0;
        for (Element el : episodeLinks) {
            String href = el.attr("href");
            String text = el.text().trim();

            // 从文本提取集数: "第01集" -> 1
            Integer episodeNum = extractEpisodeNumber(text);
            if (episodeNum == null) {
                episodeNum = extractEpisodeNumberFromUrl(href);
            }
            if (episodeNum == null) continue;

            ResourceOnline online = new ResourceOnline();
            online.setContentType(contentType);
            online.setContentId(contentId);
            online.setSeason(1);
            online.setEpisodeNumber(episodeNum);
            online.setEpisodeTitle(text);
            online.setSourceName(text);
            online.setSourceUrl(href.startsWith("http") ? href : BASE_URL + href);
            online.setSort(count++);
            onlineMapper.insert(online);
        }

        if (count > 0) {
            log.info("Extracted {} play links for {} {}", count, contentType, contentId);
        }
    }

    /** 从 "第01集" 提取集数 */
    private Integer extractEpisodeNumber(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = Pattern.compile("第(\\d+)集").matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /** 从 URL /py/475547-7-1.html 提取最后的数字作为集数 */
    private Integer extractEpisodeNumberFromUrl(String href) {
        if (href == null || href.isEmpty()) return null;
        Matcher m = Pattern.compile("/py/\\d+-\\d+-(\\d+)\\.html").matcher(href);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    // ========== Resource Extraction ==========

    private void extractMovieResources(Document doc, String contentType, Long contentId) {
        // 增量更新：删除旧资源记录，重新插入（确保磁力/网盘链接时效性）
        // 注意：online 资源由 extractEpisodes 统一处理，此处只清理磁力和网盘
        magnetMapper.delete(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, contentType)
                .eq(ResourceMagnet::getContentId, contentId));
        cloudMapper.delete(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, contentType)
                .eq(ResourceCloud::getContentId, contentId));

        int magnetSort = 0;
        int onlineSort = 0;
        int cloudSort = 0;

        // Magnet links — 只选 .down-list3 内的链接（有 title 属性和详细文本），跳过 span 内的 "磁力下载" 通用链接
        Elements magnetLinks = doc.select("p.down-list3 > a[href^=magnet:]");
        for (Element el : magnetLinks) {
            String url = el.attr("href");
            if (!url.startsWith("magnet:")) continue;
            // 优先用 title 属性（含完整资源名+大小），fallback 到文本
            String titleAttr = el.attr("title").trim();
            String text = el.text().trim();
            String displayTitle = !titleAttr.isEmpty() ? titleAttr : text;
            if (displayTitle.isEmpty() || displayTitle.equals("磁力下载")) continue;

            ResourceMagnet magnet = new ResourceMagnet();
            magnet.setContentType(contentType);
            magnet.setContentId(contentId);
            magnet.setTitle(displayTitle);
            magnet.setMagnetUrl(url);
            magnet.setResolution(extractResolution(displayTitle));
            magnet.setHasSubtitle(displayTitle.contains("sub") || displayTitle.contains("字幕") || displayTitle.contains("zh") ? Boolean.TRUE : Boolean.FALSE);
            magnet.setIsSpecialSub(displayTitle.contains("special") || displayTitle.contains("特效") ? Boolean.TRUE : Boolean.FALSE);
            magnet.setSort(magnetSort++);
            magnetMapper.insert(magnet);
        }

        // Online playback sources
        // 注意：对于 drama/variety/anime/short，online 资源由 extractEpisodes() 统一管理，此处只处理 movie 类型
        if ("movie".equals(contentType)) {
            Elements playLinks = doc.select("a[href*=player], a[href^=/play/], .stui-vodlist__item a[href]");
            for (Element el : playLinks) {
                String href = el.attr("href");
                if (href.isEmpty() || href.startsWith("javascript")) continue;
                if (!href.contains("player") && !href.startsWith("http")) continue;

                String name = el.text().trim();
                if (name.isEmpty()) name = "Online Source";

                ResourceOnline online = new ResourceOnline();
                online.setContentType(contentType);
                online.setContentId(contentId);
                online.setSourceName(name);
                online.setSourceUrl(href.startsWith("http") ? href : BASE_URL + href);
                online.setSort(onlineSort++);
                onlineMapper.insert(online);
            }
        }

        // Cloud disk links (百度/夸克/迅雷/UC/阿里/123/蓝奏)
        // 只选 .down-list3 内的链接（有 title 属性和详细文本），跳过 span 内的 "网盘下载" 通用链接
        Elements cloudLinks = doc.select("p.down-list3 > a[href*=pan.baidu], p.down-list3 > a[href*=quark], p.down-list3 > a[href*=lanzou], p.down-list3 > a[href*=xunlei], p.down-list3 > a[href*=uc.cn], p.down-list3 > a[href*=alipan], p.down-list3 > a[href*=aliyundrive], p.down-list3 > a[href*=123pan], p.down-list3 > a[href*=123.com]");
        for (Element el : cloudLinks) {
            String href = el.attr("href");
            if (href.isEmpty() || href.startsWith("javascript")) continue;
            // 优先用 title 属性，fallback 到文本
            String titleAttr = el.attr("title").trim();
            String text = el.text().trim();
            String displayTitle = !titleAttr.isEmpty() ? titleAttr : text;
            if (displayTitle.isEmpty() || displayTitle.equals("网盘下载")) continue;

            ResourceCloud cloud = new ResourceCloud();
            cloud.setContentType(contentType);
            cloud.setContentId(contentId);
            cloud.setDiskType(detectDiskType(href));
            cloud.setTitle(displayTitle);
            cloud.setUrl(href);
            cloud.setSort(cloudSort++);
            cloudMapper.insert(cloud);
        }
    }

    /** 检测代理是否可用 */
    private boolean isProxyAvailable() {
        if (proxyAvailable != null) return proxyAvailable;
        try {
            var socket = new java.net.Socket();
            socket.connect(new InetSocketAddress(PROXY_HOST, PROXY_PORT), 2000);
            socket.close();
            proxyAvailable = true;
            log.info("[PROXY] 代理可用 {}:{}", PROXY_HOST, PROXY_PORT);
        } catch (Exception e) {
            proxyAvailable = false;
            log.warn("[PROXY] 代理不可用 {}:{}, 将直连", PROXY_HOST, PROXY_PORT);
        }
        return proxyAvailable;
    }

    // ========== Breakpoint Resumption ==========

    /** 保存爬取进度（断点续爬） */
    private void saveCrawlProgress(Long scheduleId, int page, Long lastId) {
        if (scheduleId == null) return;
        try {
            CrawlerSchedule s = scheduleService.getSchedule(scheduleId);
            if (s != null) {
                s.setLastCrawledPage(page);
                s.setLastCrawledId(lastId);
                scheduleService.saveSchedule(s);
                log.debug("[CrawlerCore] Saved progress: scheduleId={}, page={}, lastId={}", scheduleId, page, lastId);
            }
        } catch (Exception e) {
            log.warn("[CrawlerCore] Failed to save progress: {}", e.getMessage());
        }
    }

    /** 爬取完成，重置断点 */
    private void resetCrawlProgress(Long scheduleId) {
        if (scheduleId == null) return;
        try {
            CrawlerSchedule s = scheduleService.getSchedule(scheduleId);
            if (s != null) {
                s.setLastCrawledPage(0);
                s.setLastCrawledId(0L);
                scheduleService.saveSchedule(s);
            }
        } catch (Exception e) {
            log.warn("[CrawlerCore] Failed to reset progress: {}", e.getMessage());
        }
    }

    // ========== HTTP Helper ==========

    private Document fetchWithRetry(String url) {
        // 速率限制：请求间延迟
        if (rateLimitMs > 0) {
            try { Thread.sleep(rateLimitMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        for (int i = 0; i < RETRY_TIMES; i++) {
            try {
                log.info("[HTTP-FETCH] GET {}", url);
                var conn = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .referrer(BASE_URL)
                        .timeout(TIMEOUT_MS);
                // 仅在代理可用时使用代理
                if (isProxyAvailable()) {
                    conn.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
                }
                Document doc = conn.ignoreHttpErrors(true)
                        .followRedirects(true)
                        .maxBodySize(10 * 1024 * 1024)
                        .get();
                if (doc != null && !doc.body().text().isEmpty()) {
                    log.info("[HTTP-FETCH] OK {} ({} bytes, title=[{}])", url, doc.body().text().length(), doc.title());
                    return doc;
                } else {
                    log.warn("[HTTP-FETCH] EMPTY body for {} ({}/{})", url, i + 1, RETRY_TIMES);
                }
            } catch (Exception e) {
                log.warn("[HTTP-FETCH] FAIL {} ({}/{}): {} — retry in 2s", url, i + 1, RETRY_TIMES, e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.error("[HTTP-FETCH] GAVE UP after {} retries: {}", RETRY_TIMES, url);
        return null;
    }

    // ========== Genre Filter Helpers ==========

    /** 解析 genreFilter JSON 数组字符串为 Set */
    private Set<String> parseGenreFilter(String genreFilterJson) {
        if (genreFilterJson == null || genreFilterJson.trim().isEmpty()) return null;
        try {
            List<String> list = objectMapper.readValue(genreFilterJson, new TypeReference<List<String>>() {});
            return list.isEmpty() ? null : new HashSet<>(list);
        } catch (Exception e) {
            log.warn("Failed to parse genreFilter: {}", genreFilterJson);
            return null;
        }
    }

    /** 检查内容的 genre 是否匹配过滤器（任一类型命中即通过） */
    private boolean matchesGenreFilter(String genreJson, Set<String> genreFilter) {
        if (genreFilter == null || genreFilter.isEmpty()) return true;
        if (genreJson == null || genreJson.isEmpty()) return false;
        try {
            List<String> genres = objectMapper.readValue(genreJson, new TypeReference<List<String>>() {});
            for (String g : genres) {
                if (genreFilter.contains(g)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ========== Field Parsing Helpers ==========

    private String parseTextField(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return (el != null) ? el.text().trim() : "";
    }

    private String extractStoryline(Document doc) {
        Element el = doc.selectFirst(".movie-introduce, .introduce, .desc, .summary");
        String text = null;
        if (el != null) {
            text = el.text().trim();
        }
        if (text == null || text.isEmpty()) {
            Element metaDesc = doc.selectFirst("meta[name=description]");
            text = (metaDesc != null) ? metaDesc.attr("content").trim() : "";
        }
        // 使用 StorylineCleaner 统一清理 UI 残留文本
        return StorylineCleaner.clean(text);
    }

    private Integer extractYear(Document doc) {
        Element el = doc.selectFirst(".year, .data, span.year");
        if (el == null) return null;
        Matcher m = YEAR_PATTERN.matcher(el.text());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private BigDecimal extractScore(Document doc) {
        // pkmp4.xyz 评分结构:
        // <a href="https://movie.douban.com/..."><span style="color: green;">豆瓣 8.6</span></a>
        // <a href="https://www.imdb.com/..."><span style="color: #dba400;">IMDB 8.3</span></a>
        // <a href="https://www.rottentomatoes.com/..."><span style="color: #ff5b5b;">烂番茄 94%</span></a>

        // 优先从豆瓣链接提取
        Elements scoreLinks = doc.select("a[href*=douban], a[href*=imdb], a[href*=rottentomatoes]");
        for (Element link : scoreLinks) {
            String href = link.attr("href");
            String text = link.text();
            // 匹配 "豆瓣 8.6" 或 "IMDB 8.3" 或 "烂番茄 94%"
            Pattern scorePattern = Pattern.compile("(?:豆瓣|IMDB|烂番茄)[\s:：]*(\\d+\\.\\d+)");
            Matcher m = scorePattern.matcher(text);
            if (m.find()) {
                try {
                    BigDecimal score = new BigDecimal(m.group(1));
                    if (score.compareTo(BigDecimal.ZERO) > 0 && score.compareTo(new BigDecimal("10")) <= 0) {
                        return score;
                    }
                } catch (Exception ignored) {}
            }
            // 也处理百分比格式（烂番茄 94% -> 9.4）
            Pattern pctPattern = Pattern.compile("烂番茄[\s:：]*(\\d+)%");
            Matcher pm = pctPattern.matcher(text);
            if (pm.find()) {
                try {
                    int pct = Integer.parseInt(pm.group(1));
                    return new BigDecimal(pct).divide(new BigDecimal("10"), 1, java.math.RoundingMode.HALF_UP);
                } catch (Exception ignored) {}
            }
        }

        // Fallback: 从 meta description 提取
        return extractScoreFromDescription(doc);
    }

    private Integer extractEpisodeCount(Document doc) {
        // 优先从 .otherbox 提取 "XX集全" 格式
        Element otherbox = doc.selectFirst(".otherbox");
        if (otherbox != null) {
            Matcher m = Pattern.compile("(\\d+)集[全更新完结]").matcher(otherbox.text());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        // Fallback: 从 .total, .episode 提取
        Element el = doc.selectFirst(".total, .episode, [class*=episode]");
        if (el != null) {
            Matcher m = Pattern.compile("(\\d+)").matcher(el.text());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private String extractResolution(String text) {
        if (text.contains("4K") || text.contains("2160")) return "4K";
        if (text.contains("1080") || text.contains("全高清")) return "1080P";
        if (text.contains("720")) return "720P";
        if (text.contains("480")) return "480P";
        return "Unknown";
    }

    private String detectDiskType(String url) {
        if (url.contains("pan.baidu") || url.contains("baidu.com")) return "baidu";
        if (url.contains("quark")) return "quark";
        if (url.contains("lanzou") || url.contains("lanzouk")) return "lanzou";
        if (url.contains("xunlei") || url.contains("thunder")) return "xunlei";
        if (url.contains("uc.cn") || url.contains("drive.uc")) return "uc";
        if (url.contains("alipan") || url.contains("aliyundrive") || url.contains("ali.com")) return "ali";
        if (url.contains("123pan") || url.contains("123.com")) return "123";
        return "other";
    }

    private Long extractContentId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    private String toJsonArray(String text) {
        if (text == null || text.isEmpty()) return "[]";
        if (text.startsWith("[")) return text;
        String[] parts = text.split("[/,，、]");
        if (parts.length > 1) {
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            try { return objectMapper.writeValueAsString(list); } catch (Exception ignored) { return text; }
        }
        try { return objectMapper.writeValueAsString(List.of(text.trim())); } catch (Exception ignored) { return text; }
    }

    // ========== pkmp4.xyz Real Page Helpers ==========

    /** 从页面提取 "主演：xxx" 或 "导演：xxx" 文本 (修复版 - 跨 div 提取) */
    private String extractTextByLabel(Document doc, String label) {
        log.trace("extractTextByLabel label={}", label);
        // 去掉 HTML 注释，避免提取注释中的文字
        String cleanHtml = doc.html().replaceAll("(?s)<!--.*?-->", "");
        Document cleanDoc = Jsoup.parse(cleanHtml);
        String labelSpan = label + "：";
        // 找到所有 span
        Elements spans = cleanDoc.select("span");
        for (Element span : spans) {
            String spanText = span.text().trim();
            if (!spanText.equals(labelSpan) && !spanText.equals(label + ":")) continue;
            // 找父 div，名字在父 div 内的 <a> 标签中（span 后）
            Element parentDiv = span.parent();
            if (parentDiv == null) continue;
            // 收集父 div 中 span 之后的所有 <a> 标签的名字
            List<String> names = new ArrayList<>();
            boolean foundSpan = false;
            for (Element child : parentDiv.children()) {
                if (child == span) { foundSpan = true; continue; }
                if (foundSpan) {
                    Elements anchors = child.select("a");
                    for (Element a : anchors) {
                        String name = a.text().trim();
                        if (!name.isEmpty() && name.length() < 50) names.add(name);
                    }
                }
            }
            if (!names.isEmpty()) {
                try { return objectMapper.writeValueAsString(names); } catch (Exception ignored) {}
            }
            // 也检查紧邻的后续所有兄弟 div 中的 <a>（主演可能有多个 div）
            Element sibling = parentDiv.nextElementSibling();
            while (sibling != null) {
                Elements nextAnchors = sibling.select("a");
                for (Element a : nextAnchors) {
                    String name = a.text().trim();
                    if (!name.isEmpty() && name.length() < 50) names.add(name);
                }
                sibling = sibling.nextElementSibling();
            }
            if (!names.isEmpty()) {
                try { return objectMapper.writeValueAsString(names); } catch (Exception ignored) {}
            }
        }
        return "[]";
    }

    /** 从页面 tag 链接提取类型列表 */
    private String extractGenresFromTags(Document doc) {
        // pkmp4 URL 格式: /ms/{type}---{genre}--------.html
        // type 可能是 1(电影), 2(剧集), 3(综艺), 4(动漫), 30(短剧)
        // 选择器匹配所有 /ms/ 开头且包含 --- 的链接
        Elements tagLinks = doc.select("a[href*='/ms/'][href*='---']");
        List<String> genres = new ArrayList<>();
        for (Element link : tagLinks) {
            String href = link.attr("href");
            // 排除地区链接（格式 /ms/{type}-{region}----------.html，只有1个短横线分隔）
            // 排除语言链接（格式 /ms/{type}----{lang}-------.html）
            // 类型链接格式: /ms/{type}---{genre}--------.html (3个短横线后接genre)
            if (!href.matches(".*/ms/\\d+---[^-].*")) continue;
            String t = link.text().trim();
            if (!t.isEmpty() && t.length() < 20 && !t.matches(".*\\d+.*")) {
                genres.add(t);
            }
        }
        if (!genres.isEmpty()) {
            try { return objectMapper.writeValueAsString(genres); } catch (Exception ignored) {}
        }
        return "[]";
    }

    /** 从页面 tag 判断地区 */
    /** 将 List 序列化为 JSON 数组字符串，失败返回 "[]" */
    private String toJsonArray(List<String> list) {
        try { return objectMapper.writeValueAsString(list); } catch (Exception e) { return "[]"; }
    }

    private List<String> extractRegionFromTags(Document doc) {
        // pkmp4.xyz: 地区字段结构与主演相同，使用 extractTextByLabel 逻辑
        String regionJson = extractTextByLabel(doc, "地区");
        try {
            return objectMapper.readValue(regionJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // Fallback: 尝试从 /ms/{type}-{region}----------.html 链接提取
            // type 可能是 1(电影), 2(剧集), 30(短剧) 等
            Elements tagLinks = doc.select("a[href*='/ms/'][href$='----------.html']");
            List<String> regions = new ArrayList<>();
            Set<String> knownRegions = Set.of("美国", "中国", "英国", "法国", "德国", "日本", "韩国", "香港", "台湾", "大陆", "印度", "加拿大", "澳大利亚", "西班牙", "意大利", "泰国", "中国大陆");
            for (Element link : tagLinks) {
                String t = link.text().trim();
                if (knownRegions.contains(t)) regions.add(t);
            }
            return regions;
        }
    }

    /** 从 meta description 中提取豆瓣评分 (e.g. "豆瓣 8.6分") */
    private BigDecimal extractScoreFromDescription(Document doc) {
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc == null) return null;
        String desc = metaDesc.attr("content");
        Pattern p = Pattern.compile("豆瓣[\s:：]*([\\d.]+)分");
        Matcher m = p.matcher(desc);
        if (m.find()) {
            try { return new BigDecimal(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    // ========== 新增字段提取方法 ==========

    /** 提取 IMDb 评分 */
    private BigDecimal extractImdbScore(Document doc) {
        Elements scoreLinks = doc.select("a[href*=imdb]");
        for (Element link : scoreLinks) {
            String text = link.text();
            Pattern scorePattern = Pattern.compile("IMDB[\s:：]*(\\d+\\.\\d+)");
            Matcher m = scorePattern.matcher(text);
            if (m.find()) {
                try {
                    BigDecimal score = new BigDecimal(m.group(1));
                    if (score.compareTo(BigDecimal.ZERO) > 0 && score.compareTo(new BigDecimal("10")) <= 0) {
                        return score;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /** 提取语言列表 */
    private String extractLanguage(Document doc) {
        // pkmp4 格式: <a href="/ms/1----英语-------.html">英语</a>
        Elements langLinks = doc.select("a[href*='/ms/'][href*='----']");
        List<String> languages = new ArrayList<>();
        for (Element link : langLinks) {
            String href = link.attr("href");
            // 语言链接格式: /ms/{type}----{lang}-------.html
            if (href.matches(".*/ms/\\d+----[^-].*")) {
                String t = link.text().trim();
                if (!t.isEmpty() && t.length() < 20 && !t.matches(".*\\d+.*")) {
                    languages.add(t);
                }
            }
        }
        if (languages.isEmpty()) {
            // Fallback: extractTextByLabel
            return extractTextByLabel(doc, "语言");
        }
        try { return objectMapper.writeValueAsString(languages); } catch (Exception e) { return "[]"; }
    }

    /** 提取片长（分钟） */
    private Integer extractDuration(Document doc) {
        // 格式: <span>片长：</span>156分钟
        String text = doc.body().text();
        Pattern p = Pattern.compile("片长[：:](\\d+)分钟");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 提取上映日期 */
    private String extractReleaseDate(Document doc) {
        // 格式: <span>上映：</span>2026-03-20(美国/中国大陆)
        Elements spans = doc.select("span");
        for (Element span : spans) {
            if (span.text().contains("上映")) {
                Element parent = span.parent();
                if (parent != null) {
                    String fullText = parent.text();
                    // 去掉 "上映：" 前缀
                    String dateText = fullText.replaceAll(".*上映[：:]\s*", "").trim();
                    if (!dateText.isEmpty() && dateText.length() > 4) {
                        return dateText;
                    }
                }
            }
        }
        return null;
    }

    /** 提取又名 */
    private String extractAlias(Document doc) {
        // 格式: <span>又名：</span>极限返航(台) / 末日圣母号(港)
        Elements spans = doc.select("span");
        for (Element span : spans) {
            if (span.text().contains("又名")) {
                Element parent = span.parent();
                if (parent != null) {
                    String fullText = parent.text();
                    String aliasText = fullText.replaceAll(".*又名[：:]\s*", "").trim();
                    if (!aliasText.isEmpty()) {
                        String[] parts = aliasText.split("[/／]");
                        List<String> aliases = new ArrayList<>();
                        for (String p : parts) {
                            String trimmed = p.trim();
                            if (!trimmed.isEmpty()) aliases.add(trimmed);
                        }
                        try { return objectMapper.writeValueAsString(aliases); } catch (Exception e) { return "[]"; }
                    }
                }
            }
        }
        return "[]";
    }

    /** 提取编剧 */
    private String extractWriter(Document doc) {
        return extractTextByLabel(doc, "编剧");
    }
}
