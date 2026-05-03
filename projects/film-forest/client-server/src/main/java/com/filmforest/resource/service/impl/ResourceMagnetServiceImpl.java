package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.service.ResourceMagnetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 磁力链接资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceMagnetServiceImpl extends ServiceImpl<ResourceMagnetMapper, ResourceMagnet>
        implements ResourceMagnetService {

    @Override
    public List<ResourceMagnet> listByContent(String contentType, Long contentId) {
        return lambdaQuery()
                .eq(ResourceMagnet::getContentType, contentType)
                .eq(ResourceMagnet::getContentId, contentId)
                .orderByAsc(ResourceMagnet::getSort)
                .list();
    }

    @Override
    public List<ResourceMagnet> listByContentAndEpisode(String contentType, Long contentId, Long episodeId) {
        return lambdaQuery()
                .eq(ResourceMagnet::getContentType, contentType)
                .eq(ResourceMagnet::getContentId, contentId)
                .eq(episodeId != null, ResourceMagnet::getEpisodeId, episodeId)
                .orderByAsc(ResourceMagnet::getSort)
                .list();
    }
}
