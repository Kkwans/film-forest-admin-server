CREATE TABLE `content_poster_match` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `content_type` varchar(20) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `source_poster_url` varchar(500) DEFAULT NULL,
  `tmdb_media_type` varchar(10) DEFAULT NULL,
  `tmdb_id` bigint unsigned DEFAULT NULL,
  `poster_path` varchar(255) DEFAULT NULL,
  `poster_language` varchar(10) DEFAULT NULL,
  `confidence` decimal(5,4) DEFAULT NULL,
  `match_status` varchar(20) NOT NULL DEFAULT 'pending',
  `diagnostic` json DEFAULT NULL,
  `matched_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_poster_match` (`content_type`, `content_id`),
  KEY `idx_content_poster_tmdb` (`tmdb_media_type`, `tmdb_id`),
  KEY `idx_content_poster_status` (`match_status`, `updated_at`),
  CONSTRAINT `chk_content_poster_type`
    CHECK (`content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')),
  CONSTRAINT `chk_content_poster_media_type`
    CHECK (`tmdb_media_type` IS NULL OR `tmdb_media_type` IN ('movie', 'tv')),
  CONSTRAINT `chk_content_poster_status`
    CHECK (`match_status` IN ('pending', 'accepted', 'rejected', 'not_found', 'error')),
  CONSTRAINT `chk_content_poster_confidence`
    CHECK (`confidence` IS NULL OR (`confidence` >= 0 AND `confidence` <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
