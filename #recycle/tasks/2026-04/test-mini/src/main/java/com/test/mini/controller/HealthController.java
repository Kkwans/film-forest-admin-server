package com.test.mini.controller;

import com.test.mini.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("test-mini is running!");
    }
}
