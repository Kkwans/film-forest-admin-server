package com.filmforest.settings.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.settings.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置 API
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    @Autowired
    private SystemSettingService settingService;

    /** 获取所有设置 */
    @GetMapping
    public Result<Map<String, String>> getSettings() {
        return Result.ok(settingService.getAllSettings());
    }

    /** 批量保存设置 */
    @PutMapping
    public Result<Boolean> saveSettings(@RequestBody Map<String, String> settings) {
        settingService.saveSettings(settings);
        log.info("保存系统设置: 共 {} 项", settings.size());
        return Result.ok(true);
    }

    /** 获取单个设置 */
    @GetMapping("/{key}")
    public Result<String> getSetting(@PathVariable String key,
                                     @RequestParam(required = false) String defaultValue) {
        String value = settingService.getValue(key, defaultValue);
        return value != null ? Result.ok(value) : Result.fail("设置不存在");
    }
}
