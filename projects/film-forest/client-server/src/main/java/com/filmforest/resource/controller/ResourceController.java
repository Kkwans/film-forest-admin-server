package com.filmforest.resource.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.service.ResourceCloudService;
import com.filmforest.resource.service.ResourceMagnetService;
import com.filmforest.resource.service.ResourceOnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源管理 Controller（在线播放/磁力/网盘）
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    @Autowired
    private ResourceOnlineService resourceOnlineService;

    @Autowired
    private ResourceMagnetService resourceMagnetService;

    @Autowired
    private ResourceCloudService resourceCloudService;

    // ==================== 在线播放资源 ====================

    /**
     * 获取在线播放资源列表
     * @param contentType movie/drama/variety/anime/short
     * @param contentId 内容ID
     * @param episodeId 集数ID（可选）
     */
    @GetMapping("/online")
    public Result<List<ResourceOnline>> listOnline(
            @RequestParam String contentType,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId) {
        List<ResourceOnline> list;
        if (episodeId != null) {
            list = resourceOnlineService.listByContentAndEpisode(contentType, contentId, episodeId);
        } else {
            list = resourceOnlineService.listByContent(contentType, contentId);
        }
        return Result.ok(list);
    }

    // ==================== 磁力链接资源 ====================

    /**
     * 获取磁力链接资源列表
     */
    @GetMapping("/magnet")
    public Result<List<ResourceMagnet>> listMagnet(
            @RequestParam String contentType,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId) {
        List<ResourceMagnet> list;
        if (episodeId != null) {
            list = resourceMagnetService.listByContentAndEpisode(contentType, contentId, episodeId);
        } else {
            list = resourceMagnetService.listByContent(contentType, contentId);
        }
        return Result.ok(list);
    }

    // ==================== 网盘资源 ====================

    /**
     * 获取网盘资源列表
     */
    @GetMapping("/cloud")
    public Result<List<ResourceCloud>> listCloud(
            @RequestParam String contentType,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId) {
        List<ResourceCloud> list;
        if (episodeId != null) {
            list = resourceCloudService.listByContentAndEpisode(contentType, contentId, episodeId);
        } else {
            list = resourceCloudService.listByContent(contentType, contentId);
        }
        return Result.ok(list);
    }
}
