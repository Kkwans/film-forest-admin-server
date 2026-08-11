-- Phase 10: 为登录用户保存可信、可跨设备恢复的最近播放位置。

CREATE TABLE `user_playback_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `resource_online_id` bigint unsigned DEFAULT NULL,
  `episode_number` int DEFAULT NULL,
  `episode_title` varchar(200) DEFAULT NULL,
  `source_name` varchar(50) DEFAULT NULL,
  `playback_type` varchar(20) DEFAULT NULL,
  `position_seconds` bigint unsigned NOT NULL DEFAULT 0,
  `duration_seconds` bigint unsigned DEFAULT NULL,
  `completed` tinyint NOT NULL DEFAULT 0,
  `last_played_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_playback_content` (`user_id`, `content_type`, `content_id`),
  KEY `idx_user_playback_recent` (`user_id`, `last_played_at`, `id`),
  KEY `idx_user_playback_resource` (`resource_online_id`),
  CONSTRAINT `fk_user_playback_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_playback_resource`
    FOREIGN KEY (`resource_online_id`) REFERENCES `resource_online` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_user_playback_content_type`
    CHECK (`content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')),
  CONSTRAINT `chk_user_playback_episode`
    CHECK (`episode_number` IS NULL OR `episode_number` > 0),
  CONSTRAINT `chk_user_playback_position`
    CHECK (`position_seconds` BETWEEN 0 AND 604800),
  CONSTRAINT `chk_user_playback_completed`
    CHECK (`completed` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
