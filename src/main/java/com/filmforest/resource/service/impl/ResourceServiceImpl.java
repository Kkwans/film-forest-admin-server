package com.filmforest.resource.service.impl;

import com.filmforest.common.type.ContentType;
import com.filmforest.resource.dto.ResourcePageQuery;
import com.filmforest.resource.entity.ResourceAdminStatus;
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
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceOnlineMapper, ResourceOnline>
        implements ResourceService {

    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "contentId", "title", "sort");

    private final ResourceMagnetMapper magnetMapper;
    private final ResourceCloudMapper cloudMapper;
    private final ResourceSourceMapper sourceMapper;

    public ResourceServiceImpl(ResourceOnlineMapper onlineMapper,
                              ResourceMagnetMapper magnetMapper, ResourceCloudMapper cloudMapper,
                              ResourceSourceMapper sourceMapper) {
        this.baseMapper = onlineMapper;
        this.magnetMapper = magnetMapper;
        this.cloudMapper = cloudMapper;
        this.sourceMapper = sourceMapper;
    }

    // ===== 在线资源 =====
    @Override
    public List<ResourceOnline> listOnlineResources(String contentType, Long contentId) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(StringUtils.isNotBlank(contentType), ResourceOnline::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceOnline::getContentId, contentId)
                .eq(ResourceOnline::getEnabled, 1)
                .isNull(ResourceOnline::getRemovedAt)
                .orderByAsc(ResourceOnline::getSort));
    }

    @Override
    public IPage<ResourceOnline> pageOnline(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceOnline> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        LambdaQueryWrapper<ResourceOnline> wrapper = new LambdaQueryWrapper<ResourceOnline>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceOnline::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceOnline::getContentId, query.getContentId())
                .eq(source != null, ResourceOnline::getSourceCode, source)
                .and(keyword != null, nested -> nested
                        .like(ResourceOnline::getEpisodeTitle, keyword)
                        .or().like(ResourceOnline::getSourceName, keyword)
                        .or().like(ResourceOnline::getSourceUrl, keyword));
        applyOnlineStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyOnlineSort(wrapper, query);
        return page(page, wrapper);
    }

    @Override
    public List<ResourceOnline> listOnlineByContentType(String contentType) {
        return list(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, normalizeContentType(contentType))
                .eq(ResourceOnline::getEnabled, 1)
                .isNull(ResourceOnline::getRemovedAt)
                .orderByDesc(ResourceOnline::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceOnline saveOnlineResource(ResourceOnline resource) {
        if (resource.getId() == null) {
            if (resource.getEnabled() == null) resource.setEnabled(1);
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

    @Override
    public boolean setOnlineEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceOnline> update = new UpdateWrapper<ResourceOnline>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return update(update);
    }

    // ===== 磁力资源 =====
    @Override
    public List<ResourceMagnet> listMagnetResources(String contentType, Long contentId) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(StringUtils.isNotBlank(contentType), ResourceMagnet::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceMagnet::getContentId, contentId)
                .eq(ResourceMagnet::getEnabled, 1)
                .isNull(ResourceMagnet::getRemovedAt)
                .orderByAsc(ResourceMagnet::getSort));
    }

    @Override
    public List<ResourceMagnet> listMagnetByContentType(String contentType) {
        return magnetMapper.selectList(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, normalizeContentType(contentType))
                .eq(ResourceMagnet::getEnabled, 1)
                .isNull(ResourceMagnet::getRemovedAt)
                .orderByDesc(ResourceMagnet::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceMagnet saveMagnetResource(ResourceMagnet resource) {
        if (resource.getId() == null) {
            if (resource.getEnabled() == null) resource.setEnabled(1);
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

    @Override
    public IPage<ResourceMagnet> pageMagnet(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceMagnet> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        String resolution = trimToNull(query.getResolution());
        LambdaQueryWrapper<ResourceMagnet> wrapper = new LambdaQueryWrapper<ResourceMagnet>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceMagnet::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceMagnet::getContentId, query.getContentId())
                .eq(source != null, ResourceMagnet::getSourceCode, source)
                .eq(resolution != null, ResourceMagnet::getResolution, resolution)
                .and(keyword != null, nested -> nested.like(ResourceMagnet::getTitle, keyword)
                        .or().like(ResourceMagnet::getMagnetUrl, keyword)
                        .or().like(ResourceMagnet::getRawText, keyword));
        applyMagnetStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyMagnetSort(wrapper, query);
        return magnetMapper.selectPage(page, wrapper);
    }

    @Override
    public boolean setMagnetEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceMagnet> update = new UpdateWrapper<ResourceMagnet>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return magnetMapper.update(null, update) > 0;
    }

    // ===== 网盘资源 =====
    @Override
    public List<ResourceCloud> listCloudResources(String contentType, Long contentId) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(StringUtils.isNotBlank(contentType), ResourceCloud::getContentType, normalizeContentType(contentType))
                .eq(contentId != null, ResourceCloud::getContentId, contentId)
                .eq(ResourceCloud::getEnabled, 1)
                .isNull(ResourceCloud::getRemovedAt)
                .orderByAsc(ResourceCloud::getSort));
    }

    @Override
    public List<ResourceCloud> listCloudByContentType(String contentType) {
        return cloudMapper.selectList(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, normalizeContentType(contentType))
                .eq(ResourceCloud::getEnabled, 1)
                .isNull(ResourceCloud::getRemovedAt)
                .orderByDesc(ResourceCloud::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public ResourceCloud saveCloudResource(ResourceCloud resource) {
        if (resource.getId() == null) {
            if (resource.getEnabled() == null) resource.setEnabled(1);
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

    @Override
    public IPage<ResourceCloud> pageCloud(ResourcePageQuery query) {
        validatePageQuery(query);
        Page<ResourceCloud> page = new Page<>(query.getPage(), query.getSize());
        String keyword = trimToNull(query.getKeyword());
        String source = trimToNull(query.getSource());
        String diskType = trimToNull(query.getDiskType());
        LambdaQueryWrapper<ResourceCloud> wrapper = new LambdaQueryWrapper<ResourceCloud>()
                .eq(StringUtils.isNotBlank(query.getContentType()), ResourceCloud::getContentType,
                        normalizeContentType(query.getContentType()))
                .eq(query.getContentId() != null, ResourceCloud::getContentId, query.getContentId())
                .eq(source != null, ResourceCloud::getSourceCode, source)
                .eq(diskType != null, ResourceCloud::getDiskType, diskType)
                .and(keyword != null, nested -> nested.like(ResourceCloud::getTitle, keyword)
                        .or().like(ResourceCloud::getUrl, keyword)
                        .or().like(ResourceCloud::getRawText, keyword));
        applyCloudStatus(wrapper, ResourceAdminStatus.from(query.getStatus()));
        applyCloudSort(wrapper, query);
        return cloudMapper.selectPage(page, wrapper);
    }

    @Override
    public boolean setCloudEnabled(Long id, boolean enabled) {
        UpdateWrapper<ResourceCloud> update = new UpdateWrapper<ResourceCloud>()
                .eq("id", id)
                .set("enabled", enabled ? 1 : 0);
        if (enabled) update.set("removed_at", null);
        return cloudMapper.update(null, update) > 0;
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
        result.put("short_drama", 0L);
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
            result.put("short_drama", count(new LambdaQueryWrapper<ResourceOnline>().eq(ResourceOnline::getContentType, "short_drama")));
        }
        return result;
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) return null;
        String canonical = "short".equals(value.trim()) ? "short_drama" : value.trim();
        return ContentType.fromValue(canonical)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + value))
                .value();
    }

    private static void validatePageQuery(ResourcePageQuery query) {
        if (query == null) throw new IllegalArgumentException("资源分页参数不能为空");
        if (query.getPage() < 1) throw new IllegalArgumentException("页码必须大于 0");
        if (query.getSize() < 1 || query.getSize() > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static boolean ascending(ResourcePageQuery query) {
        if (!"asc".equalsIgnoreCase(query.getOrder()) && !"desc".equalsIgnoreCase(query.getOrder())) {
            throw new IllegalArgumentException("排序方向只允许 asc 或 desc");
        }
        return "asc".equalsIgnoreCase(query.getOrder());
    }

    private static String sortField(ResourcePageQuery query) {
        String field = StringUtils.defaultIfBlank(query.getSort(), "createdAt");
        if (!SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("不支持的资源排序字段: " + field);
        }
        return field;
    }

    private static void applyOnlineStatus(LambdaQueryWrapper<ResourceOnline> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceOnline::getEnabled, 1).isNull(ResourceOnline::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceOnline::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceOnline::getRemovedAt);
    }

    private static void applyMagnetStatus(LambdaQueryWrapper<ResourceMagnet> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceMagnet::getEnabled, 1).isNull(ResourceMagnet::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceMagnet::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceMagnet::getRemovedAt);
    }

    private static void applyCloudStatus(LambdaQueryWrapper<ResourceCloud> wrapper, ResourceAdminStatus status) {
        if (status == ResourceAdminStatus.ACTIVE) wrapper.eq(ResourceCloud::getEnabled, 1).isNull(ResourceCloud::getRemovedAt);
        if (status == ResourceAdminStatus.DISABLED) wrapper.eq(ResourceCloud::getEnabled, 0);
        if (status == ResourceAdminStatus.REMOVED) wrapper.isNotNull(ResourceCloud::getRemovedAt);
    }

    private static void applyOnlineSort(LambdaQueryWrapper<ResourceOnline> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceOnline::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceOnline::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceOnline::getEpisodeTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceOnline::getSort);
            default -> wrapper.orderBy(true, asc, ResourceOnline::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceOnline::getId);
    }

    private static void applyMagnetSort(LambdaQueryWrapper<ResourceMagnet> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceMagnet::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceMagnet::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceMagnet::getTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceMagnet::getSort);
            default -> wrapper.orderBy(true, asc, ResourceMagnet::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceMagnet::getId);
    }

    private static void applyCloudSort(LambdaQueryWrapper<ResourceCloud> wrapper, ResourcePageQuery query) {
        boolean asc = ascending(query);
        switch (sortField(query)) {
            case "updatedAt" -> wrapper.orderBy(true, asc, ResourceCloud::getUpdatedAt);
            case "contentId" -> wrapper.orderBy(true, asc, ResourceCloud::getContentId);
            case "title" -> wrapper.orderBy(true, asc, ResourceCloud::getTitle);
            case "sort" -> wrapper.orderBy(true, asc, ResourceCloud::getSort);
            default -> wrapper.orderBy(true, asc, ResourceCloud::getCreatedAt);
        }
        wrapper.orderByDesc(ResourceCloud::getId);
    }
}
