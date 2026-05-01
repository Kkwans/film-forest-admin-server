package com.filmforest.resource.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceMagnet;
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
        stats.put("total", resourceService.countOnline());
        stats.put("byType", Map.of(
                "movie", resourceService.listOnlineByContentType("movie").size(),
                "drama", resourceService.listOnlineByContentType("drama").size(),
                "variety", resourceService.listOnlineByContentType("variety").size(),
                "anime", resourceService.listOnlineByContentType("anime").size(),
                "short", resourceService.listOnlineByContentType("short").size()
        ));
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

    // ===== 全局统计 =====
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("online", resourceService.countOnline());
        stats.put("magnet", resourceService.countMagnet());
        stats.put("todayNew", resourceService.countTodayNew());
        return Result.ok(stats);
    }
}