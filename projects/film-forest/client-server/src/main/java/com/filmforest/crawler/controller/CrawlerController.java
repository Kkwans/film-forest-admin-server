package com.filmforest.crawler.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 爬虫管理 API
 */
@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    @GetMapping("/schedules")
    public Result<List<CrawlerSchedule>> listSchedules() {
        return Result.ok(crawlerService.listSchedules());
    }

    @GetMapping("/schedule/{id}")
    public Result<CrawlerSchedule> getSchedule(@PathVariable Long id) {
        CrawlerSchedule schedule = crawlerService.getSchedule(id);
        return schedule != null ? Result.ok(schedule) : Result.fail("配置不存在");
    }

    @PostMapping("/schedule")
    public Result<Boolean> saveSchedule(@RequestBody CrawlerSchedule schedule) {
        return Result.ok(crawlerService.saveSchedule(schedule));
    }

    @DeleteMapping("/schedule/{id}")
    public Result<Boolean> deleteSchedule(@PathVariable Long id) {
        return Result.ok(crawlerService.deleteSchedule(id));
    }

    @PostMapping("/start/{id}")
    public Result<Boolean> startCrawler(@PathVariable Long id) {
        return Result.ok(crawlerService.startCrawler(id));
    }

    @PostMapping("/stop/{id}")
    public Result<Boolean> stopCrawler(@PathVariable Long id) {
        return Result.ok(crawlerService.stopCrawler(id));
    }

    @PostMapping("/toggle/{id}")
    public Result<Boolean> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(crawlerService.toggleEnabled(id, enabled));
    }

    @GetMapping("/status")
    public Result<Object> getStatus() {
        return Result.ok(crawlerService.getStatus());
    }
}
