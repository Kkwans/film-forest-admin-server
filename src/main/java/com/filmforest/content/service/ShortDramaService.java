package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.ShortDrama;

/**
 * 短剧服务接口
 * 提供短剧列表分页查询和详情获取
 */
public interface ShortDramaService extends IService<ShortDrama> {

    /**
     * 分页查询短剧列表
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param year 年份筛选
     * @param genre 类型筛选
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<ShortDrama> pageList(int pageNum, int pageSize, Integer year, String genre, String keyword);

    /**
     * 获取短剧详情（含在线资源和播放源）
     * @param id 短剧ID
     * @return 短剧详情，不存在时抛出 BusinessException
     */
    ShortDrama getDetail(Long id);
}
