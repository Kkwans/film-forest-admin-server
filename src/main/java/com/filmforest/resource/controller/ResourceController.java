package com.filmforest.resource.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/resources")
public class ResourceController {

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
    public Result<ResourceOnline> saveOnline(@RequestBody ResourceOnline resource) {
        return Result.ok(resourceService.saveOnlineResource(resource));
    }

    @DeleteMapping("/online/{id}")
    public Result<Boolean> deleteOnline(@PathVariable Long id) {
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
    public Result<ResourceMagnet> saveMagnet(@RequestBody ResourceMagnet resource) {
        return Result.ok(resourceService.saveMagnetResource(resource));
    }

    @DeleteMapping("/magnet/{id}")
    public Result<Boolean> deleteMagnet(@PathVariable Long id) {
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
    public Result<ResourceCloud> saveCloud(@RequestBody ResourceCloud resource) {
        return Result.ok(resourceService.saveCloudResource(resource));
    }

    @DeleteMapping("/cloud/{id}")
    public Result<Boolean> deleteCloud(@PathVariable Long id) {
        return Result.ok(resourceService.deleteCloudResource(id));
    }

    // ===== 资源来源 =====
    @GetMapping("/sources")
    public Result<List<ResourceSource>> listSources() {
        return Result.ok(resourceService.listSources());
    }

    @PostMapping("/sources")
    public Result<ResourceSource> saveSource(@RequestBody ResourceSource source) {
        return Result.ok(resourceService.saveSource(source));
    }

    @DeleteMapping("/sources/{id}")
    public Result<Boolean> deleteSource(@PathVariable Long id) {
        return Result.ok(resourceService.deleteSource(id));
    }

    @PostMapping("/sources/{id}/toggle")
    public Result<Boolean> toggleSource(@PathVariable Long id, @RequestParam boolean enabled) {
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