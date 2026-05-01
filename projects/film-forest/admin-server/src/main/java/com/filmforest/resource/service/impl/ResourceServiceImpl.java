package com.filmforest.resource.service.impl;

import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.service.ResourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceOnlineMapper, ResourceOnline>
        implements ResourceService {

    private final ResourceMagnetMapper magnetMapper;

    public ResourceServiceImpl(ResourceMagnetMapper magnetMapper) {
        this.magnetMapper = magnetMapper;
    }

    // ===== 在线资源 =====
    @Override
    public List<ResourceOnline> listOnlineResources(String contentType, Long contentId) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(contentType != null, ResourceOnline::getContentType, contentType)
                .eq(contentId != null, ResourceOnline::getContentId, contentId)
                .orderByAsc(ResourceOnline::getSort));
    }

    @Override
    public List<ResourceOnline> listOnlineByContentType(String contentType) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, contentType)
                .orderByDesc(ResourceOnline::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceOnline saveOnlineResource(ResourceOnline resource) {
        if (resource.getId() == null) {
            resource.setCreatedAt(LocalDateTime.now());
            save(resource);
        } else {
            updateById(resource);
        }
        return resource;
    }

    @Override
    public boolean deleteOnlineResource(Long id) {
        return removeById(id);
    }

    // ===== 磁力资源 =====
    @Override
    public List<ResourceMagnet> listMagnetResources(String contentType, Long contentId) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(contentType != null, ResourceMagnet::getContentType, contentType)
                .eq(contentId != null, ResourceMagnet::getContentId, contentId)
                .orderByAsc(ResourceMagnet::getSort));
    }

    @Override
    public List<ResourceMagnet> listMagnetByContentType(String contentType) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, contentType)
                .orderByDesc(ResourceMagnet::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceMagnet saveMagnetResource(ResourceMagnet resource) {
        if (resource.getId() == null) {
            resource.setCreatedAt(LocalDateTime.now());
            magnetMapper.insert(resource);
        } else {
            magnetMapper.updateById(resource);
        }
        return resource;
    }

    @Override
    public boolean deleteMagnetResource(Long id) {
        return magnetMapper.deleteById(id) > 0;
    }

    // ===== 统计 =====
    @Override
    public long countOnline() {
        return count();
    }

    @Override
    public long countMagnet() {
        return magnetMapper.selectCount(null);
    }

    @Override
    public long countTodayNew() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long online = count(new LambdaQueryWrapper<ResourceOnline>()
                .ge(ResourceOnline::getCreatedAt, start));
        long magnet = magnetMapper.selectCount(new LambdaQueryWrapper<ResourceMagnet>()
                .ge(ResourceMagnet::getCreatedAt, start));
        return online + magnet;
    }
}