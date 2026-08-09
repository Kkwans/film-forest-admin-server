package com.filmforest.system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class RegistrationInvitationAdminService {

    private static final int TOKEN_BYTES = 32;
    private static final int DEFAULT_EXPIRY_HOURS = 24;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RegistrationInvitationAdminService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new SecureRandom(), Clock.systemDefaultZone());
    }

    RegistrationInvitationAdminService(JdbcTemplate jdbcTemplate, SecureRandom secureRandom, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreatedInvitation create(Long actorUserId) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusHours(DEFAULT_EXPIRY_HOURS);
        jdbcTemplate.update("""
                INSERT INTO registration_invitation(token_hash, status, created_by, expires_at)
                VALUES (?, 'ACTIVE', ?, ?)
                """, hash(token), actorUserId, expiresAt);
        return new CreatedInvitation(token, expiresAt);
    }

    public List<InvitationSummary> list() {
        LocalDateTime now = LocalDateTime.now(clock);
        return jdbcTemplate.query("""
                SELECT i.id, i.status, i.created_by, creator.username AS created_by_username,
                       i.used_by, used_user.username AS used_by_username,
                       i.expires_at, i.used_at, i.revoked_at, i.created_at
                  FROM registration_invitation i
                  JOIN user creator ON creator.id = i.created_by
             LEFT JOIN user used_user ON used_user.id = i.used_by
              ORDER BY i.created_at DESC
                 LIMIT 100
                """, (rs, rowNum) -> {
            String storedStatus = rs.getString("status");
            LocalDateTime expiresAt = rs.getTimestamp("expires_at").toLocalDateTime();
            String displayStatus = "ACTIVE".equals(storedStatus) && !expiresAt.isAfter(now)
                    ? "EXPIRED" : storedStatus;
            return new InvitationSummary(
                    rs.getLong("id"), displayStatus,
                    rs.getLong("created_by"), rs.getString("created_by_username"),
                    nullableLong(rs, "used_by"), rs.getString("used_by_username"),
                    expiresAt,
                    nullableDateTime(rs, "used_at"),
                    nullableDateTime(rs, "revoked_at"),
                    rs.getTimestamp("created_at").toLocalDateTime());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean revoke(Long invitationId) {
        return jdbcTemplate.update("""
                UPDATE registration_invitation
                   SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
                """, invitationId) == 1;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime nullableDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record CreatedInvitation(String token, LocalDateTime expiresAt) {}

    public record InvitationSummary(
            Long id,
            String status,
            Long createdBy,
            String createdByUsername,
            Long usedBy,
            String usedByUsername,
            LocalDateTime expiresAt,
            LocalDateTime usedAt,
            LocalDateTime revokedAt,
            LocalDateTime createdAt) {}
}
