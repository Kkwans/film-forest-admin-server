package com.filmforest.stats.service.impl;

import com.filmforest.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据统计服务实现
 * 使用原生 SQL 聚合查询，避免为统计功能引入额外依赖
 */
@Service
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 内容表列表（与 content_type 对应） */
    private static final String[] CONTENT_TABLES = {"movie", "drama", "variety", "anime", "short_drama"};

    /** 内容类型中文名映射 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "movie", "电影",
            "drama", "剧集",
            "variety", "综艺",
            "anime", "动漫",
            "short_drama", "短剧"
    );

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // 1. 各类型内容数量
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        long totalContent = 0;
        for (String table : CONTENT_TABLES) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = 0", Long.class);
                count = count != null ? count : 0L;
                typeCounts.put(table, count);
                totalContent += count;
            } catch (Exception e) {
                log.warn("查询 {} 表数量失败", table, e);
                typeCounts.put(table, 0L);
            }
        }
        overview.put("typeCounts", typeCounts);
        overview.put("totalContent", totalContent);

        // 2. 7日新增内容
        Map<String, Long> weekGrowth = new LinkedHashMap<>();
        long totalWeekGrowth = 0;
        for (String table : CONTENT_TABLES) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)",
                        Long.class);
                count = count != null ? count : 0L;
                weekGrowth.put(table, count);
                totalWeekGrowth += count;
            } catch (Exception e) {
                log.warn("查询 {} 表7日增长失败", table, e);
                weekGrowth.put(table, 0L);
            }
        }
        overview.put("weekGrowth", weekGrowth);
        overview.put("totalWeekGrowth", totalWeekGrowth);

        // 3. 爬虫统计
        try {
            Integer totalRuns = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM crawler_task_log", Integer.class);
            Integer successRuns = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM crawler_task_log WHERE status = 'success'", Integer.class);
            Integer failedRuns = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM crawler_task_log WHERE status = 'failed'", Integer.class);
            Integer totalItemsCrawled = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(items_crawled), 0) FROM crawler_task_log WHERE status = 'success'",
                    Integer.class);

            totalRuns = totalRuns != null ? totalRuns : 0;
            successRuns = successRuns != null ? successRuns : 0;
            failedRuns = failedRuns != null ? failedRuns : 0;
            totalItemsCrawled = totalItemsCrawled != null ? totalItemsCrawled : 0;

            double successRate = totalRuns > 0 ? (successRuns * 100.0 / totalRuns) : 0;

            Map<String, Object> crawlerStats = new LinkedHashMap<>();
            crawlerStats.put("totalRuns", totalRuns);
            crawlerStats.put("successRuns", successRuns);
            crawlerStats.put("failedRuns", failedRuns);
            crawlerStats.put("successRate", Math.round(successRate * 10) / 10.0);
            crawlerStats.put("totalItemsCrawled", totalItemsCrawled);
            overview.put("crawler", crawlerStats);
        } catch (Exception e) {
            log.warn("查询爬虫统计失败", e);
            Map<String, Object> crawlerStats = new LinkedHashMap<>();
            crawlerStats.put("totalRuns", 0);
            crawlerStats.put("successRuns", 0);
            crawlerStats.put("failedRuns", 0);
            crawlerStats.put("successRate", 0);
            crawlerStats.put("totalItemsCrawled", 0);
            overview.put("crawler", crawlerStats);
        }

        // 4. 资源统计
        try {
            Integer onlineCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM resource_online", Integer.class);
            Integer magnetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM resource_magnet", Integer.class);
            Integer cloudCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM resource_cloud", Integer.class);

            Map<String, Object> resourceStats = new LinkedHashMap<>();
            resourceStats.put("online", onlineCount != null ? onlineCount : 0);
            resourceStats.put("magnet", magnetCount != null ? magnetCount : 0);
            resourceStats.put("cloud", cloudCount != null ? cloudCount : 0);
            resourceStats.put("total", (onlineCount != null ? onlineCount : 0)
                    + (magnetCount != null ? magnetCount : 0)
                    + (cloudCount != null ? cloudCount : 0));
            overview.put("resources", resourceStats);
        } catch (Exception e) {
            log.warn("查询资源统计失败", e);
            Map<String, Object> resourceStats = new LinkedHashMap<>();
            resourceStats.put("online", 0);
            resourceStats.put("magnet", 0);
            resourceStats.put("cloud", 0);
            resourceStats.put("total", 0);
            overview.put("resources", resourceStats);
        }

        // 5. 用户数
        try {
            Integer userCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user WHERE is_deleted = 0", Integer.class);
            overview.put("totalUsers", userCount != null ? userCount : 0);
        } catch (Exception e) {
            log.warn("查询用户数失败", e);
            overview.put("totalUsers", 0);
        }

        return overview;
    }

    @Override
    public Map<String, Object> getTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 构建日期列表（确保无数据的日期也有 0 值）
        List<String> dates = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            dates.add(d.format(fmt));
        }

        // 按类型查询每日新增
        Map<String, Map<String, Long>> seriesData = new LinkedHashMap<>();
        for (String table : CONTENT_TABLES) {
            Map<String, Long> dailyCounts = new LinkedHashMap<>();
            // 初始化所有日期为 0
            for (String date : dates) {
                dailyCounts.put(date, 0L);
            }
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT DATE(created_at) AS d, COUNT(*) AS cnt FROM " + table
                                + " WHERE is_deleted = 0 AND created_at >= ? AND created_at < ?"
                                + " GROUP BY DATE(created_at)",
                        startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
                for (Map<String, Object> row : rows) {
                    String dateStr = row.get("d").toString();
                    Long cnt = ((Number) row.get("cnt")).longValue();
                    dailyCounts.put(dateStr, cnt);
                }
            } catch (Exception e) {
                log.warn("查询 {} 增长趋势失败", table, e);
            }
            seriesData.put(table, dailyCounts);
        }

        // 组装返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("series", seriesData);
        result.put("labels", TYPE_LABELS);
        return result;
    }

    @Override
    public List<Map<String, Object>> getHotSearchKeywords(int days, int limit) {
        try {
            // 检查 search_log 表是否存在
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'search_log'",
                    Integer.class);
            if (tableExists == null || tableExists == 0) {
                log.warn("[Stats] search_log 表不存在，跳过热门搜索统计");
                return Collections.emptyList();
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT keyword, COUNT(*) AS search_count, MAX(created_at) AS last_search_at " +
                    "FROM search_log " +
                    "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                    "GROUP BY keyword " +
                    "ORDER BY search_count DESC " +
                    "LIMIT ?",
                    days, limit);

            // 格式化结果
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("keyword", row.get("keyword"));
                item.put("count", ((Number) row.get("search_count")).longValue());
                item.put("lastSearchAt", row.get("last_search_at"));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.error("[Stats] 查询热门搜索词失败", e);
            return Collections.emptyList();
        }
    }
}
