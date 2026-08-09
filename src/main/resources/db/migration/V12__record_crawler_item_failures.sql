-- Phase 8: 将单条爬取失败与 Job 隔离记录，便于定位、重试和运营展示。
-- 回滚边界：应用回滚后该审计表可保留；部署前归档仍覆盖全库。

CREATE TABLE `crawler_job_item_failure` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint unsigned NOT NULL,
  `source_code` varchar(50) NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `external_id` varchar(100) NOT NULL,
  `source_url` varchar(1000) NOT NULL,
  `failure_stage` varchar(20) NOT NULL,
  `error_category` varchar(64) NOT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT '1',
  `retry_exhausted` tinyint NOT NULL DEFAULT '0',
  `diagnostic` varchar(1000) DEFAULT NULL,
  `failed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawler_job_item_failure`
    (`job_id`, `source_code`, `content_type`, `external_id`),
  KEY `idx_crawler_item_failure_job` (`job_id`, `failed_at`),
  KEY `idx_crawler_item_failure_source`
    (`source_code`, `content_type`, `failure_stage`, `failed_at`),
  CONSTRAINT `fk_crawler_item_failure_job`
    FOREIGN KEY (`job_id`) REFERENCES `crawler_task_log` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_crawler_item_failure_stage`
    CHECK (`failure_stage` IN ('fetch', 'parse', 'persistence')),
  CONSTRAINT `chk_crawler_item_failure_retry`
    CHECK (`retry_exhausted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
