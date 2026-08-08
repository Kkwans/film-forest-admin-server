-- Phase 3: 建立来源条目映射与非破坏性资源 Diff 元数据。
-- 回滚边界：部署前完整备份；应用回滚后新增表/列可保留，不自动删除历史资源。

CREATE TABLE `crawler_source_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `source_code` varchar(50) NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `external_id` varchar(100) NOT NULL,
  `source_url` varchar(1000) NOT NULL,
  `internal_content_id` bigint unsigned DEFAULT NULL,
  `list_fingerprint` char(64) DEFAULT NULL,
  `detail_fingerprint` char(64) DEFAULT NULL,
  `first_seen_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `last_fetched_at` datetime(6) DEFAULT NULL,
  `last_parse_status` varchar(32) NOT NULL DEFAULT 'discovered',
  `last_error_category` varchar(64) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawler_source_item` (`source_code`, `content_type`, `external_id`),
  KEY `idx_source_item_internal` (`content_type`, `internal_content_id`),
  KEY `idx_source_item_seen` (`source_code`, `content_type`, `last_seen_at`),
  CONSTRAINT `chk_source_item_parse_status`
    CHECK (`last_parse_status` IN ('discovered', 'parsed', 'filtered', 'fetch_failed', 'parse_failed', 'persist_failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `resource_magnet`
  ADD COLUMN `source_code` varchar(50) NOT NULL DEFAULT 'legacy' AFTER `content_id`,
  ADD COLUMN `resource_key` char(64) DEFAULT NULL AFTER `source_code`,
  ADD COLUMN `raw_text` text DEFAULT NULL AFTER `resource_key`,
  ADD COLUMN `last_seen_at` datetime(6) DEFAULT NULL AFTER `raw_text`,
  ADD COLUMN `removed_at` datetime(6) DEFAULT NULL AFTER `last_seen_at`,
  ADD UNIQUE KEY `uk_resource_magnet_source_key`
    (`content_type`, `content_id`, `source_code`, `resource_key`),
  ADD KEY `idx_resource_magnet_source_active`
    (`content_type`, `content_id`, `source_code`, `removed_at`);

ALTER TABLE `resource_cloud`
  ADD COLUMN `source_code` varchar(50) NOT NULL DEFAULT 'legacy' AFTER `content_id`,
  ADD COLUMN `resource_key` char(64) DEFAULT NULL AFTER `source_code`,
  ADD COLUMN `raw_text` text DEFAULT NULL AFTER `resource_key`,
  ADD COLUMN `last_seen_at` datetime(6) DEFAULT NULL AFTER `raw_text`,
  ADD COLUMN `removed_at` datetime(6) DEFAULT NULL AFTER `last_seen_at`,
  ADD UNIQUE KEY `uk_resource_cloud_source_key`
    (`content_type`, `content_id`, `source_code`, `resource_key`),
  ADD KEY `idx_resource_cloud_source_active`
    (`content_type`, `content_id`, `source_code`, `removed_at`);

ALTER TABLE `resource_online`
  ADD COLUMN `source_code` varchar(50) NOT NULL DEFAULT 'legacy' AFTER `content_id`,
  ADD COLUMN `resource_key` char(64) DEFAULT NULL AFTER `source_code`,
  ADD COLUMN `raw_text` text DEFAULT NULL AFTER `resource_key`,
  ADD COLUMN `last_seen_at` datetime(6) DEFAULT NULL AFTER `raw_text`,
  ADD COLUMN `removed_at` datetime(6) DEFAULT NULL AFTER `last_seen_at`,
  ADD UNIQUE KEY `uk_resource_online_source_key`
    (`content_type`, `content_id`, `source_code`, `resource_key`),
  ADD KEY `idx_resource_online_source_active`
    (`content_type`, `content_id`, `source_code`, `removed_at`);

-- 保留旧值的读取兼容；新建/更新 schedule 统一由应用写 latest/full。
UPDATE `crawler_schedule`
SET `crawl_mode` = 'latest'
WHERE LOWER(`crawl_mode`) = 'incremental';

UPDATE `crawler_task_log`
SET `crawl_mode` = 'latest'
WHERE LOWER(`crawl_mode`) = 'incremental';
