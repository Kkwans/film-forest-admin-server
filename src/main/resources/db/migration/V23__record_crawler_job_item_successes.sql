-- 记录每个 Job 实际成功处理的内容快照，避免详情页只展示数量或串入其他 Job。
-- 该表是追加式审计数据；应用回滚时保留，不删除历史任务记录。

CREATE TABLE `crawler_job_item_success` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `job_id` bigint unsigned NOT NULL,
  `source_code` varchar(50) NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `external_id` varchar(100) NOT NULL,
  `source_url` varchar(1000) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `result_type` varchar(20) NOT NULL,
  `title` varchar(255) NOT NULL,
  `alias` json DEFAULT NULL,
  `poster_url` varchar(1000) DEFAULT NULL,
  `year` int DEFAULT NULL,
  `directors` json DEFAULT NULL,
  `writers` json DEFAULT NULL,
  `actors` json DEFAULT NULL,
  `genres` json DEFAULT NULL,
  `regions` json DEFAULT NULL,
  `languages` json DEFAULT NULL,
  `release_date` varchar(100) DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `total_episodes` int DEFAULT NULL,
  `score_douban` decimal(4,1) DEFAULT NULL,
  `score_imdb` decimal(4,1) DEFAULT NULL,
  `score_rt` decimal(5,2) DEFAULT NULL,
  `crawled_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawler_job_item_success`
    (`job_id`, `source_code`, `content_type`, `external_id`),
  KEY `idx_crawler_job_item_success_job` (`job_id`, `crawled_at`, `id`),
  KEY `idx_crawler_job_item_success_content` (`content_type`, `content_id`),
  CONSTRAINT `fk_crawler_job_item_success_job`
    FOREIGN KEY (`job_id`) REFERENCES `crawler_task_log` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_crawler_job_item_success_result`
    CHECK (`result_type` IN ('ADDED', 'UPDATED', 'UNCHANGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
