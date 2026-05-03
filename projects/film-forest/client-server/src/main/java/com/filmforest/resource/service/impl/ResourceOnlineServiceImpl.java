package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.service.ResourceOnlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线播放资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceOnlineServiceImpl extends ServiceImpl<ResourceOnlineMapper, ResourceOnline>
        implements ResourceOnlineService {

    @Override
    public List<ResourceOnline> listByContent(String contentType, Long contentId) {
        return lambdaQuery()
                .eq(ResourceOnline::getContentType, contentType)
                .eq(ResourceOnline::getContentId, contentId)
                .orderByAsc(ResourceOnline::getSort)
                .list();
    }

    @Override
    public List<ResourceOnline> listByContentAndEpisode(String contentType, Long contentId, Long episodeId) {
        return lambdaQuery()
                .eq(ResourceOnline::getContentType, contentType)
                .eq(ResourceOnline::getContentId, contentId)
                .eq(episodeId != null, ResourceOnline::getEpisodeId, episodeId)
                .orderByAsc(ResourceOnline::getSort)
                .list();
    }
}
