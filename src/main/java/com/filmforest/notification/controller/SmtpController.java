package com.filmforest.notification.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.notification.service.SmtpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/smtp")
public class SmtpController {
    private final SmtpService service;

    public SmtpController(SmtpService service) {
        this.service = service;
    }

    @GetMapping
    public Result<SmtpService.SmtpSettingView> get() {
        return Result.ok(service.view());
    }

    @PutMapping
    public Result<SmtpService.SmtpSettingView> save(@Valid @RequestBody SmtpService.SmtpSettingRequest request) {
        return Result.ok(service.save(request));
    }

    @PostMapping("/test-connection")
    public Result<SmtpService.SmtpTestResult> testConnection() {
        return Result.ok(service.testConnection());
    }

    @PostMapping("/test-mail")
    public Result<SmtpService.SmtpTestResult> testMail(@Valid @RequestBody SmtpService.TestMailRequest request) {
        return Result.ok(service.sendTest(request.recipient()));
    }
}
