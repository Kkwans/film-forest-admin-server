package com.filmforest.resource.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private ResourceService resourceService;

    // ===== 在线资源 =====
    @GetMapping("/online")
    public Result<List<ResourceOnline>> listOnline(
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Long contentId) {
        return Result.ok(resourceService.listOnlineResources(contentType, contentId));
    }

    @PostMapping("/online")
    public Result<ResourceOnline> saveOnline(@Valid @RequestBody ResourceOnline resource) {
        ResourceOnline saved = resourceService.saveOnlineResource(resource);
        log.info("保存在线资源: id={}, contentType={}, contentId={}", saved.getId(), resource.getContentType(), resource.getContentId());
        return Result.ok(saved);
    }

    @DeleteMapping("/online/{id}")
    public Result<Boolean> deleteOnline(@PathVariable Long id) {
        log.info("删除在线资源: id={}", id);
        return Result.ok(resourceService.deleteOnlineResource(id));
    }

    @GetMapping("/online/stats")
    public Result<Map<String, Object>> statsOnline() {
        Map<String, Object> stats = new HashMap<>();
        // 使用单次 GROUP BY 查询替代 5 次全量加载（每类型最多 200 条记录 → 单次 COUNT 聚合）
        stats.put("total", resourceService.countOnline());
        stats.put("byType", resourceService.countOnlineByContentType());
        return Result.ok(stats);
    }

    // ===== 磁力资源 =====
    @GetMapping("/magnet")
    public Result<List<ResourceMagnet>> listMagnet(
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Long contentId) {
        return Result.ok(resourceService.listMagnetResources(contentType, contentId));
    }

    @PostMapping("/magnet")
    public Result<ResourceMagnet> saveMagnet(@Valid @RequestBody ResourceMagnet resource) {
        ResourceMagnet saved = resourceService.saveMagnetResource(resource);
        log.info("保存磁力资源: id={}, contentType={}, contentId={}", saved.getId(), resource.getContentType(), resource.getContentId());
        return Result.ok(saved);
    }

    @DeleteMapping("/magnet/{id}")
    public Result<Boolean> deleteMagnet(@PathVariable Long id) {
        log.info("删除磁力资源: id={}", id);
        return Result.ok(resourceService.deleteMagnetResource(id));
    }

    // ===== 网盘资源 =====
    @GetMapping("/cloud")
    public Result<List<ResourceCloud>> listCloud(
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Long contentId) {
        return Result.ok(resourceService.listCloudResources(contentType, contentId));
    }

    @PostMapping("/cloud")
    public Result<ResourceCloud> saveCloud(@Valid @RequestBody ResourceCloud resource) {
        ResourceCloud saved = resourceService.saveCloudResource(resource);
        log.info("保存网盘资源: id={}, contentType={}, contentId={}", saved.getId(), resource.getContentType(), resource.getContentId());
        return Result.ok(saved);
    }

    @DeleteMapping("/cloud/{id}")
    public Result<Boolean> deleteCloud(@PathVariable Long id) {
        log.info("删除网盘资源: id={}", id);
        return Result.ok(resourceService.deleteCloudResource(id));
    }

    // ===== 资源来源 =====
    @GetMapping("/sources")
    public Result<List<ResourceSource>> listSources() {
        return Result.ok(resourceService.listSources());
    }

    @PostMapping("/sources")
    public Result<ResourceSource> saveSource(@Valid @RequestBody ResourceSource source) {
        ResourceSource saved = resourceService.saveSource(source);
        log.info("保存资源来源: id={}, name={}", saved.getId(), source.getName());
        return Result.ok(saved);
    }

    @DeleteMapping("/sources/{id}")
    public Result<Boolean> deleteSource(@PathVariable Long id) {
        log.info("删除资源来源: id={}", id);
        return Result.ok(resourceService.deleteSource(id));
    }

    @PostMapping("/sources/{id}/toggle")
    public Result<Boolean> toggleSource(@PathVariable Long id, @RequestParam boolean enabled) {
        log.info("切换资源来源状态: id={}, enabled={}", id, enabled);
        return Result.ok(resourceService.toggleSource(id, enabled));
    }

    // ===== 全局统计 =====
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("online", resourceService.countOnline());
        stats.put("magnet", resourceService.countMagnet());
        stats.put("cloud", resourceService.countCloud());
        stats.put("todayNew", resourceService.countTodayNew());
        return Result.ok(stats);
    }
}