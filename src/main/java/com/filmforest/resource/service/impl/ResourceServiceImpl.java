package com.filmforest.resource.service.impl;

import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceSourceMapper;
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
    private final ResourceCloudMapper cloudMapper;
    private final ResourceSourceMapper sourceMapper;

    public ResourceServiceImpl(ResourceMagnetMapper magnetMapper, ResourceCloudMapper cloudMapper,
                              ResourceSourceMapper sourceMapper) {
        this.magnetMapper = magnetMapper;
        this.cloudMapper = cloudMapper;
        this.sourceMapper = sourceMapper;
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

    // ===== 网盘资源 =====
    @Override
    public List<ResourceCloud> listCloudResources(String contentType, Long contentId) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(contentType != null, ResourceCloud::getContentType, contentType)
                .eq(contentId != null, ResourceCloud::getContentId, contentId)
                .orderByAsc(ResourceCloud::getSort));
    }

    @Override
    public List<ResourceCloud> listCloudByContentType(String contentType) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, contentType)
                .orderByDesc(ResourceCloud::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceCloud saveCloudResource(ResourceCloud resource) {
        if (resource.getId() == null) {
            resource.setCreatedAt(LocalDateTime.now());
            cloudMapper.insert(resource);
        } else {
            cloudMapper.updateById(resource);
        }
        return resource;
    }

    @Override
    public boolean deleteCloudResource(Long id) {
        return cloudMapper.deleteById(id) > 0;
    }

    // ===== 资源来源 =====
    @Override
    public List<ResourceSource> listSources() {
        return sourceMapper.selectList(new LambdaQueryWrapper<ResourceSource>()
                .orderByAsc(ResourceSource::getSort));
    }

    @Override
    public ResourceSource saveSource(ResourceSource source) {
        if (source.getId() == null) {
            source.setCreatedAt(LocalDateTime.now());
            sourceMapper.insert(source);
        } else {
            sourceMapper.updateById(source);
        }
        return source;
    }

    @Override
    public boolean deleteSource(Long id) {
        return sourceMapper.deleteById(id) > 0;
    }

    @Override
    public boolean toggleSource(Long id, boolean enabled) {
        ResourceSource source = sourceMapper.selectById(id);
        if (source == null) return false;
        source.setEnabled(enabled ? 1 : 0);
        return sourceMapper.updateById(source) > 0;
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
    public long countCloud() {
        return cloudMapper.selectCount(null);
    }

    @Override
    public long countTodayNew() {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        // 3 次 COUNT 查询（已是 count 语义，无 N+1 问题）
        long online = count(new LambdaQueryWrapper<ResourceOnline>()
                .ge(ResourceOnline::getCreatedAt, start));
        long magnet = magnetMapper.selectCount(new LambdaQueryWrapper<ResourceMagnet>()
                .ge(ResourceMagnet::getCreatedAt, start));
        long cloud = cloudMapper.selectCount(new LambdaQueryWrapper<ResourceCloud>()
                .ge(ResourceCloud::getCreatedAt, start));
        return online + magnet + cloud;
    }

    @Override
    public java.util.Map<String, Long> countOnlineByContentType() {
        // 使用 GROUP BY 单次查询获取各类型数量（替代 5 次 list().size()，每类型最多 200 条 → 单次 COUNT）
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        result.put("movie", 0L);
        result.put("drama", 0L);
        result.put("variety", 0L);
        result.put("anime", 0L);
        result.put("short", 0L);
        try {
            // 使用 QueryWrapper（非 Lambda）支持原始 SQL 列名
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResourceOnline> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.select("content_type", "COUNT(*) AS cnt").groupBy("content_type");
            java.util.List<java.util.Map<String, Object>> rows = getBaseMapper().selectMaps(wrapper);
            for (java.util.Map<String, Object> row : rows) {
                String type = (String) row.get("content_type");
                Long cnt = ((Number) row.get("cnt")).longValue();
                result.put(type, cnt);
            }
        } catch (Exception e) {
            // fallback: 逐个 count（不含完整记录加载）
            result.put("movie", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "movie")));
            result.put("drama", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "drama")));
            result.put("variety", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "variety")));
            result.put("anime", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "anime")));
            result.put("short", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "short")));
        }
        return result;
    }
}