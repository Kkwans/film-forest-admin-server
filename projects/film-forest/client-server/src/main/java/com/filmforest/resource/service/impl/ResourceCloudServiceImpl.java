package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.service.ResourceCloudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网盘链接资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceCloudServiceImpl extends ServiceImpl<ResourceCloudMapper, ResourceCloud>
        implements ResourceCloudService {

    @Override
    public List<ResourceCloud> listByContent(String contentType, Long contentId) {
        return lambdaQuery()
                .eq(ResourceCloud::getContentType, contentType)
                .eq(ResourceCloud::getContentId, contentId)
                .orderByAsc(ResourceCloud::getSort)
                .list();
    }

    @Override
    public List<ResourceCloud> listByContentAndEpisode(String contentType, Long contentId, Long episodeId) {
        return lambdaQuery()
                .eq(ResourceCloud::getContentType, contentType)
                .eq(ResourceCloud::getContentId, contentId)
                .eq(episodeId != null, ResourceCloud::getEpisodeId, episodeId)
                .orderByAsc(ResourceCloud::getSort)
                .list();
    }
}
