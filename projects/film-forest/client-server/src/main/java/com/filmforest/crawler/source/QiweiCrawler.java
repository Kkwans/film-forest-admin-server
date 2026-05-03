package com.filmforest.crawler.source;

import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.MovieService;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.http.CrawlerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 七味网影视数据爬虫 (pkmp4.xyz)
 * 目标: https://www.pkmp4.xyz - 七味影视资源站
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QiweiCrawler {

    private final CrawlerEngine crawlerEngine;
    private final MovieService movieService;

    /** 电影分类页URL模板 */
    private static final String LIST_URL_TEMPLATE = "https://www.pkmp4.xyz/vt/1.html";
    /** 剧集分类页URL */
    private static final String DRAMA_LIST_URL = "https://www.pkmp4.xyz/vt/2.html";
    /** 综艺分类页URL */
    private static final String VARIETY_LIST_URL = "https://www.pkmp4.xyz/vt/3.html";
    /** 动漫分类页URL */
    private static final String ANIME_LIST_URL = "https://www.pkmp4.xyz/vt/4.html";
    /** 短剧分类页URL */
    private static final String SHORT_LIST_URL = "https://www.pkmp4.xyz/vt/30.html";

    /**
     * 从七味网抓取指定类型内容列表
     * @param schedule 爬虫调度配置 (contentType: movie/drama/variety/anime/short)
     */
    public void crawlMovies(CrawlerSchedule schedule) {
        String contentType = schedule.getContentType() != null ? schedule.getContentType() : "movie";
        String url = getListUrl(contentType);
        log.info("[七味网] 开始抓取 {} 列表: {}", contentType, url);

        List<Movie> movies = new ArrayList<>();
        int maxPages = schedule.getBatchSize() != null ? Math.min(schedule.getBatchSize(), 10) : 5;

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = page == 1 ? url : url + "?page=" + page;
            String html = crawlerEngine.get(pageUrl);
            if (html == null || html.isEmpty()) {
                log.warn("[七味网] 第{}页抓取失败，停止", page);
                break;
            }

            List<Movie> pageMovies = parseMovieList(html, contentType);
            if (pageMovies.isEmpty()) {
                log.info("[七味网] 第{}页无数据，停止抓取", page);
                break;
            }

            movies.addAll(pageMovies);
            log.info("[七味网] 第{}页抓取到 {} 条", page, pageMovies.size());

            // 防封延时
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }

        // 入库
        int saved = 0;
        for (Movie movie : movies) {
            try {
                var existing = movieService.lambdaQuery()
                        .eq(Movie::getTitle, movie.getTitle())
                        .eq(movie.getYear() != null, Movie::getYear, movie.getYear())
                        .one();
                if (existing == null) {
                    movie.setStatus(1);
                    movieService.save(movie);
                    saved++;
                }
            } catch (Exception e) {
                log.error("[七味网] 入库失败: {}, error: {}", movie.getTitle(), e.getMessage());
            }
        }
        log.info("[七味网] 抓取完成，入库 {} 条（共抓取 {} 条）", saved, movies.size());
    }

    private String getListUrl(String contentType) {
        return switch (contentType) {
            case "drama" -> DRAMA_LIST_URL;
            case "variety" -> VARIETY_LIST_URL;
            case "anime" -> ANIME_LIST_URL;
            case "short" -> SHORT_LIST_URL;
            default -> LIST_URL_TEMPLATE;
        };
    }

    /**
     * 解析七味网列表页HTML (pkmp4.xyz)
     * 实际页面结构: ul.content-list > a[href^="/mv/"] > img + span.bottom
     */
    List<Movie> parseMovieList(String html, String contentType) {
        List<Movie> movies = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("ul.content-list > a[href^=/mv/]");

            for (Element item : items) {
                try {
                    Movie movie = new Movie();

                    // 标题: <a title="xxx" href="/mv/xxxxx.html">
                    String title = item.attr("title").trim();
                    if (title.isEmpty()) title = item.selectFirst("img") != null ? item.selectFirst("img").attr("alt").trim() : "";
                    movie.setTitle(title);

                    // 海报: img src (列表页图片)
                    String poster = item.selectFirst("img").attr("src");
                    if (StringUtils.isBlank(poster)) {
                        poster = item.selectFirst("img").attr("data-src");
                    }
                    movie.setPosterUrl(poster);

                    // 从 href 提取 movie ID 用于拼接详情页 URL
                    String href = item.attr("href"); // e.g. /mv/490613.html
                    String detailUrl = "https://www.pkmp4.xyz" + href;

                    // 抓取详情页获取更多信息
                    Movie detail = crawlDetail(detailUrl, contentType);
                    if (detail != null) {
                        // 合并数据，列表页已有数据优先级
                        if (StringUtils.isNotBlank(detail.getTitle()) && StringUtils.isBlank(movie.getTitle())) {
                            movie.setTitle(detail.getTitle());
                        }
                        if (movie.getPosterUrl() == null && detail.getPosterUrl() != null) {
                            movie.setPosterUrl(detail.getPosterUrl());
                        }
                        if (movie.getYear() == null) movie.setYear(detail.getYear());
                        if (movie.getScoreDouban() == null) movie.setScoreDouban(detail.getScoreDouban());
                        if (movie.getScoreImdb() == null) movie.setScoreImdb(detail.getScoreImdb());
                        if (StringUtils.isBlank(movie.getRegion())) movie.setRegion(detail.getRegion());
                        if (StringUtils.isBlank(movie.getGenre())) movie.setGenre(detail.getGenre());
                        if (StringUtils.isBlank(movie.getDirector())) movie.setDirector(detail.getDirector());
                        if (StringUtils.isBlank(movie.getActor())) movie.setActor(detail.getActor());
                        if (StringUtils.isBlank(movie.getStoryline())) movie.setStoryline(detail.getStoryline());
                    }

                    if (!movie.getTitle().isEmpty()) {
                        movies.add(movie);
                    }
                } catch (Exception e) {
                    log.debug("解析单条数据异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[七味网] 解析列表页异常: {}", e.getMessage());
        }
        return movies;
    }

    /**
     * 抓取七味网详情页数据
     * URL 格式: https://www.pkmp4.xyz/mv/490613.html
     */
    public Movie crawlDetail(String detailUrl, String contentType) {
        String html = crawlerEngine.get(detailUrl);
        if (html == null || html.isEmpty()) return null;

        Movie movie = new Movie();
        try {
            Document doc = Jsoup.parse(html);

            // 标题: <h1>挽救计划<span class="year">(2026)</span></h1>
            Element h1 = doc.selectFirst("h1");
            if (h1 != null) {
                String titleText = h1.text();
                // 去掉年份括号
                titleText = titleText.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
                movie.setTitle(titleText);
                // 年份: h1 span.year
                Element yearEl = h1.selectFirst(".year");
                if (yearEl != null) {
                    String yearText = yearEl.text().replaceAll("[()]", "").trim();
                    try { movie.setYear(Integer.parseInt(yearText)); } catch (Exception ignored) {}
                }
            }

            // 海报: 从页面提取（可能在详情区域）
            Element posterImg = doc.selectFirst(".detail-img img, .vod-img img, .pic img");
            if (posterImg != null) {
                String poster = posterImg.attr("src");
                if (StringUtils.isBlank(poster)) poster = posterImg.attr("data-src");
                movie.setPosterUrl(poster);
            }

            // 类型标签: <a href="/ms/1---剧情--------.html">剧情</a>
            Elements tagLinks = doc.select(".vod-info .tag-list a, .info a[href^='/ms/']");
            if (!tagLinks.isEmpty()) {
                List<String> genres = new ArrayList<>();
                for (Element tag : tagLinks) {
                    String t = tag.text().trim();
                    if (!t.isEmpty() && t.length() < 20) genres.add(t);
                }
                if (!genres.isEmpty()) movie.setGenre(String.join(",", genres));
            }

            // IMDB 评分: <a href="https://www.imdb.com/title/tt12042730/" target="_blank">
            Element imdbLink = doc.selectFirst("a[href*='imdb.com/title']");
            if (imdbLink != null) {
                String imdbUrl = imdbLink.attr("href");
                // 尝试从页面提取 IMDB 评分
                Element scoreEl = doc.selectFirst(".score-imdb, .imdb-score, .vod-score");
                if (scoreEl != null) {
                    try {
                        String scoreText = scoreEl.text().replaceAll("[^\\d.]", "");
                        if (!scoreText.isEmpty()) movie.setScoreImdb(new BigDecimal(scoreText));
                    } catch (Exception ignored) {}
                }
                // 从 IMDB URL 提取 ID
                // imdbUrl like https://www.imdb.com/title/tt12042730/
            }

            // 豆瓣评分: 在页面描述中 "豆瓣 8.6分"
            Element descEl = doc.selectFirst("meta[name='description']");
            if (descEl != null) {
                String desc = descEl.attr("content");
                // 查找 "豆瓣 X分" 或 "豆瓣: X"
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("豆瓣[\\s:：]*([\\d.]+)分");
                var m = p.matcher(desc);
                if (m.find()) {
                    try { movie.setScoreDouban(new BigDecimal(m.group(1))); } catch (Exception ignored) {}
                }
            }

            // 详情区提取导演/演员
            // 寻找 dt/dd 结构的描述信息
            Elements dds = doc.select(".vod-info dd");
            for (Element dd : dds) {
                String text = dd.text();
                if (text.startsWith("导演：")) {
                    String directors = text.substring(3).trim();
                    if (!directors.isEmpty()) movie.setDirector("[\"" + directors.replace(" ", "\",\"") + "\"]");
                }
                // 演员可能在 dd 中
            }

            // 从详情文字区域提取演员
            Elements actorLinks = doc.select(".vod-content a[href*='/star/'], .vod-info a[href*='/star/']");
            if (!actorLinks.isEmpty()) {
                String actors = actorLinks.eachText().stream().reduce((a, b) -> a + "," + b).orElse("");
                if (!actors.isEmpty()) movie.setActor(actors);
            }

            // 剧情简介从 meta description 或详情区
            Element storylineEl = doc.selectFirst(".vod-content, .desc, .story, .con");
            if (storylineEl != null) {
                String story = storylineEl.text().trim();
                if (story.length() > 10) movie.setStoryline(story);
            }

            // 地区 - 从 tag 中找或页面文字
            for (Element tag : tagLinks) {
                String t = tag.text().trim();
                if (t.matches("美国|中国|英国|法国|德国|日本|韩国|香港|台湾|大陆|印度|其他")) {
                    movie.setRegion(t);
                    break;
                }
            }

            // 时长 - 可能需要从详情提取
            // ...

        } catch (Exception e) {
            log.error("[七味网] 解析详情页异常: {}", e.getMessage());
        }
        return movie;
    }

    /**
     * 抓取磁力链接（用于资源表）
     */
    public List<String> extractMagnetLinks(String detailUrl) {
        List<String> magnets = new ArrayList<>();
        String html = crawlerEngine.get(detailUrl);
        if (html == null) return magnets;

        try {
            Document doc = Jsoup.parse(html);
            Elements magnetLinks = doc.select("a[href^='magnet:']");
            for (Element link : magnetLinks) {
                String magnet = link.attr("href");
                if (!magnets.contains(magnet)) magnets.add(magnet);
            }
        } catch (Exception e) {
            log.error("[七味网] 提取磁力链接异常: {}", e.getMessage());
        }
        return magnets;
    }

    /**
     * 抓取百度网盘链接
     */
    public List<String> extractBaiduPanLinks(String detailUrl) {
        List<String> pans = new ArrayList<>();
        String html = crawlerEngine.get(detailUrl);
        if (html == null) return pans;

        try {
            Document doc = Jsoup.parse(html);
            Elements panLinks = doc.select("a[href*='pan.baidu.com']");
            for (Element link : panLinks) {
                String pan = link.attr("href");
                if (!pans.contains(pan)) pans.add(pan);
            }
        } catch (Exception e) {
            log.error("[七味网] 提取百度网盘链接异常: {}", e.getMessage());
        }
        return pans;
    }
}