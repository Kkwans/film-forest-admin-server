-- 资源抓取范围默认为下载资源，避免定时增量任务为很少使用的在线播放逐页请求。
-- 在线播放仍可通过单独的 ONLINE 配置启用，ALL 保留一次抓取全部资源的场景。
ALTER TABLE `crawler_schedule`
  ADD COLUMN `resource_scope` varchar(20) NOT NULL DEFAULT 'DOWNLOADS' AFTER `crawl_mode`;

UPDATE `crawler_schedule`
SET `resource_scope` = 'DOWNLOADS'
WHERE `resource_scope` IS NULL OR `resource_scope` = '';

ALTER TABLE `crawler_task_log`
  ADD COLUMN `resource_scope` varchar(20) NOT NULL DEFAULT 'DOWNLOADS' AFTER `crawl_mode`;

UPDATE `crawler_task_log` job
LEFT JOIN `crawler_schedule` schedule ON schedule.`id` = job.`schedule_id`
SET job.`resource_scope` = COALESCE(NULLIF(schedule.`resource_scope`, ''), 'DOWNLOADS')
WHERE job.`resource_scope` IS NULL OR job.`resource_scope` = '';

ALTER TABLE `resource_magnet`
  ADD COLUMN `size_bytes` bigint unsigned DEFAULT NULL AFTER `magnet_url`;

-- 回滚边界：保留新增列和已有数据；应用回滚后仍可读取默认 DOWNLOADS，
-- 不自动删除列，避免破坏已入库的磁力大小和任务快照。
