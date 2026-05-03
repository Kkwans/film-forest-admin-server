package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "ok");
        info.put("service", "film-forest-backend");
        info.put("timestamp", LocalDateTime.now().toString());
        return Result.ok(info);
    }
}
