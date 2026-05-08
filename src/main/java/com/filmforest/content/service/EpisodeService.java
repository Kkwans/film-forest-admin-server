package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Episode;

import java.util.List;

public interface EpisodeService extends IService<Episode> {

    /**
     * 分页查询剧集列表
     */
    IPage<Episode> pageList(int pageNum, int pageSize, Long contentId, String contentType);

    /**
     * 根据内容ID获取所有剧集
     */
    List<Episode> listByContentId(Long contentId, String contentType);

    /**
     * 批量保存剧集（去重：同 contentId + episodeNumber 不重复插入）
     */
    int saveBatchDistinct(List<Episode> episodes);
}
