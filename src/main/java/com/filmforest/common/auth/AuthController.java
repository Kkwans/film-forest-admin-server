package com.filmforest.common.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证 API - 登录/Token 刷新
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordService passwordService;

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordService = passwordService;
    }

    /** 登录 */
    @PostMapping("/login")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        log.info("登录请求: username={}", req.getUsername());

        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())
                .eq(User::getIsDeleted, 0)
        );

        if (user == null) {
            log.warn("登录失败: 用户不存在, username={}", req.getUsername());
            return Result.fail("用户名或密码错误");
        }

        PasswordService.Verification verification = passwordService.verify(
                req.getPassword(), user.getPasswordHash(), user.getPasswordAlgorithm());
        if (!verification.matches()) {
            log.warn("登录失败: 密码错误, username={}", req.getUsername());
            return Result.fail("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            log.warn("登录失败: 账号已禁用, userId={}", user.getId());
            return Result.fail("账号已被禁用");
        }

        if (user.getRole() != UserRole.ADMIN) {
            log.warn("管理端登录拒绝: userId={}, role={}", user.getId(), user.getRole());
            return Result.fail(403, "当前账号没有管理权限");
        }

        if (verification.needsUpgrade()) {
            user.setPasswordHash(passwordService.encode(req.getPassword()));
            user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
            userMapper.updateById(user);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("登录成功: userId={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername(),
            "role", user.getRole().name(),
            "mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword())
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
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token 刷新失败: Token 已过期");
            return Result.fail("Token 已过期");
        }

        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);
        String newToken = jwtUtil.generateToken(userId, username);
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
        data.put("role", user.getRole());
        data.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        return Result.ok(data);
    }

    /** 登录请求体 */
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
