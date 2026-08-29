-- Search analytics is consumed by the public hot-search endpoint and by the
-- web search write path.  Keep this table append-only and bounded to the
-- fields needed by those two contracts.
CREATE TABLE IF NOT EXISTS `search_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `keyword` varchar(200) NOT NULL,
  `result_count` int unsigned NOT NULL DEFAULT 0,
  `source` varchar(20) NOT NULL DEFAULT 'web',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_search_log_created_keyword` (`created_at`, `keyword`),
  KEY `idx_search_log_keyword_created` (`keyword`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
