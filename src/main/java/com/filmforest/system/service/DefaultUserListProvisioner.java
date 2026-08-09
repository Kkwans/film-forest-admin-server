package com.filmforest.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultUserListProvisioner {

    private static final List<DefaultList> DEFAULT_LISTS = List.of(
            new DefaultList("想看", "want_to_watch"),
            new DefaultList("在看", "watching"),
            new DefaultList("看过", "watched"));

    private final JdbcTemplate jdbcTemplate;

    public DefaultUserListProvisioner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createFor(Long userId) {
        for (DefaultList list : DEFAULT_LISTS) {
            jdbcTemplate.update("""
                    INSERT INTO user_movie_list(user_id, name, type, is_default)
                    VALUES (?, ?, ?, 1)
                    """, userId, list.name(), list.type());
        }
    }

    private record DefaultList(String name, String type) {}
}
