CREATE TABLE `tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `color` varchar(20) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `usage_count` int unsigned NOT NULL DEFAULT 0,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  `active_name` varchar(100)
      GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END) STORED,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
      ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_active_name` (`active_name`),
  KEY `idx_tag_active_usage` (`is_deleted`, `usage_count`, `sort_order`, `id`),
  CONSTRAINT `chk_tag_deleted` CHECK (`is_deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `content_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `content_id` bigint unsigned NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_tag` (`content_type`, `content_id`, `tag_id`),
  KEY `idx_content_tag_filter` (`tag_id`, `content_type`, `content_id`),
  CONSTRAINT `fk_content_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`)
      ON DELETE CASCADE,
  CONSTRAINT `chk_content_tag_type` CHECK (
      `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
