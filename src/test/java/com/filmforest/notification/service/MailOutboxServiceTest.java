package com.filmforest.notification.service;

import com.filmforest.notification.entity.MailOutbox;
import com.filmforest.notification.mapper.MailOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxServiceTest {
    @Mock private MailOutboxMapper mapper;
    @Mock private SmtpService smtpService;
    @Mock private AdminNotificationService notificationService;
    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        service = new MailOutboxService(mapper, smtpService, notificationService);
    }

    @Test
    void successfulDeliveryMarksMessageSent() {
        MailOutbox mail = mail(1L, 0);
        when(mapper.updateById(mail)).thenReturn(1);

        service.dispatchOne(mail);

        assertThat(mail.getStatus()).isEqualTo("SENT");
        assertThat(mail.getSentAt()).isNotNull();
        verify(notificationService, never()).publishStationOnlyToAdmins(any());
    }

    @Test
    void failuresUseExponentialRetryAndFinalFailureCreatesStationAlert() {
        MailOutbox retry = mail(2L, 1);
        doThrow(new SmtpService.SmtpDeliveryException("TEMPORARY_REJECTED", "暂时拒绝"))
                .when(smtpService).deliver(retry);
        service.dispatchOne(retry);
        assertThat(retry.getStatus()).isEqualTo("RETRY");
        assertThat(retry.getAttemptCount()).isEqualTo(2);
        assertThat(retry.getNextAttemptAt()).isNotNull();

        MailOutbox failed = mail(3L, 4);
        doThrow(new SmtpService.SmtpDeliveryException("PERMANENT_REJECTED", "永久拒绝"))
                .when(smtpService).deliver(failed);
        service.dispatchOne(failed);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getAttemptCount()).isEqualTo(5);
        ArgumentCaptor<AdminNotificationService.NotificationEvent> event = ArgumentCaptor.forClass(AdminNotificationService.NotificationEvent.class);
        verify(notificationService).publishStationOnlyToAdmins(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("DATA_ANOMALY");
        assertThat(event.getValue().message()).doesNotContain("admin@example.test");
    }

    private static MailOutbox mail(long id, int attempts) {
        MailOutbox mail = new MailOutbox();
        mail.setId(id);
        mail.setRecipient("admin@example.test");
        mail.setSubject("主题");
        mail.setBody("内容");
        mail.setStatus("PENDING");
        mail.setAttemptCount(attempts);
        return mail;
    }
}
