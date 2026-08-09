package com.filmforest.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultUserListProvisionerTest {

    @Test
    void createsAllThreeDefaultListsForAdminProvisionedAccount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DefaultUserListProvisioner provisioner = new DefaultUserListProvisioner(jdbcTemplate);

        provisioner.createFor(17L);

        verify(jdbcTemplate).update(anyString(), eq(17L), eq("想看"), eq("want_to_watch"));
        verify(jdbcTemplate).update(anyString(), eq(17L), eq("在看"), eq("watching"));
        verify(jdbcTemplate).update(anyString(), eq(17L), eq("看过"), eq("watched"));
    }
}
