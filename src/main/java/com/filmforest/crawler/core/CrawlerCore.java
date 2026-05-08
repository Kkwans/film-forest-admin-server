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
import com.filmforest.content.mapper.EpisodeMapper;
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
    @Autowired private EpisodeMapper episodeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Pattern YEAR_PATTERN = Pattern.compile("((?:19|20)\\d{2})");
    private final Pattern ID_PATTERN = Pattern.compile("/mv/(\\d+)");
    private final ConcurrentHashMap<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();

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

            if ("movie".equals(type)) {
                int[] r = crawlMovieList(1, batchSize, stopFlag);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("drama".equals(type)) {
                int[] r = crawlDramaList(1, batchSize, stopFlag);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("variety".equals(type)) {
                int[] r = crawlVarietyList(1, batchSize, stopFlag);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("anime".equals(type)) {
                int[] r = crawlAnimeList(1, batchSize, stopFlag);
                added = r[0]; updated = r[1]; total = r[2];
            } else if ("short_drama".equals(type) || "short".equals(type)) {
                int[] r = crawlShortDramaList(1, batchSize, stopFlag);
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

    public int[] crawlMovieList(int startPage, int maxItems, AtomicBoolean stopFlag) {
        int added = 0, updated = 0, total = 0;
        int page = startPage;

        while (total < maxItems) {
            if (stopFlag != null && stopFlag.get()) break;
            // type=1 电影列表: /vt/1.html, /vt/1-2.html, ...
            String listUrl = getListUrl("1", page);
            Document listDoc = fetchWithRetry(listUrl);
            if (listDoc == null) break;

            // 抓详情页链接 /mv/数字.html（去重，避免同一页面中重复链接导致重复入库）
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
                int[] r = crawlMovieDetail(detailUrl, stopFlag);
            log.debug("crawlMovieDetail completed for: {} -> added:{} updated:{}", detailUrl, r[0], r[1]);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlMovieDetail(String detailUrl, AtomicBoolean stopFlag) {
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
            Element storylineEl = doc.selectFirst(".movie-introduce p, .zkjj_a, .con");
            if (storylineEl != null) {
                storyline = storylineEl.text().trim();
                storyline = storyline.replaceAll("展开全部", "").replaceAll("收起部分", "").trim();
            }

            // 主演/导演: 从 .text-overflow 区域提取
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");

            // 类型/地区: 从 tag 链接提取 (<a href="/ms/1---剧情--------.html">剧情</a>)
            String genre = extractGenresFromTags(doc);
            List<String> regionList = extractRegionFromTags(doc); String region = "[]"; try { region = objectMapper.writeValueAsString(regionList); } catch (Exception ignored) {}

            // 评分: 尝试从 meta description (e.g. "豆瓣 8.6分") 或页面提取
            BigDecimal score = extractScoreFromDescription(doc);

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
            movie.setGenre(toJsonArray(genre));
            movie.setRegion(toJsonArray(region));
            movie.setScoreDouban(score);
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
    public int[] crawlDramaList(int startPage, int maxItems, AtomicBoolean stopFlag) {
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
                int[] r = crawlDramaDetail(detailUrl, stopFlag);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlDramaDetail(String detailUrl, AtomicBoolean stopFlag) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();

            String posterUrl = null;
            Element img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");

            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            List<String> regionList = extractRegionFromTags(doc); String region = "[]"; try { region = objectMapper.writeValueAsString(regionList); } catch (Exception ignored) {}
            BigDecimal score = extractScore(doc);
            Integer totalEpisode = extractEpisodeCount(doc);

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
            drama.setGenre(toJsonArray(genre));
            drama.setRegion(toJsonArray(region));
            drama.setScoreDouban(score);
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
    public int[] crawlVarietyList(int startPage, int maxItems, AtomicBoolean stopFlag) {
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
                int[] r = crawlVarietyDetail(detailUrl, stopFlag);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            page++;
        }
        return new int[]{added, updated, total};
    }

    // type=4 动漫列表
    public int[] crawlAnimeList(int startPage, int maxItems, AtomicBoolean stopFlag) {
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
                int[] r = crawlAnimeDetail(detailUrl, stopFlag);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            page++;
        }
        return new int[]{added, updated, total};
    }

    // type=30 短剧列表
    public int[] crawlShortDramaList(int startPage, int maxItems, AtomicBoolean stopFlag) {
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
                int[] r = crawlShortDramaDetail(detailUrl, stopFlag);
                if (r[0] == 1) added++;
                if (r[1] == 1) updated++;
                total++;
            }
            page++;
        }
        return new int[]{added, updated, total};
    }

    public int[] crawlVarietyDetail(String detailUrl, AtomicBoolean stopFlag) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String genre = extractGenresFromTags(doc);
            Integer totalEpisode = extractEpisodeCount(doc);

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
            variety.setGenre(toJsonArray(genre));
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

    public int[] crawlAnimeDetail(String detailUrl, AtomicBoolean stopFlag) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String director = extractTextByLabel(doc, "导演");
            String genre = extractGenresFromTags(doc);
            List<String> regionList = extractRegionFromTags(doc); String region = "[]"; try { region = objectMapper.writeValueAsString(regionList); } catch (Exception ignored) {}
            Integer totalEpisode = extractEpisodeCount(doc);

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
            anime.setGenre(toJsonArray(genre));
            anime.setRegion(toJsonArray(region));
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

    public int[] crawlShortDramaDetail(String detailUrl, AtomicBoolean stopFlag) {
        Document doc = fetchWithRetry(detailUrl);
        if (doc == null) return new int[]{0, 0, 0};

        try {
            String title = doc.selectFirst("h1").text().trim();
            String posterUrl = null;
            Element img = doc.selectFirst("div.li-img img, .movie-cover img");
            if (img != null) posterUrl = img.attr("abs:src");
            Integer year = extractYear(doc);
            String storyline = extractStoryline(doc);
            String actor = extractTextByLabel(doc, "主演");
            String genre = extractGenresFromTags(doc);
            List<String> regionList = extractRegionFromTags(doc); String region = "[]"; try { region = objectMapper.writeValueAsString(regionList); } catch (Exception ignored) {}
            Integer totalEpisode = extractEpisodeCount(doc);

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
            shortDrama.setGenre(toJsonArray(genre));
            shortDrama.setRegion(toJsonArray(region));
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

    // ========== Episode Extraction ==========

    private void extractEpisodes(Document doc, String contentType, Long contentId) {
        // 增量更新：删除旧剧集记录
        episodeMapper.delete(new LambdaQueryWrapper<Episode>()
                .eq(Episode::getContentType, contentType)
                .eq(Episode::getContentId, contentId));

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
                // fallback: 从 URL 提取集数 /py/475547-7-1.html -> 1
                episodeNum = extractEpisodeNumberFromUrl(href);
            }
            if (episodeNum == null) continue;

            Episode episode = new Episode();
            episode.setContentType(contentType);
            episode.setContentId(contentId);
            episode.setSeason(1);
            episode.setEpisodeNumber(episodeNum);
            episode.setTitle(text);
            episodeMapper.insert(episode);
            count++;
        }

        if (count > 0) {
            log.info("Extracted {} episodes for {} {}", count, contentType, contentId);
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
        magnetMapper.delete(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, contentType)
                .eq(ResourceMagnet::getContentId, contentId));
        cloudMapper.delete(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, contentType)
                .eq(ResourceCloud::getContentId, contentId));
        onlineMapper.delete(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, contentType)
                .eq(ResourceOnline::getContentId, contentId));

        int magnetSort = 0;
        int onlineSort = 0;

        // Magnet links
        Elements magnetLinks = doc.select("a[href^=magnet:]");
        for (Element el : magnetLinks) {
            String url = el.attr("href");
            if (!url.startsWith("magnet:")) continue;
            String text = el.text();

            ResourceMagnet magnet = new ResourceMagnet();
            magnet.setContentType(contentType);
            magnet.setContentId(contentId);
            magnet.setTitle(text);
            magnet.setMagnetUrl(url);
            magnet.setResolution(extractResolution(text));
            magnet.setHasSubtitle(text.contains("sub") || text.contains("zh") ? Boolean.TRUE : Boolean.FALSE);
            magnet.setIsSpecialSub(text.contains("special") || text.contains("特效") ? Boolean.TRUE : Boolean.FALSE);
            magnet.setSort(magnetSort++);
            magnetMapper.insert(magnet);
        }

        // Online playback sources
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

        // Cloud disk links
        Elements cloudLinks = doc.select("a[href*=pan.baidu], a[href*=quark], a[href*=lanzou], a[href*=xunlei]");
        for (Element el : cloudLinks) {
            String href = el.attr("href");
            if (href.isEmpty() || href.startsWith("javascript")) continue;
            String text = el.text();

            ResourceCloud cloud = new ResourceCloud();
            cloud.setContentType(contentType);
            cloud.setContentId(contentId);
            cloud.setDiskType(detectDiskType(href));
            cloud.setTitle(text);
            cloud.setUrl(href);
            cloud.setSort(0);
            cloudMapper.insert(cloud);
        }
    }

    // ========== HTTP Helper ==========

    private Document fetchWithRetry(String url) {
        for (int i = 0; i < RETRY_TIMES; i++) {
            try {
                log.info("[HTTP-FETCH] GET {}", url);
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .referrer(BASE_URL)
                        .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)))
                        .timeout(TIMEOUT_MS)
                        .ignoreHttpErrors(true)
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
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
        log.error("[HTTP-FETCH] GAVE UP after {} retries: {}", RETRY_TIMES, url);
        return null;
    }

    // ========== Field Parsing Helpers ==========

    private String parseTextField(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return (el != null) ? el.text().trim() : "";
    }

    private String extractStoryline(Document doc) {
        Element el = doc.selectFirst(".movie-introduce, .introduce, .desc, .summary");
        if (el != null) {
            String text = el.text().trim();
            if (!text.isEmpty()) return text;
        }
        Element metaDesc = doc.selectFirst("meta[name=description]");
        return (metaDesc != null) ? metaDesc.attr("content").trim() : "";
    }

    private Integer extractYear(Document doc) {
        Element el = doc.selectFirst(".year, .data, span.year");
        if (el == null) return null;
        Matcher m = YEAR_PATTERN.matcher(el.text());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private BigDecimal extractScore(Document doc) {
        Element el = doc.selectFirst(".score, [class*=score], .rating");
        if (el == null) return null;
        String text = el.text().replaceAll("[^0-9.]", "");
        if (text.isEmpty()) return null;
        try { return new BigDecimal(text); } catch (Exception ignored) { return null; }
    }

    private Integer extractEpisodeCount(Document doc) {
        Element el = doc.selectFirst(".total, .episode, [class*=episode]");
        if (el == null) return null;
        Matcher m = Pattern.compile("(\\d+)").matcher(el.text());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
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
        Elements tagLinks = doc.select("a[href^='/ms/1--']");
        List<String> genres = new ArrayList<>();
        for (Element link : tagLinks) {
            String t = link.text().trim();
            if (!t.isEmpty() && t.length() < 20 && !t.matches(".*[0-9]+.*")) {
                genres.add(t);
            }
        }
        if (!genres.isEmpty()) {
            try { return objectMapper.writeValueAsString(genres); } catch (Exception ignored) {}
        }
        return "[]";
    }

    /** 从页面 tag 判断地区 */
    private List<String> extractRegionFromTags(Document doc) {
        // pkmp4.xyz: 地区字段结构与主演相同，使用 extractTextByLabel 逻辑
        String regionJson = extractTextByLabel(doc, "地区");
        try {
            return objectMapper.readValue(regionJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // Fallback: 尝试从 /ms/1-{region}----------.html 链接提取
            Elements tagLinks = doc.select("a[href^='/ms/1-']");
            List<String> regions = new ArrayList<>();
            Set<String> knownRegions = Set.of("美国", "中国", "英国", "法国", "德国", "日本", "韩国", "香港", "台湾", "大陆", "印度", "加拿大", "澳大利亚", "西班牙", "意大利", "泰国");
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
}
