package com.filmforest.stats.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.stats.service.StatsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 数据统计 API
 * 提供管理端仪表盘所需的各类统计数据
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    /**
     * 数据概览
     * 返回：各类型内容数量、7日增长、爬虫成功率、资源总数、用户数
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.ok(statsService.getOverview());
    }

    /**
     * 内容增长趋势
     * @param days 天数，默认 30
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        return Result.ok(statsService.getTrend(days));
    }

    /**
     * 热门搜索词
     * @param days 时间范围（天），默认 30
     * @param limit 返回数量，默认 15
     */
    @GetMapping("/hot-search")
    public Result<List<Map<String, Object>>> getHotSearch(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "15") int limit) {
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        if (limit < 1) limit = 10;
        if (limit > 50) limit = 50;
        return Result.ok(statsService.getHotSearchKeywords(days, limit));
    }

    /**
     * 数据报表
     * 返回结构化的报表数据，包含各维度统计汇总
     */
    @GetMapping("/report")
    public Result<Map<String, Object>> getReport(
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 7;
        if (days > 365) days = 365;
        return Result.ok(statsService.getReport(days));
    }

    /**
     * 导出概览数据为 CSV
     */
    @GetMapping("/export/overview")
    public void exportOverview(HttpServletResponse response) throws IOException {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=film-forest-overview-" + date + ".csv");
        // Write BOM for Excel compatibility
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        Map<String, Object> overview = statsService.getOverview();
        @SuppressWarnings("unchecked")
        Map<String, Long> typeCounts = (Map<String, Long>) overview.get("typeCounts");
        @SuppressWarnings("unchecked")
        Map<String, Long> weekGrowth = (Map<String, Long>) overview.get("weekGrowth");
        @SuppressWarnings("unchecked")
        Map<String, Object> crawler = (Map<String, Object>) overview.get("crawler");
        @SuppressWarnings("unchecked")
        Map<String, Object> resources = (Map<String, Object>) overview.get("resources");

        Map<String, String> labels = Map.of(
                "movie", "电影", "drama", "剧集", "variety", "综艺",
                "anime", "动漫", "short_drama", "短剧");

        PrintWriter writer = response.getWriter();
        writer.println("类别,数量,7日增长,占比");
        long total = (long) overview.getOrDefault("totalContent", 0L);
        for (String type : new String[]{"movie", "drama", "variety", "anime", "short_drama"}) {
            long count = typeCounts.getOrDefault(type, 0L);
            long growth = weekGrowth.getOrDefault(type, 0L);
            String pct = total > 0 ? String.format("%.1f%%", count * 100.0 / total) : "0%";
            writer.printf("%s,%d,%d,%s%n", labels.getOrDefault(type, type), count, growth, pct);
        }
        writer.printf("合计,%d,%d,100%%%n", total, overview.getOrDefault("totalWeekGrowth", 0L));
        writer.println();
        writer.println("爬虫统计,数值");
        writer.printf("总运行次数,%s%n", crawler.getOrDefault("totalRuns", 0));
        writer.printf("成功次数,%s%n", crawler.getOrDefault("successRuns", 0));
        writer.printf("失败次数,%s%n", crawler.getOrDefault("failedRuns", 0));
        writer.printf("成功率,%s%%%n", crawler.getOrDefault("successRate", 0));
        writer.printf("总抓取量,%s%n", crawler.getOrDefault("totalItemsCrawled", 0));
        writer.println();
        writer.println("资源类型,数量");
        writer.printf("在线资源,%s%n", resources.getOrDefault("online", 0));
        writer.printf("磁力资源,%s%n", resources.getOrDefault("magnet", 0));
        writer.printf("网盘资源,%s%n", resources.getOrDefault("cloud", 0));
        writer.printf("合计,%s%n", resources.getOrDefault("total", 0));
        writer.printf("用户数,%s%n", overview.getOrDefault("totalUsers", 0));
        writer.flush();
    }

    /**
     * 导出内容列表为 CSV
     */
    @GetMapping("/export/content")
    public void exportContent(
            @RequestParam(required = false) String type,
            HttpServletResponse response) throws IOException {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=film-forest-content-" + (type != null ? type : "all") + "-" + date + ".csv");
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        List<Map<String, Object>> rows = statsService.getContentList(type);
        PrintWriter writer = response.getWriter();
        writer.println("ID,类型,标题,年份,豆瓣评分,IMDB评分,状态,创建时间");
        Map<String, String> labels = Map.of(
                "movie", "电影", "drama", "剧集", "variety", "综艺",
                "anime", "动漫", "short_drama", "短剧");
        for (Map<String, Object> row : rows) {
            String rowType = String.valueOf(row.getOrDefault("type", ""));
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                    row.getOrDefault("id", ""),
                    labels.getOrDefault(rowType, rowType),
                    escapeCsv(String.valueOf(row.getOrDefault("title", ""))),
                    row.getOrDefault("year", ""),
                    row.getOrDefault("score_douban", ""),
                    row.getOrDefault("score_imdb", ""),
                    row.getOrDefault("status", ""),
                    row.getOrDefault("created_at", ""));
        }
        writer.flush();
    }

    /**
     * 导出搜索热词为 CSV
     */
    @GetMapping("/export/hot-search")
    public void exportHotSearch(
            @RequestParam(defaultValue = "30") int days,
            HttpServletResponse response) throws IOException {
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=film-forest-hot-search-" + date + ".csv");
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        List<Map<String, Object>> keywords = statsService.getHotSearchKeywords(days, 100);
        PrintWriter writer = response.getWriter();
        writer.println("排名,关键词,搜索次数,最近搜索时间");
        int rank = 1;
        for (Map<String, Object> item : keywords) {
            writer.printf("%d,%s,%s,%s%n",
                    rank++,
                    escapeCsv(String.valueOf(item.getOrDefault("keyword", ""))),
                    item.getOrDefault("count", ""),
                    item.getOrDefault("lastSearchAt", ""));
        }
        writer.flush();
    }

    /** CSV 字段转义 */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
