package com.filmforest.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * JWT 认证过滤器
 * 拦截 /api/** 请求，验证 Authorization 头
 * 白名单: POST /api/auth/login, GET /api/health
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isPublicRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = Long.parseLong(claims.getSubject());
                User user = userMapper.selectById(userId);
                if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                        || Integer.valueOf(1).equals(user.getIsDeleted())) {
                    writeError(response, 401, "登录状态已失效");
                    return;
                }
                if (user.getRole() != UserRole.ADMIN) {
                    writeError(response, 403, "当前账号没有管理权限");
                    return;
                }
                request.setAttribute("userId", user.getId());
                request.setAttribute("username", user.getUsername());
                request.setAttribute("role", user.getRole());
                chain.doFilter(request, response);
                return;
            } catch (JwtException | IllegalArgumentException ignored) {
                // 统一在下方返回认证失败，避免泄露令牌解析细节。
            }
        }

        writeError(response, 401, "未登录或 Token 已过期");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api") && !path.startsWith("/api/");
    }

    boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        return (path.equals("/api/auth/login") && HttpMethod.POST.matches(request.getMethod()))
                || (path.equals("/api/health") && HttpMethod.GET.matches(request.getMethod()));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(Result.fail(status, message)));
    }
}
