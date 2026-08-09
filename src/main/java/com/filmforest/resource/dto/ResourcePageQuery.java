package com.filmforest.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ResourcePageQuery {

    @Min(value = 1, message = "页码必须大于 0")
    private int page = 1;

    @Min(value = 1, message = "每页数量必须大于 0")
    @Max(value = 100, message = "每页最多查询 100 条")
    private int size = 20;

    private String keyword;
    private String contentType;
    private Long contentId;
    private String source;
    private String status;
    private String resolution;
    private String diskType;
    private String sort = "createdAt";
    private String order = "desc";
}
