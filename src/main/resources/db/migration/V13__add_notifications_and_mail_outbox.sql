-- Phase 8: 管理员站内通知、订阅偏好、系统 SMTP 与可靠邮件 Outbox。

CREATE TABLE `admin_notification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `severity` varchar(20) NOT NULL DEFAULT 'INFO',
  `title` varchar(160) NOT NULL,
  `message` text NOT NULL,
  `link` varchar(500) DEFAULT NULL,
  `reference_type` varchar(50) DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `idempotency_key` varchar(160) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_notification_user_event` (`user_id`, `idempotency_key`),
  KEY `idx_admin_notification_inbox` (`user_id`, `read_at`, `created_at`),
  CONSTRAINT `fk_admin_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_admin_notification_severity` CHECK (`severity` IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `admin_notification_preference` (
  `user_id` bigint NOT NULL,
  `email_enabled` tinyint NOT NULL DEFAULT 0,
  `crawler_failure` tinyint NOT NULL DEFAULT 1,
  `crawler_recovery` tinyint NOT NULL DEFAULT 1,
  `data_anomaly` tinyint NOT NULL DEFAULT 1,
  `crawler_success` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_admin_notification_preference_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_admin_notification_email_enabled` CHECK (`email_enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `smtp_setting` (
  `id` tinyint unsigned NOT NULL DEFAULT 1,
  `host` varchar(255) DEFAULT NULL,
  `port` int DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `password_ciphertext` varbinary(4096) DEFAULT NULL,
  `password_iv` binary(12) DEFAULT NULL,
  `password_key_version` int NOT NULL DEFAULT 1,
  `from_email` varchar(255) DEFAULT NULL,
  `from_name` varchar(160) DEFAULT NULL,
  `security_mode` varchar(20) NOT NULL DEFAULT 'STARTTLS',
  `enabled` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_smtp_singleton` CHECK (`id` = 1),
  CONSTRAINT `chk_smtp_security_mode` CHECK (`security_mode` IN ('NONE', 'STARTTLS', 'SSL')),
  CONSTRAINT `chk_smtp_enabled` CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_smtp_port` CHECK (`port` IS NULL OR (`port` BETWEEN 1 AND 65535))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `smtp_setting` (`id`, `enabled`) VALUES (1, 0);

CREATE TABLE `mail_outbox` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `notification_id` bigint unsigned DEFAULT NULL,
  `recipient` varchar(255) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_error` varchar(1000) DEFAULT NULL,
  `idempotency_key` varchar(180) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_outbox_idempotency` (`idempotency_key`),
  KEY `idx_mail_outbox_dispatch` (`status`, `next_attempt_at`, `id`),
  CONSTRAINT `fk_mail_outbox_notification` FOREIGN KEY (`notification_id`) REFERENCES `admin_notification` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_mail_outbox_status` CHECK (`status` IN ('PENDING', 'RETRY', 'SENT', 'FAILED')),
  CONSTRAINT `chk_mail_outbox_attempt_count` CHECK (`attempt_count` BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
