package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Anime;

/**
 * 动漫服务接口
 * 提供动漫列表分页查询和详情获取
 */
public interface AnimeService extends IService<Anime> {

    /**
     * 分页查询动漫列表
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param year 年份筛选
     * @param genre 类型筛选
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String genre, String keyword);

    /**
     * 获取动漫详情（含在线资源和播放源）
     * @param id 动漫ID
     * @return 动漫详情，不存在时抛出 BusinessException
     */
    Anime getDetail(Long id);
}
