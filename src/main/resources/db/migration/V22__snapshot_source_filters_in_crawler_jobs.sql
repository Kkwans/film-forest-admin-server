ALTER TABLE `crawler_task_log`
  ADD COLUMN `source_filter_snapshot` text NULL AFTER `query_snapshot`;
