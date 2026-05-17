package com.filmforest.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 数据统计服务接口
 * 提供管理端仪表盘所需的各类统计数据
 */
public interface StatsService {

    /**
     * 获取数据概览
     * 包含：各类型内容数量、7日增长、爬虫成功率、资源总数
     */
    Map<String, Object> getOverview();

    /**
     * 获取内容增长趋势（近30天）
     * 按日期+类型聚合每日新增内容数
     */
    Map<String, Object> getTrend(int days);

    /**
     * 获取热门搜索词 Top N
     * 按搜索次数降序，支持时间范围过滤
     */
    List<Map<String, Object>> getHotSearchKeywords(int days, int limit);
}
