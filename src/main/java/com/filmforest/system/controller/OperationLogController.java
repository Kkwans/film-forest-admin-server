package com.filmforest.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.system.entity.OperationLog;
import com.filmforest.system.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志 API
 */
@RestController
@RequestMapping("/api/admin/logs")
public class OperationLogController {

    @Autowired
    private OperationLogService logService;

    /** 分页查询操作日志 */
    @GetMapping
    public Result<Page<OperationLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(logService.listLogs(page, size, action, module, status, keyword));
    }

    /** 获取日志统计 */
    @GetMapping("/stats")
    public Result<Object> stats() {
        long total = logService.count();
        long today = logService.count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog>()
                .ge(OperationLog::getCreatedAt, java.time.LocalDate.now().atStartOfDay())
        );
        long failed = logService.count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getStatus, 0)
        );
        return Result.ok(new java.util.LinkedHashMap<>() {{
            put("total", total);
            put("today", today);
            put("failed", failed);
        }});
    }
}
