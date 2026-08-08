package com.filmforest.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.Result;
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

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
            if (jwtUtil.validateToken(token)) {
                // 将用户信息存入 request attribute
                request.setAttribute("userId", jwtUtil.getUserId(token));
                request.setAttribute("username", jwtUtil.getUsername(token));
                chain.doFilter(request, response);
                return;
            }
        }

        // 未认证
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(Result.fail(401, "未登录或 Token 已过期")));
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
}
