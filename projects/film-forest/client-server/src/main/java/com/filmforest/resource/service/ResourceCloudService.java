package com.filmforest.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.resource.entity.ResourceCloud;

/**
 * 网盘链接资源 Service
 */
public interface ResourceCloudService extends IService<ResourceCloud> {

    /**
     * 根据内容类型和内容ID查询网盘资源列表
     */
    java.util.List<ResourceCloud> listByContent(String contentType, Long contentId);

    /**
     * 根据内容类型、内容ID和集数查询网盘资源
     */
    java.util.List<ResourceCloud> listByContentAndEpisode(String contentType, Long contentId, Long episodeId);
}
