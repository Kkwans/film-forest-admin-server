-- Phase 8: 来源条目与站内内容主键解耦，并按规范化标题/年份建立幂等身份。

CREATE TABLE `crawler_content_identity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `content_type` varchar(20) NOT NULL,
  `canonical_key` char(64) NOT NULL,
  `normalized_title` varchar(200) NOT NULL,
  `release_year` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crawler_content_identity` (`content_type`, `canonical_key`),
  KEY `idx_crawler_identity_title_year` (`content_type`, `normalized_title`, `release_year`),
  CONSTRAINT `chk_crawler_identity_type` CHECK (
    `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `crawler_source_item`
  ADD COLUMN `canonical_key` char(64) DEFAULT NULL AFTER `internal_content_id`,
  ADD KEY `idx_source_item_canonical` (`content_type`, `canonical_key`);
