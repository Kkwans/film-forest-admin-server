package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.service.DramaService;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

@Service
public class DramaServiceImpl extends ServiceImpl<DramaMapper, Drama> implements DramaService {

    @Override
    public IPage<Drama> pageList(int pageNum, int pageSize, Integer year, String region, String genre) {
        LambdaQueryWrapper<Drama> wrapper = new LambdaQueryWrapper<>();
        // 注意：deleted 条件由 MyBatis-Plus 全局 logic-delete 自动处理，不要重复添加
        wrapper.eq(year != null, Drama::getYear, year);
        wrapper.like(StringUtils.isNotBlank(region), Drama::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Drama::getGenre, genre);
        wrapper.orderByDesc(Drama::getCreatedAt);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Drama getDetail(Long id) {
        // 注意：deleted 条件由 MyBatis-Plus 全局 logic-delete 自动处理
        return getById(id);
    }
}