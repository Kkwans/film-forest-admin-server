package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Movie;

/**
 * 电影服务接口
 * 提供电影列表分页查询和详情获取
 */
public interface MovieService extends IService<Movie> {

    /**
     * 分页查询电影列表
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param year 年份筛选
     * @param genre 类型筛选
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<Movie> pageList(int pageNum, int pageSize, Integer year, String genre, String keyword);

    /**
     * 获取电影详情（含在线资源和播放源）
     * @param id 电影ID
     * @return 电影详情，不存在时抛出 BusinessException
     */
    Movie getDetail(Long id);
}
