package com.filmforest.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.notification.entity.MailOutbox;
import com.filmforest.notification.mapper.MailOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MailOutboxService {
    private static final Logger log = LoggerFactory.getLogger(MailOutboxService.class);
    private static final int MAX_ATTEMPTS = 5;
    private final MailOutboxMapper mapper;
    private final SmtpService smtpService;
    private final AdminNotificationService notificationService;

    public MailOutboxService(MailOutboxMapper mapper, SmtpService smtpService,
                             AdminNotificationService notificationService) {
        this.mapper = mapper;
        this.smtpService = smtpService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox-dispatch-interval-ms:30000}")
    public void dispatchDue() {
        if (!smtpService.isDeliveryEnabled()) return;
        List<MailOutbox> due = mapper.selectList(new LambdaQueryWrapper<MailOutbox>()
                .in(MailOutbox::getStatus, "PENDING", "RETRY")
                .le(MailOutbox::getNextAttemptAt, LocalDateTime.now())
                .orderByAsc(MailOutbox::getId)
                .last("LIMIT 20"));
        for (MailOutbox mail : due) dispatchOne(mail);
    }

    void dispatchOne(MailOutbox mail) {
        try {
            smtpService.deliver(mail);
            mail.setStatus("SENT");
            mail.setSentAt(LocalDateTime.now());
            mail.setLastError(null);
            mapper.updateById(mail);
        } catch (SmtpService.SmtpDeliveryException exception) {
            int attempt = (mail.getAttemptCount() == null ? 0 : mail.getAttemptCount()) + 1;
            mail.setAttemptCount(attempt);
            mail.setLastError(exception.category() + ": " + exception.getMessage());
            if (attempt >= MAX_ATTEMPTS) {
                mail.setStatus("FAILED");
                mapper.updateById(mail);
                publishFinalFailureAlert(mail);
            } else {
                mail.setStatus("RETRY");
                mail.setNextAttemptAt(LocalDateTime.now().plusMinutes(1L << (attempt - 1)));
                mapper.updateById(mail);
            }
            log.warn("邮件 Outbox 投递失败: id={}, attempt={}, category={}", mail.getId(), attempt, exception.category());
        }
    }

    private void publishFinalFailureAlert(MailOutbox mail) {
        try {
            notificationService.publishStationOnlyToAdmins(new AdminNotificationService.NotificationEvent(
                    "DATA_ANOMALY", "ERROR", "邮件投递最终失败",
                    "收件人 " + maskEmail(mail.getRecipient()) + " 在 5 次尝试后仍未投递成功。",
                    "/settings", "MAIL_OUTBOX", mail.getId(), "mail-outbox:" + mail.getId() + ":failed"));
        } catch (RuntimeException error) {
            // 邮件终态已经持久化；告警写入失败不能使同一封邮件重新投递。
            log.error("邮件最终失败告警创建失败: outboxId={}", mail.getId(), error);
        }
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }
}
