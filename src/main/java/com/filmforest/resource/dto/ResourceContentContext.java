package com.filmforest.resource.dto;

/** 资源关联内容的可读摘要，供管理端识别资源归属。 */
public record ResourceContentContext(
        String title,
        String alias,
        String posterUrl,
        Integer year,
        String releaseDate
) {
}
