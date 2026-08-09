package com.filmforest.system.controller;

import com.filmforest.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Lightweight unauthenticated liveness endpoint for the local container probe. */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "ok",
                "service", "film-forest-admin",
                "timestamp", Instant.now().toString()
        ));
    }
}
