-- 将当前影片年份随实时解析进度持久化，供管理端在标题后展示；历史 Job 保持可空。
ALTER TABLE `crawler_task_log`
  ADD COLUMN `current_item_year` smallint unsigned DEFAULT NULL AFTER `current_item_title`;
