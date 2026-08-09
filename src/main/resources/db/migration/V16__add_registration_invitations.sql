CREATE TABLE IF NOT EXISTS `registration_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hash` char(64) COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `used_by` bigint DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_invitation_token_hash` (`token_hash`),
  KEY `idx_registration_invitation_status_expiry` (`status`, `expires_at`),
  KEY `idx_registration_invitation_creator` (`created_by`, `created_at`),
  CONSTRAINT `fk_registration_invitation_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_registration_invitation_user` FOREIGN KEY (`used_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
