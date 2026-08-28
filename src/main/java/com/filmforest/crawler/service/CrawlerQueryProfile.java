package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 为游标和 Job 快照生成不含凭据的稳定查询 profile。 */
public final class CrawlerQueryProfile {

    private CrawlerQueryProfile() {
    }

    public static String canonical(CrawlerSchedule schedule) {
        return canonical(schedule, true);
    }

    /**
     * 生成分页游标使用的 profile。资源范围只影响详情页资源解析，不影响来源列表分页，
     * 因此不能因为切换 DOWNLOADS/ONLINE/ALL 而让续爬游标失效。
     */
    public static String cursorCanonical(CrawlerSchedule schedule) {
        return canonical(schedule, false);
    }

    public static String cursorHash(CrawlerSchedule schedule) {
        return sha256(cursorCanonical(schedule));
    }

    public static String cursorSnapshot(CrawlerSchedule schedule) {
        return cursorCanonical(schedule);
    }

    /** 将旧版（未包含 resourceScope）和 V29 后快照统一到游标 profile。 */
    public static boolean cursorSnapshotMatches(String snapshot, CrawlerSchedule schedule) {
        String normalized = normalizeCursorSnapshot(snapshot);
        return normalized != null && Objects.equals(normalized, cursorCanonical(schedule));
    }

    private static String canonical(CrawlerSchedule schedule, boolean includeResourceScope) {
        Map<String, String> filters = schedule.getSourceFilters() == null
                ? Map.of()
                : new TreeMap<>(schedule.getSourceFilters());
        String filterText = filters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        String base = String.join("|",
                safe(schedule.getSourceSite()),
                safe(schedule.getAdapterCode()),
                safe(schedule.getContentType()),
                safe(schedule.getSourceSort()),
                safe(schedule.getTraversalMode()),
                safe(schedule.getEndPolicy()),
                filterText,
                safe(schedule.getGenreFilter()));
        if (!includeResourceScope) return base;
        String[] parts = base.split("\\|", -1);
        return String.join("|", parts[0], parts[1], parts[2],
                safe(schedule.getResourceScope()), parts[3], parts[4], parts[5], parts[6], parts[7]);
    }

    public static String hash(CrawlerSchedule schedule) {
        return sha256(canonical(schedule));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte digestByte : digest) result.append(String.format("%02x", digestByte));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }

    public static String snapshot(CrawlerSchedule schedule) {
        return canonical(schedule);
    }

    private static String normalizeCursorSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return null;
        String[] parts = snapshot.split("\\|", -1);
        if (parts.length == 8) return snapshot;
        if (parts.length != 9) return null;
        return String.join("|", parts[0], parts[1], parts[2], parts[4],
                parts[5], parts[6], parts[7], parts[8]);
    }

    public static String filterSnapshot(CrawlerSchedule schedule) {
        Map<String, String> filters = schedule.getSourceFilters() == null
                ? Map.of() : new TreeMap<>(schedule.getSourceFilters());
        return filters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public static Map<String, String> parseFilterSnapshot(String snapshot) {
        Map<String, String> result = new LinkedHashMap<>();
        if (snapshot == null || snapshot.isBlank()) return result;
        for (String pair : snapshot.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && separator < pair.length() - 1) {
                result.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
