package com.filmforest.resource.service;

import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

public interface ResourceService {

    // ===== 在线资源 =====
    List<ResourceOnline> listOnlineResources(String contentType, Long contentId);
    ResourceOnline saveOnlineResource(ResourceOnline resource);
    boolean deleteOnlineResource(Long id);
    List<ResourceOnline> listOnlineByContentType(String contentType);

    // ===== 磁力资源 =====
    List<ResourceMagnet> listMagnetResources(String contentType, Long contentId);
    ResourceMagnet saveMagnetResource(ResourceMagnet resource);
    boolean deleteMagnetResource(Long id);
    List<ResourceMagnet> listMagnetByContentType(String contentType);

    // ===== 统计 =====
    long countOnline();
    long countMagnet();
    long countTodayNew();
}