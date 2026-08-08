package com.filmforest.content.service;

import com.filmforest.common.dto.PageResult;
import com.filmforest.common.type.ContentType;
import com.filmforest.content.dto.AdminContentItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用一次白名单 UNION 查询提供跨五类内容的真实分页。 */
@Service
public class AdminContentQueryService {

    private static final List<ContentType> ALL_TYPES = List.of(ContentType.values());
    private static final Map<String, String> SORT_COLUMNS = new LinkedHashMap<>();

    static {
        SORT_COLUMNS.put("createdAt", "created_at");
        SORT_COLUMNS.put("updatedAt", "updated_at");
        SORT_COLUMNS.put("year", "year");
        SORT_COLUMNS.put("title", "title");
        SORT_COLUMNS.put("score", "score_douban");
        SORT_COLUMNS.put("status", "status");
    }

    private final JdbcTemplate jdbcTemplate;

    public AdminContentQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<AdminContentItem> search(
            String type,
            Integer status,
            String keyword,
            String sort,
            String sortDir,
            int page,
            int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ContentType> selectedTypes = resolveTypes(type);
        validateStatus(status);

        List<Object> filterArguments = new ArrayList<>();
        String unionSql = selectedTypes.stream()
                .map(contentType -> selectSql(contentType, status, keyword, filterArguments))
                .reduce((left, right) -> left + " UNION ALL " + right)
                .orElseThrow();

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + unionSql + ") content",
                Long.class,
                filterArguments.toArray()
        );
        long total = count == null ? 0 : count;
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        long offset = (long) (safePage - 1) * safeSize;
        if (offset >= total) {
            return new PageResult<>(List.of(), total, safeSize, safePage, pages);
        }

        String sortColumn = SORT_COLUMNS.getOrDefault(sort, "updated_at");
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        String dataSql = "SELECT * FROM (" + unionSql + ") content ORDER BY "
                + sortColumn + " " + direction + ", type ASC, id DESC LIMIT ? OFFSET ?";
        List<Object> dataArguments = new ArrayList<>(filterArguments);
        dataArguments.add(safeSize);
        dataArguments.add(offset);

        List<AdminContentItem> records = jdbcTemplate.query(
                dataSql,
                (resultSet, rowNum) -> new AdminContentItem(
                        resultSet.getLong("id"),
                        resultSet.getString("type"),
                        resultSet.getString("title"),
                        resultSet.getString("poster_url"),
                        getNullableInteger(resultSet.getObject("year")),
                        resultSet.getBigDecimal("score_douban"),
                        resultSet.getInt("status"),
                        toLocalDateTime(resultSet.getTimestamp("created_at")),
                        toLocalDateTime(resultSet.getTimestamp("updated_at"))
                ),
                dataArguments.toArray()
        );
        return new PageResult<>(records, total, safeSize, safePage, pages);
    }

    private static List<ContentType> resolveTypes(String type) {
        if (type == null || type.isBlank()) {
            return ALL_TYPES;
        }
        String canonical = "short".equals(type) ? "short_drama" : type;
        return List.of(ContentType.fromValue(canonical)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + type)));
    }

    private static void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new IllegalArgumentException("状态只允许 0 或 1");
        }
    }

    private static String selectSql(
            ContentType contentType,
            Integer status,
            String keyword,
            List<Object> arguments
    ) {
        String table = contentType.value();
        StringBuilder sql = new StringBuilder("SELECT id, '")
                .append(table)
                .append("' AS type, title, poster_url, year, score_douban, status, created_at, updated_at FROM ")
                .append(table)
                .append(" WHERE is_deleted = 0");
        if (status != null) {
            sql.append(" AND status = ?");
            arguments.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title LIKE ? OR CAST(alias AS CHAR) LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return sql.toString();
    }

    private static Integer getNullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
