package com.filmforest.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.resource.entity.ResourceMagnet;

/**
 * 磁力链接资源 Service
 */
public interface ResourceMagnetService extends IService<ResourceMagnet> {

    /**
     * 根据内容类型和内容ID查询磁力链接资源列表
     */
    java.util.List<ResourceMagnet> listByContent(String contentType, Long contentId);

    /**
     * 根据内容类型、内容ID和集数查询磁力链接资源
     */
    java.util.List<ResourceMagnet> listByContentAndEpisode(String contentType, Long contentId, Long episodeId);
}
