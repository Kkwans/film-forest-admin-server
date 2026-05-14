package com.filmforest.common.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.User;
import com.filmforest.content.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证 API - 登录/注册/Token 刷新
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserMapper userMapper;

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        log.info("登录请求: username={}", req.getUsername());

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())
                .eq(User::getIsDeleted, 0)
        );

        if (user == null) {
            log.warn("登录失败: 用户不存在, username={}", req.getUsername());
            return Result.fail("用户不存在");
        }

        // 验证密码（SHA-256 哈希）
        if (!hashPassword(req.getPassword()).equals(user.getPasswordHash())) {
            log.warn("登录失败: 密码错误, username={}", req.getUsername());
            return Result.fail("密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败: 账号已禁用, userId={}", user.getId());
            return Result.fail("账号已被禁用");
        }

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("登录成功: userId={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername()
        ));

        return Result.ok(data);
    }

    /** 注册（管理员） */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody LoginRequest req) {
        log.info("注册请求: username={}", req.getUsername());

        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())
        );
        if (count > 0) {
            log.warn("注册失败: 用户名已存在, username={}", req.getUsername());
            return Result.fail("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(hashPassword(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setStatus(1);
        userMapper.insert(user);

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("注册成功: userId={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername()
        ));

        return Result.ok(data);
    }

    /** 刷新 Token */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(jakarta.servlet.http.HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.warn("Token 刷新失败: 缺少 Authorization 头");
            return Result.fail("Token 无效");
        }
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            log.warn("Token 刷新失败: Token 已过期");
            return Result.fail("Token 已过期");
        }

        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String newToken = JwtUtil.generateToken(userId, username);
        log.info("Token 刷新成功: userId={}, username={}", userId, username);

        Map<String, Object> data = new HashMap<>();
        data.put("token", newToken);
        return Result.ok(data);
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(jakarta.servlet.http.HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        if (userId == null) return Result.fail("未登录");
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("avatarUrl", user.getAvatarUrl());
        return Result.ok(data);
    }

    /** SHA-256 哈希密码 */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }

    /** 登录/注册请求体 */
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 30, message = "用户名长度 3~30 位")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度至少 6 位")
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
