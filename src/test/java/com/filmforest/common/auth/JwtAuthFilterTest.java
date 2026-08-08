package com.filmforest.common.auth;

import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userMapper);

    @Test
    void allowsOnlyExplicitPublicEndpoints() {
        assertThat(isPublic("POST", "/api/auth/login")).isTrue();
        assertThat(isPublic("GET", "/api/health")).isTrue();
        assertThat(isPublic("OPTIONS", "/api/settings")).isTrue();
    }

    @Test
    void protectsRegistrationAndAllOtherAdminEndpoints() {
        assertThat(isPublic("GET", "/api/auth/login")).isFalse();
        assertThat(isPublic("POST", "/api/auth/register")).isFalse();
        assertThat(isPublic("GET", "/api/settings")).isFalse();
        assertThat(isPublic("POST", "/api/crawler/start")).isFalse();
    }

    @Test
    void allowsOnlyActiveAdminFromCurrentDatabaseState() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        User admin = user(7L, UserRole.ADMIN, 1, 0);
        when(userMapper.selectById(7L)).thenReturn(admin);
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute("userId")).isEqualTo(7L);
        assertThat(request.getAttribute("role")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsCurrentUserRoleEvenWithValidAdminToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(userMapper.selectById(7L)).thenReturn(user(7L, UserRole.USER, 1, 0));
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void revokesDisabledOrDeletedAccountImmediately() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(userMapper.selectById(7L)).thenReturn(user(7L, UserRole.ADMIN, 0, 0));
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    private boolean isPublic(String method, String path) {
        return filter.isPublicRequest(new MockHttpServletRequest(method, path));
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/settings");
        request.addHeader("Authorization", "Bearer valid-token");
        return request;
    }

    private User user(Long id, UserRole role, int status, int deleted) {
        User user = new User();
        user.setId(id);
        user.setUsername("admin");
        user.setRole(role);
        user.setStatus(status);
        user.setIsDeleted(deleted);
        return user;
    }
}
