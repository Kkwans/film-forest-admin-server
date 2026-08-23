-- 将当前影片解析阶段持久化，供管理端实时展示；不修改历史 Job 结果。
ALTER TABLE `crawler_task_log`
  ADD COLUMN `current_item_title` varchar(200) DEFAULT NULL AFTER `current_item`,
  ADD COLUMN `current_stage` varchar(32) DEFAULT NULL AFTER `current_item_title`,
  ADD COLUMN `current_stage_progress` tinyint unsigned DEFAULT NULL AFTER `current_stage`,
  ADD COLUMN `current_stage_message` varchar(255) DEFAULT NULL AFTER `current_stage_progress`;
