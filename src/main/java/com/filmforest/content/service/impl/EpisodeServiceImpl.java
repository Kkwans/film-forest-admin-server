package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Episode;
import com.filmforest.content.mapper.EpisodeMapper;
import com.filmforest.content.service.EpisodeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class EpisodeServiceImpl extends ServiceImpl<EpisodeMapper, Episode> implements EpisodeService {

    @Override
    public IPage<Episode> pageList(int pageNum, int pageSize, Long contentId, String contentType) {
        LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
        if (contentId != null) {
            wrapper.eq(Episode::getContentId, contentId);
        }
        if (StringUtils.hasText(contentType)) {
            wrapper.eq(Episode::getContentType, contentType);
        }
        wrapper.orderByAsc(Episode::getEpisodeNumber);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<Episode> listByContentId(Long contentId, String contentType) {
        LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Episode::getContentId, contentId)
               .eq(Episode::getContentType, contentType)
               .orderByAsc(Episode::getEpisodeNumber);
        return list(wrapper);
    }

    @Override
    public int saveBatchDistinct(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (Episode ep : episodes) {
            LambdaQueryWrapper<Episode> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Episode::getContentId, ep.getContentId())
                   .eq(Episode::getContentType, ep.getContentType())
                   .eq(Episode::getEpisodeNumber, ep.getEpisodeNumber());
            if (!exists(wrapper)) {
                save(ep);
                saved++;
            }
        }
        return saved;
    }
}
