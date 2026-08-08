-- Phase 5: 每用户海报偏好、加密 TMDB 凭据与显式批量补全 Job。
-- 回滚边界：应用回滚后保留新增表；不修改或覆盖五类内容表的 poster_url。

CREATE TABLE `user_poster_setting` (
  `user_id` bigint NOT NULL,
  `poster_source` varchar(16) NOT NULL DEFAULT 'original',
  `credential_type` varchar(32) DEFAULT NULL,
  `credential_ciphertext` varbinary(2048) DEFAULT NULL,
  `credential_iv` varbinary(32) DEFAULT NULL,
  `credential_key_version` int NOT NULL DEFAULT 1,
  `credential_hint` varchar(32) DEFAULT NULL,
  `validation_status` varchar(24) NOT NULL DEFAULT 'not_configured',
  `validation_error_code` varchar(64) DEFAULT NULL,
  `validated_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_poster_setting_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_user_poster_source`
    CHECK (`poster_source` IN ('original', 'tmdb')),
  CONSTRAINT `chk_user_poster_validation`
    CHECK (`validation_status` IN ('not_configured', 'unverified', 'valid', 'invalid', 'rate_limited', 'unavailable')),
  CONSTRAINT `chk_user_poster_credential_pair`
    CHECK ((`credential_ciphertext` IS NULL AND `credential_iv` IS NULL AND `credential_type` IS NULL)
      OR (`credential_ciphertext` IS NOT NULL AND `credential_iv` IS NOT NULL AND `credential_type` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `poster_enrichment_job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'queued',
  `cancel_requested` tinyint NOT NULL DEFAULT 0,
  `content_type` varchar(20) DEFAULT NULL,
  `total_count` int NOT NULL DEFAULT 0,
  `processed_count` int NOT NULL DEFAULT 0,
  `matched_count` int NOT NULL DEFAULT 0,
  `pending_count` int NOT NULL DEFAULT 0,
  `failed_count` int NOT NULL DEFAULT 0,
  `current_content_type` varchar(20) DEFAULT NULL,
  `current_content_id` bigint unsigned DEFAULT NULL,
  `error_summary` varchar(1000) DEFAULT NULL,
  `queued_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `started_at` datetime(6) DEFAULT NULL,
  `heartbeat_at` datetime(6) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `active_user_id` bigint GENERATED ALWAYS AS (
    CASE WHEN `status` IN ('queued', 'running', 'cancel_requested') THEN `user_id` ELSE NULL END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poster_enrichment_active_user` (`active_user_id`),
  KEY `idx_poster_enrichment_user_queued` (`user_id`, `queued_at`),
  KEY `idx_poster_enrichment_status_heartbeat` (`status`, `heartbeat_at`),
  -- user_id 被存储生成列 active_user_id 依赖。MySQL 不允许该基列使用级联外键动作；
  -- 用户删除采用逻辑删除，因此保留默认 RESTRICT 既满足兼容性，也避免 Job 失去审计主体。
  CONSTRAINT `fk_poster_enrichment_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `chk_poster_enrichment_status`
    CHECK (`status` IN ('queued', 'running', 'cancel_requested', 'success', 'partial_success', 'failed', 'cancelled', 'interrupted')),
  CONSTRAINT `chk_poster_enrichment_cancel_requested`
    CHECK (`cancel_requested` IN (0, 1)),
  CONSTRAINT `chk_poster_enrichment_content_type`
    CHECK (`content_type` IS NULL OR `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
