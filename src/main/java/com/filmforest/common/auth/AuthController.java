package com.filmforest.common.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.User;
import com.filmforest.content.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private UserMapper userMapper;

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return Result.fail("用户名和密码不能为空");
        }

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())
                .eq(User::getIsDeleted, 0)
        );

        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 验证密码（SHA-256 哈希）
        if (!hashPassword(req.getPassword()).equals(user.getPasswordHash())) {
            return Result.fail("密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.fail("账号已被禁用");
        }

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

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
    public Result<Map<String, Object>> register(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return Result.fail("用户名和密码不能为空");
        }
        if (req.getUsername().length() < 3 || req.getPassword().length() < 6) {
            return Result.fail("用户名至少3位，密码至少6位");
        }

        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())
        );
        if (count > 0) {
            return Result.fail("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(hashPassword(req.getPassword()));
        user.setNickname(req.getUsername());
        user.setStatus(1);
        userMapper.insert(user);

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

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
            return Result.fail("Token 无效");
        }
        String token = auth.substring(7);
        if (!JwtUtil.validateToken(token)) {
            return Result.fail("Token 已过期");
        }

        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String newToken = JwtUtil.generateToken(userId, username);

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
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
