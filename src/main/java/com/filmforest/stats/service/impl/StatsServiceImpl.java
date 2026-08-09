package com.filmforest.stats.service.impl;

import com.filmforest.common.type.ContentType;
import com.filmforest.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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
    @Cacheable(value = "stats", key = "'overview'")
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
    @Cacheable(value = "stats", key = "'trend_' + #days")
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
    @Cacheable(value = "stats", key = "'hotsearch_' + #days + '_' + #limit")
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

    @Override
    @Cacheable(value = "stats", key = "'report_' + #days")
    public Map<String, Object> getReport(int days) {
        Map<String, Object> report = new LinkedHashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        // 1. 各类型新增数量对比
        List<Map<String, Object>> typeGrowth = new ArrayList<>();
        for (String table : CONTENT_TABLES) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table
                                + " WHERE is_deleted = 0 AND created_at >= ? AND created_at < ?",
                        Long.class,
                        startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", table);
                item.put("label", TYPE_LABELS.getOrDefault(table, table));
                item.put("count", count != null ? count : 0);
                typeGrowth.add(item);
            } catch (Exception e) {
                log.warn("[Report] 查询 {} 新增数量失败", table, e);
            }
        }
        report.put("typeGrowth", typeGrowth);

        // 2. 爬虫效率统计
        try {
            Map<String, Object> efficiency = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS total_runs, " +
                    "COALESCE(SUM(CASE WHEN status='success' THEN 1 ELSE 0 END), 0) AS success_runs, " +
                    "COALESCE(SUM(CASE WHEN status='failed' THEN 1 ELSE 0 END), 0) AS failed_runs, " +
                    "COALESCE(SUM(items_crawled), 0) AS total_items, " +
                    "COALESCE(SUM(items_added), 0) AS total_added, " +
                    "COALESCE(SUM(items_updated), 0) AS total_updated, " +
                    "COALESCE(AVG(duration_ms), 0) AS avg_duration " +
                    "FROM crawler_task_log WHERE started_at >= ? AND started_at < ?",
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
            Map<String, Object> crawlerEfficiency = new LinkedHashMap<>();
            long totalRuns = numberAsLong(efficiency, "total_runs");
            long successRuns = numberAsLong(efficiency, "success_runs");
            crawlerEfficiency.put("totalRuns", totalRuns);
            crawlerEfficiency.put("successRuns", successRuns);
            crawlerEfficiency.put("failedRuns", numberAsLong(efficiency, "failed_runs"));
            crawlerEfficiency.put("totalItems", numberAsLong(efficiency, "total_items"));
            crawlerEfficiency.put("totalAdded", numberAsLong(efficiency, "total_added"));
            crawlerEfficiency.put("totalUpdated", numberAsLong(efficiency, "total_updated"));
            crawlerEfficiency.put("avgDurationMs", Math.round(numberAsDouble(efficiency, "avg_duration")));
            crawlerEfficiency.put("successRate", totalRuns > 0 ? Math.round(successRuns * 1000.0 / totalRuns) / 10.0 : 0);
            report.put("crawlerEfficiency", crawlerEfficiency);
        } catch (Exception e) {
            log.warn("[Report] 查询爬虫效率失败", e);
        }

        // 3. 内容质量统计（评分分布）
        try {
            List<Map<String, Object>> qualityStats = new ArrayList<>();
            for (String table : CONTENT_TABLES) {
                try {
                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "SELECT COUNT(*) AS total, " +
                            "COALESCE(SUM(CASE WHEN score_douban >= 8 THEN 1 ELSE 0 END), 0) AS high_score, " +
                            "COALESCE(SUM(CASE WHEN score_douban >= 5 AND score_douban < 8 THEN 1 ELSE 0 END), 0) AS mid_score, " +
                            "COALESCE(SUM(CASE WHEN score_douban > 0 AND score_douban < 5 THEN 1 ELSE 0 END), 0) AS low_score, " +
                            "COALESCE(AVG(CASE WHEN score_douban > 0 THEN score_douban END), 0) AS avg_score " +
                            "FROM " + table + " WHERE is_deleted = 0",
                            (Object[]) null);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", table);
                    item.put("label", TYPE_LABELS.getOrDefault(table, table));
                    item.put("total", numberAsLong(row, "total"));
                    item.put("highScore", numberAsLong(row, "high_score"));
                    item.put("midScore", numberAsLong(row, "mid_score"));
                    item.put("lowScore", numberAsLong(row, "low_score"));
                    item.put("avgScore", Math.round(numberAsDouble(row, "avg_score") * 10) / 10.0);
                    qualityStats.add(item);
                } catch (Exception e) {
                    log.warn("[Report] 查询 {} 质量统计失败", table, e);
                }
            }
            report.put("qualityStats", qualityStats);
        } catch (Exception e) {
            log.warn("[Report] 查询内容质量统计失败", e);
        }

        // 4. 每日新增趋势（按天汇总所有类型）
        try {
            List<String> dates = new ArrayList<>();
            List<Long> dailyTotals = new ArrayList<>();
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                dates.add(d.format(DateTimeFormatter.ofPattern("MM-dd")));
                long dayTotal = 0;
                for (String table : CONTENT_TABLES) {
                    try {
                        Long cnt = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM " + table
                                        + " WHERE is_deleted = 0 AND DATE(created_at) = ?",
                                Long.class, d);
                        dayTotal += cnt != null ? cnt : 0;
                    } catch (Exception ignored) {}
                }
                dailyTotals.add(dayTotal);
            }
            Map<String, Object> dailyTrend = new LinkedHashMap<>();
            dailyTrend.put("dates", dates);
            dailyTrend.put("totals", dailyTotals);
            report.put("dailyTrend", dailyTrend);
        } catch (Exception e) {
            log.warn("[Report] 查询每日趋势失败", e);
        }

        report.put("days", days);
        report.put("startDate", startDate.toString());
        report.put("endDate", endDate.toString());
        return report;
    }

    private static long numberAsLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double numberAsDouble(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    @Override
    public List<Map<String, Object>> getContentList(ContentType type) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] tables = type != null ? new String[]{type.value()} : CONTENT_TABLES;
        for (String table : tables) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, '" + table + "' AS type, title, year, score_douban, score_imdb, " +
                        "CASE WHEN status = 1 THEN '已发布' ELSE '未发布' END AS status, " +
                        "created_at FROM " + table + " WHERE is_deleted = 0 ORDER BY created_at DESC");
                result.addAll(rows);
            } catch (Exception e) {
                log.warn("[Export] 查询 {} 列表失败", table, e);
            }
        }
        return result;
    }
}
