package com.filmforest.stats.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.stats.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * 热门搜索词
     * @param days 时间范围（天），默认 30
     * @param limit 返回数量，默认 15
     */
    @GetMapping("/hot-search")
    public Result<List<Map<String, Object>>> getHotSearch(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "15") int limit) {
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        if (limit < 1) limit = 10;
        if (limit > 50) limit = 50;
        return Result.ok(statsService.getHotSearchKeywords(days, limit));
    }
}
