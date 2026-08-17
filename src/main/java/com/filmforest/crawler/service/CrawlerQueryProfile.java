package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 为游标和 Job 快照生成不含凭据的稳定查询 profile。 */
public final class CrawlerQueryProfile {

    private CrawlerQueryProfile() {
    }

    public static String canonical(CrawlerSchedule schedule) {
        Map<String, String> filters = schedule.getSourceFilters() == null
                ? Map.of()
                : new TreeMap<>(schedule.getSourceFilters());
        String filterText = filters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return String.join("|",
                safe(schedule.getSourceSite()),
                safe(schedule.getAdapterCode()),
                safe(schedule.getContentType()),
                safe(schedule.getSourceSort()),
                safe(schedule.getTraversalMode()),
                safe(schedule.getEndPolicy()),
                filterText,
                safe(schedule.getGenreFilter()));
    }

    public static String hash(CrawlerSchedule schedule) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(schedule).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }

    public static String snapshot(CrawlerSchedule schedule) {
        return canonical(schedule);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
