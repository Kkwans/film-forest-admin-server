package com.filmforest.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.Result;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * JWT 认证过滤器
 * 拦截 /api/** 请求，验证 Authorization 头
 * 白名单: /api/auth/login, /api/auth/register, /api/health
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 白名单放行（仅登录和注册）
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.startsWith("/api/health") || path.equals("/")) {
            chain.doFilter(request, response);
            return;
        }

        // OPTIONS 请求放行（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (JwtUtil.validateToken(token)) {
                // 将用户信息存入 request attribute
                request.setAttribute("userId", JwtUtil.getUserId(token));
                request.setAttribute("username", JwtUtil.getUsername(token));
                chain.doFilter(request, response);
                return;
            }
        }

        // 未认证
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(Result.fail("未登录或 Token 已过期")));
    }
}
