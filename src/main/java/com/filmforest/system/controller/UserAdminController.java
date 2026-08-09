package com.filmforest.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.auth.PasswordService;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.system.service.DefaultUserListProvisioner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户管理 API（管理员操作）
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final DefaultUserListProvisioner defaultListProvisioner;

    public UserAdminController(UserMapper userMapper, PasswordService passwordService,
                               DefaultUserListProvisioner defaultListProvisioner) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.defaultListProvisioner = defaultListProvisioner;
    }

    /** 分页查询用户列表 */
    @GetMapping
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(User::getUsername, keyword)
                .or().like(User::getNickname, keyword)
                .or().like(User::getEmail, keyword)
                .or().like(User::getPhone, keyword)
            );
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        // 清除密码哈希
        result.getRecords().forEach(u -> u.setPasswordHash(null));
        return Result.ok(result);
    }

    /** 获取单个用户详情 */
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    /** 创建用户 */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<User> create(@Valid @RequestBody CreateUserRequest req) {
        // 检查用户名唯一
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())
        );
        if (count > 0) return Result.fail("用户名已存在");

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordService.encode(req.getPassword()));
        user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
        user.setMustChangePassword(true);
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        user.setRole(UserRole.USER);
        userMapper.insert(user);
        defaultListProvisioner.createFor(user.getId());

        user.setPasswordHash(null);
        log.info("创建用户: id={}, username={}", user.getId(), user.getUsername());
        return Result.ok(user);
    }

    /** 更新用户信息（不含密码） */
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");

        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getStatus() != null) user.setStatus(req.getStatus());

        userMapper.updateById(user);
        user.setPasswordHash(null);
        log.info("更新用户: id={}, username={}", user.getId(), user.getUsername());
        return Result.ok(user);
    }

    /** 删除用户（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        userMapper.deleteById(id);
        log.info("删除用户: id={}, username={}", id, user.getUsername());
        return Result.ok(true);
    }

    /** 切换用户状态（启用/禁用） */
    @PostMapping("/{id}/toggle-status")
    public Result<User> toggleStatus(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);
        user.setPasswordHash(null);
        log.info("切换用户状态: id={}, username={}, status={}", id, user.getUsername(), user.getStatus());
        return Result.ok(user);
    }

    /** 重置用户密码 */
    @PostMapping("/{id}/reset-password")
    public Result<Boolean> resetPassword(@PathVariable Long id,
                                         @Valid @RequestBody ResetPasswordRequest req,
                                         HttpServletRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.fail("用户不存在");
        user.setPasswordHash(passwordService.encode(req.getNewPassword()));
        user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
        Long actorUserId = (Long) request.getAttribute("userId");
        user.setMustChangePassword(!Objects.equals(actorUserId, id));
        userMapper.updateById(user);
        log.info("重置用户密码: id={}, username={}", id, user.getUsername());
        return Result.ok(true);
    }

    // ===== Request DTOs =====

    public static class CreateUserRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 30, message = "用户名长度 3~30 位")
        private String username;
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度至少 6 位")
        private String password;
        private String nickname;
        private String email;
        private String phone;
        private Integer status;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class UpdateUserRequest {
        private String nickname;
        private String email;
        private String phone;
        private String avatarUrl;
        private Integer status;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度至少 6 位")
        private String newPassword;

        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
