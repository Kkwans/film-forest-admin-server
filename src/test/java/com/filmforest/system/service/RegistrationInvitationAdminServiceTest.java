package com.filmforest.system.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationInvitationAdminServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void createReturnsRawTokenOnceButPersistsOnlySha256Hash() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RegistrationInvitationAdminService service = new RegistrationInvitationAdminService(
                jdbcTemplate, new SecureRandom(new byte[]{1, 2, 3}), FIXED_CLOCK);

        RegistrationInvitationAdminService.CreatedInvitation invitation = service.create(7L);

        assertThat(invitation.token()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(invitation.expiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 9, 0));
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), arguments.capture());
        assertThat(arguments.getValue()[0].toString()).matches("^[0-9a-f]{64}$");
        assertThat(arguments.getValue()[0]).isNotEqualTo(invitation.token());
        assertThat(arguments.getValue()[1]).isEqualTo(7L);
    }

    @Test
    void revokeSucceedsOnlyWhenOneActiveInvitationWasUpdated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        RegistrationInvitationAdminService service = new RegistrationInvitationAdminService(
                jdbcTemplate, new SecureRandom(), FIXED_CLOCK);

        assertThat(service.revoke(9L)).isTrue();
        assertThat(service.revoke(9L)).isFalse();
    }
}
