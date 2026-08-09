package com.filmforest.notification.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.notification.entity.AdminNotification;
import com.filmforest.notification.entity.AdminNotificationPreference;
import com.filmforest.notification.service.AdminNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationService service;

    public AdminNotificationController(AdminNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Result<IPage<AdminNotification>> list(HttpServletRequest request,
                                                @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(userId(request), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount(HttpServletRequest request) {
        return Result.ok(Map.of("count", service.unreadCount(userId(request))));
    }

    @PostMapping("/{id}/read")
    public Result<Boolean> markRead(HttpServletRequest request, @PathVariable long id) {
        return Result.ok(service.markRead(userId(request), id));
    }

    @PostMapping("/read-all")
    public Result<Map<String, Long>> markAllRead(HttpServletRequest request) {
        return Result.ok(Map.of("updated", service.markAllRead(userId(request))));
    }

    @GetMapping("/preferences")
    public Result<AdminNotificationPreference> preferences(HttpServletRequest request) {
        return Result.ok(service.getPreference(userId(request)));
    }

    @PutMapping("/preferences")
    public Result<AdminNotificationPreference> savePreferences(HttpServletRequest request,
                                                               @RequestBody AdminNotificationPreference preference) {
        return Result.ok(service.savePreference(userId(request), preference));
    }

    private static long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("未识别当前管理员");
        return userId;
    }
}
