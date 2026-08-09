package com.filmforest.notification.service;

import com.filmforest.notification.config.SecretEncryptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {SecretCipher.class, SecretCipherWiringTest.WiringConfig.class})
class SecretCipherWiringTest {

    @Autowired
    private SecretCipher secretCipher;

    @Test
    void wiresProductionConstructorInSpringContext() {
        assertThat(secretCipher).isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    static class WiringConfig {
        @Bean
        SecretEncryptionProperties secretEncryptionProperties() {
            String key = Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
            return new SecretEncryptionProperties(key);
        }
    }
}
