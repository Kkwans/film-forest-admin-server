package com.filmforest.common.auth;

import com.filmforest.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 登录管理员的列表分页偏好 API。 */
@RestController
@RequestMapping("/api/auth/preferences/list-page-size")
public class AdminListPagePreferenceController {

    private final AdminListPagePreferenceService service;

    public AdminListPagePreferenceController(AdminListPagePreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Integer>> get(HttpServletRequest request) {
        return Result.ok(service.get(userId(request)));
    }

    @PutMapping
    public Result<Map<String, Integer>> update(@RequestBody Request body, HttpServletRequest request) {
        return Result.ok(service.update(userId(request), body.key(), body.size()));
    }

    private static long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("未识别当前管理员");
        return userId;
    }

    public record Request(String key, Integer size) {}
}
