-- 与五类内容表的海报 URL 列保持相同排序规则，确保按当前 source_url 关联失败状态。
ALTER TABLE `content_poster_backup_failure`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
