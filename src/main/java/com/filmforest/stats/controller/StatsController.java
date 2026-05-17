package com.filmforest.stats.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.stats.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据统计 API
 * 提供管理端仪表盘所需的各类统计数据
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    /**
     * 数据概览
     * 返回：各类型内容数量、7日增长、爬虫成功率、资源总数、用户数
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.ok(statsService.getOverview());
    }

    /**
     * 内容增长趋势
     * @param days 天数，默认 30
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        return Result.ok(statsService.getTrend(days));
    }
}
