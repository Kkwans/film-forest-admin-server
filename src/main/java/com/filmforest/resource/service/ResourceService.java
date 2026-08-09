package com.filmforest.resource.service;

import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.dto.ResourcePageQuery;
import com.baomidou.mybatisplus.spring.service.IService;
import java.util.List;

public interface ResourceService {

    // ===== 在线资源 =====
    List<ResourceOnline> listOnlineResources(String contentType, Long contentId);
    com.baomidou.mybatisplus.core.metadata.IPage<ResourceOnline> pageOnline(ResourcePageQuery query);
    ResourceOnline saveOnlineResource(ResourceOnline resource);
    boolean deleteOnlineResource(Long id);
    boolean setOnlineEnabled(Long id, boolean enabled);
    List<ResourceOnline> listOnlineByContentType(String contentType);

    // ===== 磁力资源 =====
    List<ResourceMagnet> listMagnetResources(String contentType, Long contentId);
    ResourceMagnet saveMagnetResource(ResourceMagnet resource);
    boolean deleteMagnetResource(Long id);
    List<ResourceMagnet> listMagnetByContentType(String contentType);
    com.baomidou.mybatisplus.core.metadata.IPage<ResourceMagnet> pageMagnet(ResourcePageQuery query);
    boolean setMagnetEnabled(Long id, boolean enabled);

    // ===== 网盘资源 =====
    List<ResourceCloud> listCloudResources(String contentType, Long contentId);
    ResourceCloud saveCloudResource(ResourceCloud resource);
    boolean deleteCloudResource(Long id);
    List<ResourceCloud> listCloudByContentType(String contentType);
    com.baomidou.mybatisplus.core.metadata.IPage<ResourceCloud> pageCloud(ResourcePageQuery query);
    boolean setCloudEnabled(Long id, boolean enabled);

    // ===== 资源来源 =====
    List<ResourceSource> listSources();
    ResourceSource saveSource(ResourceSource source);
    boolean deleteSource(Long id);
    boolean toggleSource(Long id, boolean enabled);

    // ===== 统计 =====
    long countOnline();
    long countMagnet();
    long countCloud();
    long countTodayNew();

    /** 按内容类型分组统计在线资源数量（单次查询替代 5 次全量加载） */
    java.util.Map<String, Long> countOnlineByContentType();
}
