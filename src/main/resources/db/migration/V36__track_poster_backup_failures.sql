-- 持久化海报本地化失败状态，避免失效源在每个调度周期中被无限重试。
-- 失败记录与当前 source_url 绑定；爬虫更新源地址后，新地址会自动重新进入待处理队列。

CREATE TABLE `content_poster_backup_failure` (
  `content_type` varchar(32) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `source_url` varchar(1000) NOT NULL,
  `failure_code` varchar(64) NOT NULL,
  `http_status` smallint unsigned DEFAULT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT 1,
  `terminal` tinyint(1) NOT NULL DEFAULT 0,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `first_failed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_failed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`content_type`, `content_id`),
  KEY `idx_poster_backup_failure_retry` (`terminal`, `next_retry_at`),
  CONSTRAINT `chk_poster_backup_failure_type`
    CHECK (`content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')),
  CONSTRAINT `chk_poster_backup_failure_attempts` CHECK (`attempt_count` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
