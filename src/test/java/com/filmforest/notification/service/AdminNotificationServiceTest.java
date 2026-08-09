package com.filmforest.notification.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock private AdminNotificationMapper notificationMapper;
    @Mock private AdminNotificationPreferenceMapper preferenceMapper;
    @Mock private MailOutboxMapper outboxMapper;
    @Mock private SmtpSettingMapper smtpSettingMapper;
    @Mock private UserMapper userMapper;
    private AdminNotificationService service;

    @BeforeEach
    void setUp() {
        initializeTableInfo(AdminNotification.class);
        initializeTableInfo(AdminNotificationPreference.class);
        initializeTableInfo(User.class);
        service = new AdminNotificationService(notificationMapper, preferenceMapper, outboxMapper,
                smtpSettingMapper, userMapper);
    }

    @Test
    void inboxOperationsAreAlwaysScopedToCurrentAdministrator() {
        when(notificationMapper.update(any(), any())).thenReturn(1);

        assertThat(service.markRead(9L, 33L)).isTrue();

        ArgumentCaptor<Wrapper<AdminNotification>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(notificationMapper).update(any(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("id", "user_id", "read_at");
        assertThat(((AbstractWrapper<?, ?, ?>) update.getValue()).getParamNameValuePairs().values())
                .contains(9L, 33L);
    }

    @Test
    void defaultsSubscribeToFailuresButNotRoutineSuccess() {
        AdminNotificationPreference preference = service.getPreference(7L);

        assertThat(preference.getCrawlerFailure()).isOne();
        assertThat(preference.getCrawlerRecovery()).isOne();
        assertThat(preference.getDataAnomaly()).isOne();
        assertThat(preference.getCrawlerSuccess()).isZero();
        assertThat(preference.getEmailEnabled()).isZero();
    }

    @Test
    void preferenceRejectsValuesOutsideBooleanDomain() {
        AdminNotificationPreference input = new AdminNotificationPreference();
        input.setCrawlerFailure(2);

        assertThatThrownBy(() -> service.savePreference(7L, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 或 1");
    }

    @Test
    void publishCreatesFailureNotificationForEveryActiveAdminAndIgnoresDuplicates() {
        User first = admin(1L);
        User second = admin(2L);
        when(userMapper.selectList(any())).thenReturn(List.of(first, second));
        when(notificationMapper.insert(any(AdminNotification.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("duplicate"));

        int created = service.publishToAdmins(new AdminNotificationService.NotificationEvent(
                "CRAWLER_FAILED", "ERROR", "爬虫失败", "任务执行失败", "/crawler",
                "CRAWLER_JOB", 8L, "crawler-job:8:failed"));

        assertThat(created).isOne();
        ArgumentCaptor<AdminNotification> notification = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationMapper, org.mockito.Mockito.times(2)).insert(notification.capture());
        assertThat(notification.getAllValues()).extracting(AdminNotification::getUserId).containsExactly(1L, 2L);
        assertThat(notification.getAllValues()).allMatch(value -> value.getIdempotencyKey().equals("crawler-job:8:failed"));
    }

    @Test
    void routineSuccessIsSuppressedByDefaultPreference() {
        when(userMapper.selectList(any())).thenReturn(List.of(admin(1L)));

        int created = service.publishToAdmins(new AdminNotificationService.NotificationEvent(
                "CRAWLER_SUCCESS", "SUCCESS", "爬虫完成", "任务执行成功", "/crawler",
                "CRAWLER_JOB", 8L, "crawler-job:8:success"));

        assertThat(created).isZero();
    }

    @Test
    void enabledEmailSubscriptionCreatesOneOutboxMessageWithoutExposingCredentials() {
        User admin = admin(1L);
        admin.setEmail("admin@example.test");
        when(userMapper.selectList(any())).thenReturn(List.of(admin));
        AdminNotificationPreference preference = new AdminNotificationPreference();
        preference.setUserId(1L);
        preference.setEmailEnabled(1);
        preference.setCrawlerFailure(1);
        preference.setCrawlerRecovery(1);
        preference.setDataAnomaly(1);
        preference.setCrawlerSuccess(0);
        when(preferenceMapper.selectById(1L)).thenReturn(preference);
        SmtpSetting smtp = new SmtpSetting();
        smtp.setEnabled(1);
        smtp.setHost("smtp.example.test");
        smtp.setFromEmail("forest@example.test");
        when(smtpSettingMapper.selectById(1)).thenReturn(smtp);
        when(notificationMapper.insert(any(AdminNotification.class))).thenAnswer(invocation -> {
            ((AdminNotification) invocation.getArgument(0)).setId(88L);
            return 1;
        });

        service.publishToAdmins(new AdminNotificationService.NotificationEvent(
                "CRAWLER_FAILED", "ERROR", "爬虫失败", "任务执行失败", "/crawler",
                "CRAWLER_JOB", 8L, "crawler-job:8:failed"));

        ArgumentCaptor<MailOutbox> outbox = ArgumentCaptor.forClass(MailOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        assertThat(outbox.getValue().getRecipient()).isEqualTo("admin@example.test");
        assertThat(outbox.getValue().getNotificationId()).isEqualTo(88L);
        assertThat(outbox.getValue().getIdempotencyKey()).isEqualTo("notification:88:email");
        assertThat(outbox.getValue().getBody()).doesNotContain("smtp.example.test");
    }

    private static User admin(long id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.ADMIN);
        user.setStatus(1);
        user.setIsDeleted(0);
        return user;
    }

    private static void initializeTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "notification-service-test");
        assistant.setCurrentNamespace("notification-service-test." + entityType.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
