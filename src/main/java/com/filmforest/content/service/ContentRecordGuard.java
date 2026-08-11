package com.filmforest.content.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/** 多态内容关联写入前的存在性边界，防止生成孤儿标签或资源关系。 */
@Service
public class ContentRecordGuard {

    private static final Set<String> CONTENT_TABLES =
            Set.of("movie", "drama", "variety", "anime", "short_drama");

    private final JdbcTemplate jdbcTemplate;

    public ContentRecordGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireActiveRecord(String rawContentType, Long contentId) {
        if (contentId == null || contentId <= 0) {
            throw new IllegalArgumentException("内容 ID 必须为正整数");
        }
        String table = canonicalTable(rawContentType);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ? AND is_deleted = 0",
                Integer.class,
                contentId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("内容不存在或已删除，不能设置题材");
        }
    }

    private static String canonicalTable(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("内容类型不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("short".equals(normalized)) normalized = "short_drama";
        if (!CONTENT_TABLES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的内容类型: " + value);
        }
        return normalized;
    }
}
