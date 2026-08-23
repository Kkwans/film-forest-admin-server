package com.filmforest.common.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminListPagePreferenceService {

    public static final int DEFAULT_SIZE = 10;
    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 100;

    private final AdminListPagePreferenceMapper mapper;

    public AdminListPagePreferenceService(AdminListPagePreferenceMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Integer> get(long userId) {
        return toMap(mapper.selectById(userId));
    }

    @Transactional
    public Map<String, Integer> update(long userId, String rawKey, Integer rawSize) {
        String key = normalizeKey(rawKey);
        int size = normalizeSize(rawSize);
        AdminListPagePreference preference = mapper.selectById(userId);
        if (preference == null) {
            preference = defaultPreference(userId);
            mapper.insert(preference);
        }
        set(preference, key, size);
        mapper.updateById(preference);
        return toMap(preference);
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("分页偏好名称不能为空");
        }
        String key = value.trim();
        if (!switch (key) {
            case "crawlerLogs", "operationLogs", "resources", "notifications", "users", "content" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("不支持的分页偏好");
        }
        return key;
    }

    public static int normalizeSize(Integer value) {
        if (value == null || value < MIN_SIZE || value > MAX_SIZE) {
            throw new IllegalArgumentException("每页条数必须是 2 到 100 之间的整数");
        }
        return value;
    }

    private static AdminListPagePreference defaultPreference(long userId) {
        AdminListPagePreference preference = new AdminListPagePreference();
        preference.setUserId(userId);
        preference.setCrawlerLogsSize(DEFAULT_SIZE);
        preference.setOperationLogsSize(DEFAULT_SIZE);
        preference.setResourcesSize(DEFAULT_SIZE);
        preference.setNotificationsSize(DEFAULT_SIZE);
        preference.setUsersSize(DEFAULT_SIZE);
        preference.setContentSize(DEFAULT_SIZE);
        return preference;
    }

    private static void set(AdminListPagePreference preference, String key, int size) {
        switch (key) {
            case "crawlerLogs" -> preference.setCrawlerLogsSize(size);
            case "operationLogs" -> preference.setOperationLogsSize(size);
            case "resources" -> preference.setResourcesSize(size);
            case "notifications" -> preference.setNotificationsSize(size);
            case "users" -> preference.setUsersSize(size);
            case "content" -> preference.setContentSize(size);
            default -> throw new IllegalArgumentException("不支持的分页偏好");
        }
    }

    private static Map<String, Integer> toMap(AdminListPagePreference preference) {
        int crawlerLogs = safe(preference == null ? null : preference.getCrawlerLogsSize());
        int operationLogs = safe(preference == null ? null : preference.getOperationLogsSize());
        int resources = safe(preference == null ? null : preference.getResourcesSize());
        int notifications = safe(preference == null ? null : preference.getNotificationsSize());
        int users = safe(preference == null ? null : preference.getUsersSize());
        int content = safe(preference == null ? null : preference.getContentSize());
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("crawlerLogs", crawlerLogs);
        result.put("operationLogs", operationLogs);
        result.put("resources", resources);
        result.put("notifications", notifications);
        result.put("users", users);
        result.put("content", content);
        return result;
    }

    private static int safe(Integer value) {
        return value != null && value >= MIN_SIZE && value <= MAX_SIZE ? value : DEFAULT_SIZE;
    }
}
