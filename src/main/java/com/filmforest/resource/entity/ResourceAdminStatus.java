package com.filmforest.resource.entity;

import java.util.Locale;

public enum ResourceAdminStatus {
    ACTIVE,
    DISABLED,
    REMOVED;

    public static ResourceAdminStatus from(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("资源状态只允许 ACTIVE、DISABLED 或 REMOVED", invalid);
        }
    }
}
