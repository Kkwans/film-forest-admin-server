package com.test.mini.controller;

import com.test.mini.dto.*;
import com.test.mini.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVO>> register(@Valid @RequestBody RegisterRequest req) {
        try {
            UserVO user = userService.register(req);
            return ResponseEntity.ok(ApiResponse.success("注册成功", user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserVO>> login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req.getUsername(), req.getPassword())
                .map(user -> ResponseEntity.ok(ApiResponse.success("登录成功", user)))
                .orElse(ResponseEntity.badRequest().body(ApiResponse.error(401, "用户名或密码错误")));
    }
}
