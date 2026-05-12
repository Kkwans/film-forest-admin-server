package com.filmforest.common.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具类
 */
public class JwtUtil {

    // 生产环境应从配置读取，这里用固定密钥
    private static final String SECRET = "film-forest-admin-jwt-secret-key-2026-very-long-and-secure";
    private static final long EXPIRE_MS = 24 * 60 * 60 * 1000; // 24小时

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    /** 生成 JWT Token */
    public static String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_MS))
                .signWith(getKey())
                .compact();
    }

    /** 解析 JWT Token，返回 Claims */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Token 中获取用户名 */
    public static String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /** 从 Token 中获取用户 ID */
    public static Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /** 验证 Token 是否有效 */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
