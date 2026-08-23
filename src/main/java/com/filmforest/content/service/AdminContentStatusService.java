package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.common.type.ContentType;
import com.filmforest.content.dto.ContentStatusBatchResult;
import com.filmforest.content.dto.ContentStatusBatchAllRequest;
import com.filmforest.content.dto.ContentStatusTarget;
import com.filmforest.content.model.ContentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 跨五类内容的原子状态变更边界。 */
@Service
public class AdminContentStatusService {

    private static final int MAX_BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public AdminContentStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ContentStatusBatchResult updateStatuses(List<ContentStatusTarget> targets, int status) {
        if (!ContentStatus.isValid(status)) {
            throw new IllegalArgumentException("状态只允许 0、1 或 2");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("至少选择一条内容");
        }
        if (targets.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("一次最多更新 100 条内容");
        }

        Map<ContentType, LinkedHashSet<Long>> groupedIds = normalizeTargets(targets);
        groupedIds.forEach(this::requireAllActive);

        int updated = 0;
        for (Map.Entry<ContentType, LinkedHashSet<Long>> entry : groupedIds.entrySet()) {
            List<Long> ids = List.copyOf(entry.getValue());
            String placeholders = placeholders(ids.size());
            List<Object> arguments = new ArrayList<>(ids.size() + 1);
            arguments.add(status);
            arguments.addAll(ids);
            int rows = jdbcTemplate.update(
                    "UPDATE " + entry.getKey().value()
                            + " SET status = ?, updated_at = NOW()"
                            + " WHERE is_deleted = 0 AND id IN (" + placeholders + ")",
                    arguments.toArray()
            );
            if (rows != ids.size()) {
                throw new BusinessException(409, "内容状态在提交期间发生变化，请刷新后重试");
            }
            updated += rows;
        }
        return new ContentStatusBatchResult(targets.size(), updated, status);
    }

    /**
     * 按当前内容列表筛选条件跨分页更新状态。
     * 筛选条件和更新在同一个事务中执行，避免前端逐页循环造成漏项或部分完成。
     */
    @Transactional
    public ContentStatusBatchResult updateAllStatuses(ContentStatusBatchAllRequest request) {
        if (request == null || request.targetStatus() == null
                || !ContentStatus.isValid(request.targetStatus())) {
            throw new IllegalArgumentException("状态只允许 0、1 或 2");
        }
        if (request.currentStatus() != null && !ContentStatus.isValid(request.currentStatus())) {
            throw new IllegalArgumentException("筛选状态只允许 0、1 或 2");
        }

        List<ContentType> contentTypes = resolveTypes(request.type());
        int requested = 0;
        int updated = 0;
        for (ContentType type : contentTypes) {
            StringBuilder predicate = new StringBuilder(" WHERE is_deleted = 0");
            List<Object> arguments = new ArrayList<>();
            if (request.currentStatus() != null) {
                predicate.append(" AND status = ?");
                arguments.add(request.currentStatus());
            }
            predicate.append(" AND status <> ?");
            arguments.add(request.targetStatus());
            if (request.keyword() != null && !request.keyword().isBlank()) {
                predicate.append(" AND (title LIKE ? OR CAST(alias AS CHAR) LIKE ?)");
                String pattern = "%" + request.keyword().trim() + "%";
                arguments.add(pattern);
                arguments.add(pattern);
            }

            Integer matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + type.value() + predicate,
                    Integer.class,
                    arguments.toArray()
            );
            int count = matching == null ? 0 : matching;
            requested += count;

            if (count > 0) {
                List<Object> updateArguments = new ArrayList<>();
                updateArguments.add(request.targetStatus());
                updateArguments.addAll(arguments);
                int rows = jdbcTemplate.update(
                        "UPDATE " + type.value()
                                + " SET status = ?, updated_at = NOW()"
                                + predicate,
                        updateArguments.toArray()
                );
                if (rows != count) {
                    throw new BusinessException(409, "内容状态在提交期间发生变化，请刷新后重试");
                }
                updated += rows;
            }
        }
        return new ContentStatusBatchResult(requested, updated, request.targetStatus());
    }

    private List<ContentType> resolveTypes(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return List.of(ContentType.values());
        }
        String canonicalType = "short".equalsIgnoreCase(rawType.trim())
                ? "short_drama" : rawType.trim().toLowerCase(Locale.ROOT);
        return List.of(ContentType.fromValue(canonicalType)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + rawType)));
    }

    private Map<ContentType, LinkedHashSet<Long>> normalizeTargets(List<ContentStatusTarget> targets) {
        Map<ContentType, LinkedHashSet<Long>> grouped = new LinkedHashMap<>();
        for (ContentStatusTarget target : targets) {
            if (target == null || target.type() == null || target.type().isBlank()
                    || target.id() == null || target.id() <= 0) {
                throw new IllegalArgumentException("内容类型和 ID 必须有效");
            }
            String rawType = target.type().trim().toLowerCase(Locale.ROOT);
            String canonicalType = "short".equals(rawType) ? "short_drama" : rawType;
            ContentType type = ContentType.fromValue(canonicalType)
                    .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + target.type()));
            boolean added = grouped.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
                    .add(target.id());
            if (!added) {
                throw new IllegalArgumentException("批量请求包含重复内容: " + canonicalType + " #" + target.id());
            }
        }
        return grouped;
    }

    private void requireAllActive(ContentType type, LinkedHashSet<Long> groupedIds) {
        List<Long> ids = List.copyOf(groupedIds);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + type.value()
                        + " WHERE is_deleted = 0 AND id IN (" + placeholders(ids.size()) + ")",
                Long.class,
                ids.toArray()
        );
        if (count == null || count != ids.size()) {
            throw new BusinessException(409, "所选内容已不存在或已删除，请刷新后重试");
        }
    }

    private static String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }
}
