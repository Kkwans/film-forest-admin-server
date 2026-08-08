package com.filmforest.content.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端跨内容类型列表所需的最小摘要。 */
public record AdminContentItem(
        long id,
        String type,
        String title,
        String posterUrl,
        Integer year,
        BigDecimal scoreDouban,
        int status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
