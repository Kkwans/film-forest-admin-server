package com.filmforest.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.notification.entity.AdminNotification;
import com.filmforest.notification.entity.AdminNotificationPreference;
import com.filmforest.notification.entity.MailOutbox;
import com.filmforest.notification.entity.SmtpSetting;
import com.filmforest.notification.mapper.AdminNotificationMapper;
import com.filmforest.notification.mapper.AdminNotificationPreferenceMapper;
import com.filmforest.notification.mapper.MailOutboxMapper;
import com.filmforest.notification.mapper.SmtpSettingMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminNotificationService {

    private final AdminNotificationMapper notificationMapper;
    private final AdminNotificationPreferenceMapper preferenceMapper;
    private final MailOutboxMapper outboxMapper;
    private final SmtpSettingMapper smtpSettingMapper;
    private final UserMapper userMapper;

    public AdminNotificationService(AdminNotificationMapper notificationMapper,
                                    AdminNotificationPreferenceMapper preferenceMapper,
                                    MailOutboxMapper outboxMapper,
                                    SmtpSettingMapper smtpSettingMapper,
                                    UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.preferenceMapper = preferenceMapper;
        this.outboxMapper = outboxMapper;
        this.smtpSettingMapper = smtpSettingMapper;
        this.userMapper = userMapper;
    }

    public IPage<AdminNotification> list(long userId, boolean unreadOnly, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("通知分页参数不合法");
        }
        return notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminNotification>()
                        .eq(AdminNotification::getUserId, userId)
                        .isNull(unreadOnly, AdminNotification::getReadAt)
                        .orderByDesc(AdminNotification::getCreatedAt)
                        .orderByDesc(AdminNotification::getId));
    }

    public long unreadCount(long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<AdminNotification>()
                .eq(AdminNotification::getUserId, userId)
                .isNull(AdminNotification::getReadAt));
    }

    public boolean markRead(long userId, long notificationId) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<AdminNotification>()
                .eq(AdminNotification::getId, notificationId)
                .eq(AdminNotification::getUserId, userId)
                .isNull(AdminNotification::getReadAt)
                .set(AdminNotification::getReadAt, LocalDateTime.now())) > 0;
    }

    public long markAllRead(long userId) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<AdminNotification>()
                .eq(AdminNotification::getUserId, userId)
                .isNull(AdminNotification::getReadAt)
                .set(AdminNotification::getReadAt, LocalDateTime.now()));
    }

    public AdminNotificationPreference getPreference(long userId) {
        AdminNotificationPreference preference = preferenceMapper.selectById(userId);
        return preference != null ? preference : defaultPreference(userId);
    }

    @Transactional
    public AdminNotificationPreference savePreference(long userId, AdminNotificationPreference input) {
        AdminNotificationPreference preference = getPreference(userId);
        preference.setEmailEnabled(flag(input.getEmailEnabled(), preference.getEmailEnabled()));
        preference.setCrawlerFailure(flag(input.getCrawlerFailure(), preference.getCrawlerFailure()));
        preference.setCrawlerRecovery(flag(input.getCrawlerRecovery(), preference.getCrawlerRecovery()));
        preference.setDataAnomaly(flag(input.getDataAnomaly(), preference.getDataAnomaly()));
        preference.setCrawlerSuccess(flag(input.getCrawlerSuccess(), preference.getCrawlerSuccess()));
        if (preferenceMapper.selectById(userId) == null) preferenceMapper.insert(preference);
        else preferenceMapper.updateById(preference);
        return preference;
    }

    @Transactional
    public int publishToAdmins(NotificationEvent event) {
        return publish(event, true);
    }

    @Transactional
    public int publishStationOnlyToAdmins(NotificationEvent event) {
        return publish(event, false);
    }

    private int publish(NotificationEvent event, boolean emailEligible) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.ADMIN)
                .eq(User::getStatus, 1)
                .eq(User::getIsDeleted, 0));
        SmtpSetting smtp = emailEligible ? smtpSettingMapper.selectById(1) : null;
        boolean smtpReady = smtp != null && smtp.getEnabled() != null && smtp.getEnabled() == 1
                && smtp.getHost() != null && smtp.getFromEmail() != null
                && ((smtp.getUsername() == null || smtp.getUsername().isBlank())
                || (smtp.getPasswordCiphertext() != null && smtp.getPasswordIv() != null));
        int created = 0;
        for (User admin : admins) {
            AdminNotificationPreference preference = getPreference(admin.getId());
            if (!subscribed(preference, event.eventType())) continue;
            AdminNotification notification = new AdminNotification();
            notification.setUserId(admin.getId());
            notification.setEventType(event.eventType());
            notification.setSeverity(event.severity());
            notification.setTitle(event.title());
            notification.setMessage(event.message());
            notification.setLink(event.link());
            notification.setReferenceType(event.referenceType());
            notification.setReferenceId(event.referenceId());
            notification.setIdempotencyKey(event.idempotencyKey());
            notification.setCreatedAt(LocalDateTime.now());
            try {
                notificationMapper.insert(notification);
                if (smtpReady && preference.getEmailEnabled() == 1
                        && admin.getEmail() != null && !admin.getEmail().isBlank()) {
                    enqueueEmail(notification, admin.getEmail());
                }
                created++;
            } catch (DuplicateKeyException ignored) {
                // 同一用户、同一业务事件只创建一次通知。
            }
        }
        return created;
    }

    private void enqueueEmail(AdminNotification notification, String recipient) {
        MailOutbox outbox = new MailOutbox();
        outbox.setNotificationId(notification.getId());
        outbox.setRecipient(recipient.trim());
        outbox.setSubject("[影视森林] " + notification.getTitle());
        outbox.setBody(notification.getMessage() + "\n\n请登录影视森林管理端查看详情。");
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        outbox.setIdempotencyKey("notification:" + notification.getId() + ":email");
        outbox.setCreatedAt(LocalDateTime.now());
        try {
            outboxMapper.insert(outbox);
        } catch (DuplicateKeyException ignored) {
            // 通知邮件同样遵守幂等边界。
        }
    }

    private static AdminNotificationPreference defaultPreference(long userId) {
        AdminNotificationPreference preference = new AdminNotificationPreference();
        preference.setUserId(userId);
        preference.setEmailEnabled(0);
        preference.setCrawlerFailure(1);
        preference.setCrawlerRecovery(1);
        preference.setDataAnomaly(1);
        preference.setCrawlerSuccess(0);
        return preference;
    }

    private static int flag(Integer value, Integer fallback) {
        int resolved = value == null ? (fallback == null ? 0 : fallback) : value;
        if (resolved != 0 && resolved != 1) throw new IllegalArgumentException("通知偏好只允许 0 或 1");
        return resolved;
    }

    private static boolean subscribed(AdminNotificationPreference preference, String eventType) {
        return switch (eventType) {
            case "CRAWLER_FAILED", "CRAWLER_INTERRUPTED" -> preference.getCrawlerFailure() == 1;
            case "CRAWLER_RECOVERED" -> preference.getCrawlerRecovery() == 1;
            case "DATA_ANOMALY" -> preference.getDataAnomaly() == 1;
            case "CRAWLER_SUCCESS" -> preference.getCrawlerSuccess() == 1;
            default -> true;
        };
    }

    public record NotificationEvent(String eventType, String severity, String title, String message,
                                    String link, String referenceType, Long referenceId,
                                    String idempotencyKey) {
        public NotificationEvent {
            if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("通知事件类型不能为空");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("通知标题不能为空");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("通知内容不能为空");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("通知幂等键不能为空");
            severity = severity == null ? "INFO" : severity;
        }
    }
}
