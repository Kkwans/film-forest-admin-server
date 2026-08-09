package com.filmforest.notification.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.filmforest.notification.entity.MailOutbox;
import com.filmforest.notification.entity.SmtpSetting;
import com.filmforest.notification.mapper.SmtpSettingMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SmtpService {
    private static final int SETTING_ID = 1;
    private final SmtpSettingMapper mapper;
    private final SecretCipher cipher;
    private final SmtpTransport transport;

    public SmtpService(SmtpSettingMapper mapper, SecretCipher cipher, SmtpTransport transport) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.transport = transport;
    }

    public SmtpSettingView view() {
        return view(setting());
    }

    public boolean isDeliveryEnabled() {
        SmtpSetting setting = setting();
        return setting != null && setting.getEnabled() != null && setting.getEnabled() == 1 && configured(setting);
    }

    @Transactional
    public SmtpSettingView save(SmtpSettingRequest request) {
        String mode = normalizeMode(request.securityMode());
        SmtpSetting existing = setting();
        SecretCipher.EncryptedSecret encrypted = null;
        if (request.password() != null && !request.password().isBlank()) encrypted = cipher.encrypt(request.password());
        byte[] ciphertext = request.clearPassword() ? null : encrypted != null ? encrypted.ciphertext() : existing == null ? null : existing.getPasswordCiphertext();
        byte[] iv = request.clearPassword() ? null : encrypted != null ? encrypted.iv() : existing == null ? null : existing.getPasswordIv();
        int keyVersion = encrypted != null ? encrypted.keyVersion() : existing == null || existing.getPasswordKeyVersion() == null ? 1 : existing.getPasswordKeyVersion();
        boolean enabled = request.enabled();
        boolean authenticated = request.username() != null && !request.username().isBlank();
        if (enabled && (request.host().isBlank() || request.fromEmail().isBlank()
                || (authenticated && ciphertext == null))) {
            throw new IllegalArgumentException("启用 SMTP 前必须完整配置服务器、发件人和密码");
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            SmtpSetting created = new SmtpSetting();
            created.setId(SETTING_ID);
            created.setHost(request.host().trim());
            created.setPort(request.port());
            created.setUsername(trimToNull(request.username()));
            created.setPasswordCiphertext(ciphertext);
            created.setPasswordIv(iv);
            created.setPasswordKeyVersion(keyVersion);
            created.setFromEmail(request.fromEmail().trim());
            created.setFromName(trimToNull(request.fromName()));
            created.setSecurityMode(mode);
            created.setEnabled(enabled ? 1 : 0);
            created.setCreatedAt(now);
            mapper.insert(created);
        } else {
            mapper.update(null, new UpdateWrapper<SmtpSetting>()
                    .eq("id", SETTING_ID)
                    .set("host", request.host().trim())
                    .set("port", request.port())
                    .set("username", trimToNull(request.username()))
                    .set("password_ciphertext", ciphertext)
                    .set("password_iv", iv)
                    .set("password_key_version", keyVersion)
                    .set("from_email", request.fromEmail().trim())
                    .set("from_name", trimToNull(request.fromName()))
                    .set("security_mode", mode)
                    .set("enabled", enabled ? 1 : 0)
                    .set("updated_at", now));
        }
        return view();
    }

    public SmtpTestResult testConnection() {
        try {
            SmtpSetting setting = requireConfigured();
            transport.test(setting, password(setting));
            return new SmtpTestResult(true, "SUCCESS", "SMTP 连接与认证成功");
        } catch (SmtpDeliveryException exception) {
            return new SmtpTestResult(false, exception.category(), exception.getMessage());
        }
    }

    public SmtpTestResult sendTest(String recipient) {
        try {
            SmtpSetting setting = requireConfigured();
            transport.send(setting, password(setting), recipient, "影视森林 SMTP 测试",
                    "这是一封由影视森林管理端发送的测试邮件。收到此邮件表示 SMTP 配置可用。");
            return new SmtpTestResult(true, "SUCCESS", "测试邮件已发送");
        } catch (SmtpDeliveryException exception) {
            return new SmtpTestResult(false, exception.category(), exception.getMessage());
        }
    }

    public void deliver(MailOutbox outbox) {
        SmtpSetting setting = requireConfigured();
        transport.send(setting, password(setting), outbox.getRecipient(), outbox.getSubject(), outbox.getBody());
    }

    private String password(SmtpSetting setting) {
        if (setting.getUsername() == null || setting.getUsername().isBlank()) return "";
        try {
            return cipher.decrypt(setting.getPasswordCiphertext(), setting.getPasswordIv(), setting.getPasswordKeyVersion());
        } catch (RuntimeException exception) {
            throw new SmtpDeliveryException("CONFIGURATION_ERROR", "SMTP 凭据不可用或无法解密");
        }
    }

    private SmtpSetting requireConfigured() {
        SmtpSetting setting = setting();
        if (setting == null || !configured(setting)) throw new SmtpDeliveryException("NOT_CONFIGURED", "SMTP 尚未完整配置");
        return setting;
    }

    private SmtpSetting setting() {
        return mapper.selectById(SETTING_ID);
    }

    private static boolean configured(SmtpSetting setting) {
        return setting.getHost() != null && !setting.getHost().isBlank()
                && setting.getPort() != null && setting.getFromEmail() != null && !setting.getFromEmail().isBlank()
                && ((setting.getUsername() == null || setting.getUsername().isBlank())
                || (setting.getPasswordCiphertext() != null && setting.getPasswordIv() != null));
    }

    private static SmtpSettingView view(SmtpSetting setting) {
        if (setting == null) return new SmtpSettingView(false, false, null, null, null, null, null, "STARTTLS", null);
        return new SmtpSettingView(configured(setting), setting.getEnabled() != null && setting.getEnabled() == 1,
                setting.getHost(), setting.getPort(), setting.getUsername(), setting.getFromEmail(), setting.getFromName(),
                setting.getSecurityMode(), setting.getPasswordCiphertext() == null ? null : "••••••••");
    }

    private static String normalizeMode(String value) {
        String mode = value == null ? "STARTTLS" : value.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("NONE") && !mode.equals("STARTTLS") && !mode.equals("SSL")) {
            throw new IllegalArgumentException("SMTP 安全模式只允许 NONE、STARTTLS 或 SSL");
        }
        return mode;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SmtpSettingRequest(@NotBlank String host, @Min(1) @Max(65535) int port,
                                     String username, String password, boolean clearPassword,
                                     @Email @NotBlank String fromEmail, String fromName,
                                     String securityMode, boolean enabled) {
    }
    public record SmtpSettingView(boolean configured, boolean enabled, String host, Integer port,
                                  String username, String fromEmail, String fromName,
                                  String securityMode, String passwordMask) {
    }
    public record SmtpTestResult(boolean success, String category, String message) {
    }
    public record TestMailRequest(@Email @NotBlank String recipient) {
    }
    public static class SmtpDeliveryException extends RuntimeException {
        private final String category;
        public SmtpDeliveryException(String category, String message) { super(message); this.category = category; }
        public String category() { return category; }
    }
}
