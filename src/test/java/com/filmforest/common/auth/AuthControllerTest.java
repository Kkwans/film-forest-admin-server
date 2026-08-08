package com.filmforest.common.auth;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final PasswordService passwordService = new PasswordService();
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuthController controller = new AuthController(
            userMapper, jwtUtil, passwordService, loginAttemptService);

    @Test
    void rejectsValidUserCredentialsFromAdminLogin() throws Exception {
        User user = user(UserRole.USER);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<User>>any())).thenReturn(user);
        AuthController.LoginRequest request = loginRequest();

        Result<Map<String, Object>> result = controller.login(request, servletRequest());

        assertThat(result.getCode()).isEqualTo(403);
        verify(jwtUtil, never()).generateToken(any(), any());
        verify(userMapper, never()).updateById(any(User.class));
        verify(loginAttemptService).recordFailure("192.0.2.10", "admin");
    }

    @Test
    void issuesTokenOnlyToAdmin() throws Exception {
        User admin = user(UserRole.ADMIN);
        when(userMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<User>>any())).thenReturn(admin);
        when(jwtUtil.generateToken(1L, "admin")).thenReturn("token");

        Result<Map<String, Object>> result = controller.login(loginRequest(), servletRequest());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("token", "token");
        assertThat(admin.getPasswordAlgorithm()).isEqualTo(PasswordAlgorithm.BCRYPT);
        assertThat(admin.getPasswordHash()).startsWith("$2");
        verify(userMapper).updateById(admin);
        verify(loginAttemptService).recordSuccess("192.0.2.10", "admin");
    }

    @Test
    void blocksLoginBeforeDatabaseLookup() {
        when(loginAttemptService.isBlocked("192.0.2.10", "admin")).thenReturn(true);

        Result<Map<String, Object>> result = controller.login(loginRequest(), servletRequest());

        assertThat(result.getCode()).isEqualTo(429);
        verify(userMapper, never()).selectOne(org.mockito.ArgumentMatchers.<Wrapper<User>>any());
    }

    private User user(UserRole role) throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setPasswordHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("secret12".getBytes(StandardCharsets.UTF_8))));
        user.setPasswordAlgorithm(PasswordAlgorithm.LEGACY_SHA256);
        user.setMustChangePassword(false);
        user.setStatus(1);
        user.setIsDeleted(0);
        user.setRole(role);
        return user;
    }

    private AuthController.LoginRequest loginRequest() {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("secret12");
        return request;
    }

    private MockHttpServletRequest servletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
