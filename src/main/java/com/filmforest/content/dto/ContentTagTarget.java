package com.filmforest.content.dto;

/** 管理端批量读取题材关联的多态内容定位。 */
public record ContentTagTarget(String contentType, Long contentId) {
}
