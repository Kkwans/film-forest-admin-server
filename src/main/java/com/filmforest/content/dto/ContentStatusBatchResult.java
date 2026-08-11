package com.filmforest.content.dto;

/** 批量状态更新的稳定响应。 */
public record ContentStatusBatchResult(
        int requested,
        int updated,
        int status
) {
}
