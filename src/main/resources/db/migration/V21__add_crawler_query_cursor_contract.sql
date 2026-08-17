-- 来源查询契约、跨 Job 游标和可观测进度。所有变更保持向后兼容。
ALTER TABLE `crawler_schedule`
  ADD COLUMN `source_sort` varchar(20) NOT NULL DEFAULT 'TIME' AFTER `rate_limit_ms`,
  ADD COLUMN `source_filter_json` json NULL AFTER `source_sort`,
  ADD COLUMN `traversal_mode` varchar(32) NOT NULL DEFAULT 'CONTINUOUS_SYNC' AFTER `source_filter_json`,
  ADD COLUMN `end_policy` varchar(32) NOT NULL DEFAULT 'HOLD_COMPLETED' AFTER `traversal_mode`,
  ADD COLUMN `new_item_limit` int NOT NULL DEFAULT 10 AFTER `end_policy`,
  ADD COLUMN `backfill_item_limit` int NOT NULL DEFAULT 10 AFTER `new_item_limit`,
  ADD COLUMN `manual_run_limit` int NOT NULL DEFAULT 100 AFTER `backfill_item_limit`,
  ADD COLUMN `configuration_status` varchar(32) NOT NULL DEFAULT 'VALIDATED' AFTER `manual_run_limit`,
  ADD COLUMN `configuration_issue` varchar(1000) NULL AFTER `configuration_status`,
  ADD COLUMN `query_profile_hash` varchar(64) NULL AFTER `configuration_issue`;

UPDATE `crawler_schedule`
SET `source_sort` = CASE LOWER(COALESCE(`priority`, ''))
  WHEN 'by_score' THEN 'RATING'
  WHEN 'by_hot' THEN 'POPULARITY'
  ELSE 'TIME'
END,
`traversal_mode` = CASE
  WHEN LOWER(COALESCE(`crawl_mode`, 'latest')) = 'full' THEN 'MANUAL_FULL'
  WHEN LOWER(COALESCE(`priority`, '')) IN ('by_score', 'by_hot') THEN 'BACKFILL_CONTINUE'
  ELSE 'CONTINUOUS_SYNC'
END,
`end_policy` = 'HOLD_COMPLETED',
`new_item_limit` = GREATEST(COALESCE(`batch_size`, 10), 1),
`backfill_item_limit` = GREATEST(COALESCE(`batch_size`, 10), 1),
`manual_run_limit` = GREATEST(COALESCE(`batch_size`, 100), 1),
`configuration_status` = CASE
  WHEN LOWER(COALESCE(`priority`, '')) IN ('by_score', 'by_hot') THEN 'NEEDS_REVIEW'
  ELSE 'VALIDATED'
END,
`configuration_issue` = CASE
  WHEN LOWER(COALESCE(`priority`, '')) IN ('by_score', 'by_hot')
    THEN CONCAT('历史 priority=', `priority`, ' 已映射为来源排序，需通过来源能力预览确认')
  ELSE NULL
END;

CREATE TABLE `crawler_schedule_cursor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint NOT NULL,
  `profile_hash` varchar(64) NOT NULL,
  `source_code` varchar(64) NOT NULL,
  `content_type` varchar(32) NOT NULL,
  `source_sort` varchar(20) NOT NULL,
  `traversal_mode` varchar(32) NOT NULL,
  `query_snapshot` text NULL,
  `next_page` int NOT NULL DEFAULT 1,
  `next_item_index` int NOT NULL DEFAULT 0,
  `next_external_id` varchar(255) NULL,
  `last_committed_external_id` varchar(255) NULL,
  `head_watermark` varchar(255) NULL,
  `state` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `cycle` int NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 0,
  `last_error` varchar(1000) NULL,
  `last_run_at` datetime NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawler_schedule_cursor_schedule` (`schedule_id`),
  KEY `idx_crawler_schedule_cursor_state` (`state`, `updated_at`),
  CONSTRAINT `fk_crawler_schedule_cursor_schedule`
    FOREIGN KEY (`schedule_id`) REFERENCES `crawler_schedule` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `crawler_task_log`
  ADD COLUMN `source_sort` varchar(20) NULL AFTER `crawl_mode`,
  ADD COLUMN `traversal_mode` varchar(32) NULL AFTER `source_sort`,
  ADD COLUMN `query_profile_hash` varchar(64) NULL AFTER `traversal_mode`,
  ADD COLUMN `query_snapshot` text NULL AFTER `query_profile_hash`,
  ADD COLUMN `config_snapshot` text NULL AFTER `query_snapshot`,
  ADD COLUMN `outcome_code` varchar(32) NULL AFTER `config_snapshot`,
  ADD COLUMN `pages_scanned` int NOT NULL DEFAULT 0 AFTER `failed_count`,
  ADD COLUMN `list_items_scanned` int NOT NULL DEFAULT 0 AFTER `pages_scanned`,
  ADD COLUMN `detail_attempted` int NOT NULL DEFAULT 0 AFTER `list_items_scanned`,
  ADD COLUMN `cursor_advanced` int NOT NULL DEFAULT 0 AFTER `detail_attempted`,
  ADD COLUMN `new_items` int NOT NULL DEFAULT 0 AFTER `cursor_advanced`,
  ADD COLUMN `backfill_items` int NOT NULL DEFAULT 0 AFTER `new_items`;
