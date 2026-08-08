-- Phase 1: 将 crawler_task_log 扩展为权威 Job 模型。
-- 回滚边界：部署前做完整备份；应用回滚可继续读取旧字段，新增列保留，不自动删除。

ALTER TABLE `crawler_schedule`
  ADD COLUMN `crawl_mode` varchar(20) NOT NULL DEFAULT 'incremental' AFTER `content_type`,
  ADD KEY `idx_schedule_due` (`enabled`, `next_run_time`);

ALTER TABLE `crawler_task_log`
  MODIFY COLUMN `status` varchar(32) NOT NULL,
  MODIFY COLUMN `duration_ms` bigint DEFAULT NULL,
  MODIFY COLUMN `started_at` datetime(6) DEFAULT NULL,
  MODIFY COLUMN `finished_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `source_code` varchar(50) DEFAULT NULL AFTER `content_type`,
  ADD COLUMN `crawl_mode` varchar(20) NOT NULL DEFAULT 'incremental' AFTER `source_code`,
  ADD COLUMN `trigger_type` varchar(20) NOT NULL DEFAULT 'manual' AFTER `crawl_mode`,
  ADD COLUMN `retry_of_job_id` bigint unsigned DEFAULT NULL AFTER `trigger_type`,
  ADD COLUMN `cancel_requested` tinyint NOT NULL DEFAULT '0' AFTER `status`,
  ADD COLUMN `current_page` int DEFAULT NULL AFTER `cancel_requested`,
  ADD COLUMN `current_item` varchar(500) DEFAULT NULL AFTER `current_page`,
  ADD COLUMN `discovered_count` int NOT NULL DEFAULT '0' AFTER `current_item`,
  ADD COLUMN `fetch_succeeded_count` int NOT NULL DEFAULT '0' AFTER `discovered_count`,
  ADD COLUMN `parse_succeeded_count` int NOT NULL DEFAULT '0' AFTER `fetch_succeeded_count`,
  ADD COLUMN `added_count` int NOT NULL DEFAULT '0' AFTER `parse_succeeded_count`,
  ADD COLUMN `updated_count` int NOT NULL DEFAULT '0' AFTER `added_count`,
  ADD COLUMN `unchanged_count` int NOT NULL DEFAULT '0' AFTER `updated_count`,
  ADD COLUMN `filtered_count` int NOT NULL DEFAULT '0' AFTER `unchanged_count`,
  ADD COLUMN `failed_count` int NOT NULL DEFAULT '0' AFTER `filtered_count`,
  ADD COLUMN `checkpoint` json DEFAULT NULL AFTER `failed_count`,
  ADD COLUMN `heartbeat_at` datetime(6) DEFAULT NULL AFTER `checkpoint`,
  ADD COLUMN `progress_updated_at` datetime(6) DEFAULT NULL AFTER `heartbeat_at`,
  ADD COLUMN `error_summary` varchar(1000) DEFAULT NULL AFTER `progress_updated_at`,
  ADD COLUMN `queued_at` datetime(6) DEFAULT NULL AFTER `error_summary`;

-- Phase 0 及更早版本使用 NAS 的 Asia/Shanghai 本地时间写入无时区 datetime；
-- Phase 1 起统一存储 UTC，因此先把既有运行时间平移到 UTC。
UPDATE `crawler_task_log`
SET `started_at` = CASE
      WHEN `started_at` IS NULL THEN NULL
      ELSE DATE_SUB(`started_at`, INTERVAL 8 HOUR)
    END,
    `finished_at` = CASE
      WHEN `finished_at` IS NULL THEN NULL
      ELSE DATE_SUB(`finished_at`, INTERVAL 8 HOUR)
    END;

UPDATE `crawler_task_log`
SET `finished_at` = CASE
    WHEN LOWER(`status`) IN ('running', 'pending_retry') THEN COALESCE(`finished_at`, UTC_TIMESTAMP(6))
    ELSE `finished_at`
  END,
  `status` = CASE LOWER(`status`)
    WHEN 'running' THEN 'interrupted'
    WHEN 'stopped' THEN 'cancelled'
    WHEN 'pending_retry' THEN 'interrupted'
    WHEN 'partial_success' THEN 'partial_success'
    WHEN 'success' THEN 'success'
    WHEN 'failed' THEN 'failed'
    WHEN 'cancelled' THEN 'cancelled'
    WHEN 'interrupted' THEN 'interrupted'
    ELSE 'failed'
  END,
  `cancel_requested` = 0,
  `discovered_count` = COALESCE(`items_crawled`, 0),
  `fetch_succeeded_count` = COALESCE(`items_crawled`, 0),
  `parse_succeeded_count` = COALESCE(`items_added`, 0) + COALESCE(`items_updated`, 0),
  `added_count` = COALESCE(`items_added`, 0),
  `updated_count` = COALESCE(`items_updated`, 0),
  `error_summary` = LEFT(`error_message`, 1000),
  `queued_at` = COALESCE(`started_at`, UTC_TIMESTAMP(6));

UPDATE `crawler_task_log` job
LEFT JOIN `crawler_schedule` schedule ON schedule.`id` = job.`schedule_id`
SET job.`source_code` = COALESCE(NULLIF(schedule.`source_site`, ''), 'pkmp4'),
    job.`crawl_mode` = COALESCE(NULLIF(schedule.`crawl_mode`, ''), 'incremental'),
    job.`trigger_type` = 'legacy';

-- 数据迁移后的自动调度按已确认决策保持关闭，部署验收后再明确启用。
UPDATE `crawler_schedule`
SET `enabled` = 0,
    `next_run_time` = NULL,
    `status` = 'idle',
    `last_run_time` = CASE
      WHEN `last_run_time` IS NULL THEN NULL
      ELSE DATE_SUB(`last_run_time`, INTERVAL 8 HOUR)
    END;

ALTER TABLE `crawler_task_log`
  MODIFY COLUMN `queued_at` datetime(6) NOT NULL,
  ADD COLUMN `active_schedule_id` bigint unsigned
    GENERATED ALWAYS AS (
      CASE
        WHEN `status` IN ('queued', 'running', 'cancel_requested') THEN `schedule_id`
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY `uk_crawler_job_active_schedule` (`active_schedule_id`),
  ADD KEY `idx_crawler_job_status_heartbeat` (`status`, `heartbeat_at`),
  ADD KEY `idx_crawler_job_schedule_queued` (`schedule_id`, `queued_at`),
  ADD KEY `idx_crawler_job_retry_of` (`retry_of_job_id`),
  ADD CONSTRAINT `chk_crawler_job_status`
    CHECK (`status` IN (
      'queued', 'running', 'cancel_requested', 'success',
      'partial_success', 'failed', 'cancelled', 'interrupted'
    )),
  ADD CONSTRAINT `chk_crawler_job_cancel_requested`
    CHECK (`cancel_requested` IN (0, 1));
