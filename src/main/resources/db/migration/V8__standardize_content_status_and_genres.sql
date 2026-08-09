-- Phase 8: 内容三态与标准题材基础。
-- 兼容策略：旧的 status=0 表示“未上线”，迁移为 OFFLINE(2)；新建内容默认 DRAFT(0)。

UPDATE `movie` SET `status` = 2 WHERE `status` = 0;
UPDATE `drama` SET `status` = 2 WHERE `status` = 0;
UPDATE `variety` SET `status` = 2 WHERE `status` = 0;
UPDATE `anime` SET `status` = 2 WHERE `status` = 0;
UPDATE `short_drama` SET `status` = 2 WHERE `status` = 0;

ALTER TABLE `movie`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_movie_content_status` CHECK (`status` IN (0, 1, 2));
ALTER TABLE `drama`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_drama_content_status` CHECK (`status` IN (0, 1, 2));
ALTER TABLE `variety`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_variety_content_status` CHECK (`status` IN (0, 1, 2));
ALTER TABLE `anime`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_anime_content_status` CHECK (`status` IN (0, 1, 2));
ALTER TABLE `short_drama`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0,
  ADD CONSTRAINT `chk_short_drama_content_status` CHECK (`status` IN (0, 1, 2));

ALTER TABLE `tag`
  ADD COLUMN `code` varchar(80) DEFAULT NULL AFTER `id`,
  ADD COLUMN `is_system` tinyint NOT NULL DEFAULT 0 AFTER `usage_count`,
  ADD CONSTRAINT `chk_tag_system` CHECK (`is_system` IN (0, 1));

UPDATE `tag`
SET `code` = CONCAT('custom-', `id`)
WHERE `code` IS NULL;

ALTER TABLE `tag`
  MODIFY COLUMN `code` varchar(80) NOT NULL,
  ADD UNIQUE KEY `uk_tag_code` (`code`),
  ADD KEY `idx_tag_system_order` (`is_system`, `sort_order`, `id`);

CREATE TABLE `tag_content_type` (
  `tag_id` bigint unsigned NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`tag_id`, `content_type`),
  KEY `idx_tag_content_type_lookup` (`content_type`, `tag_id`),
  CONSTRAINT `fk_tag_content_type_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_tag_content_type_value` CHECK (
    `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tag_source_alias` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tag_id` bigint unsigned NOT NULL,
  `source_code` varchar(50) NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `alias` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_source_alias` (`source_code`, `content_type`, `alias`),
  KEY `idx_tag_source_alias_tag` (`tag_id`, `content_type`),
  CONSTRAINT `fk_tag_source_alias_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_tag_source_alias_type` CHECK (
    `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `tag` (`name`, `code`, `color`, `sort_order`, `usage_count`, `is_system`)
VALUES
  ('剧情', 'drama', '#4f7664', 10, 0, 1),
  ('喜剧', 'comedy', '#c58c3d', 20, 0, 1),
  ('动作', 'action', '#ad5a55', 30, 0, 1),
  ('爱情', 'romance', '#b45f7f', 40, 0, 1),
  ('科幻', 'science-fiction', '#557da8', 50, 0, 1),
  ('动画', 'animation', '#7b69a7', 60, 0, 1),
  ('悬疑', 'mystery', '#606d75', 70, 0, 1),
  ('惊悚', 'thriller', '#765867', 80, 0, 1),
  ('恐怖', 'horror', '#754742', 90, 0, 1),
  ('犯罪', 'crime', '#6c655c', 100, 0, 1),
  ('冒险', 'adventure', '#4c806e', 110, 0, 1),
  ('奇幻', 'fantasy', '#6a6f9a', 120, 0, 1),
  ('家庭', 'family', '#7c8351', 130, 0, 1),
  ('传记', 'biography', '#8b6d52', 140, 0, 1),
  ('历史', 'history', '#8a725d', 150, 0, 1),
  ('战争', 'war', '#65675d', 160, 0, 1),
  ('音乐', 'music', '#976b8e', 170, 0, 1),
  ('歌舞', 'musical', '#a06e72', 180, 0, 1),
  ('运动', 'sport', '#4d7b80', 190, 0, 1),
  ('西部', 'western', '#8c714d', 200, 0, 1),
  ('武侠', 'wuxia', '#657953', 210, 0, 1),
  ('古装', 'costume', '#80704d', 220, 0, 1),
  ('灾难', 'disaster', '#8b594f', 230, 0, 1),
  ('纪录片', 'documentary', '#4f7480', 240, 0, 1),
  ('短片', 'short-film', '#787878', 250, 0, 1),
  ('真人秀', 'reality-show', '#5c8063', 260, 0, 1),
  ('脱口秀', 'talk-show', '#96764d', 270, 0, 1),
  ('游戏', 'game-show', '#687e50', 280, 0, 1),
  ('亲子', 'parenting', '#a17e5c', 290, 0, 1),
  ('旅行', 'travel', '#4d8080', 300, 0, 1),
  ('美食', 'food', '#a16c4d', 310, 0, 1),
  ('文化', 'culture', '#7d6b50', 320, 0, 1),
  ('竞技', 'competition', '#597a68', 330, 0, 1),
  ('晚会', 'gala', '#986a63', 340, 0, 1),
  ('访谈', 'interview', '#6c7386', 350, 0, 1),
  ('儿童', 'children', '#8b8a52', 360, 0, 1),
  ('校园', 'school', '#5f7b8a', 370, 0, 1),
  ('都市', 'urban', '#647582', 380, 0, 1),
  ('职场', 'workplace', '#68726c', 390, 0, 1)
ON DUPLICATE KEY UPDATE
  `code` = VALUES(`code`),
  `is_system` = 1,
  `sort_order` = VALUES(`sort_order`);

INSERT INTO `tag_content_type` (`tag_id`, `content_type`)
SELECT `tag`.`id`, mapping.`content_type`
FROM `tag`
JOIN (
  SELECT 'drama' AS `tag_code`, 'movie' AS `content_type` UNION ALL
  SELECT 'comedy', 'movie' UNION ALL SELECT 'action', 'movie' UNION ALL SELECT 'romance', 'movie' UNION ALL
  SELECT 'science-fiction', 'movie' UNION ALL SELECT 'animation', 'movie' UNION ALL SELECT 'mystery', 'movie' UNION ALL
  SELECT 'thriller', 'movie' UNION ALL SELECT 'horror', 'movie' UNION ALL SELECT 'crime', 'movie' UNION ALL
  SELECT 'adventure', 'movie' UNION ALL SELECT 'fantasy', 'movie' UNION ALL SELECT 'family', 'movie' UNION ALL
  SELECT 'biography', 'movie' UNION ALL SELECT 'history', 'movie' UNION ALL SELECT 'war', 'movie' UNION ALL
  SELECT 'music', 'movie' UNION ALL SELECT 'musical', 'movie' UNION ALL SELECT 'sport', 'movie' UNION ALL
  SELECT 'western', 'movie' UNION ALL SELECT 'wuxia', 'movie' UNION ALL SELECT 'costume', 'movie' UNION ALL
  SELECT 'disaster', 'movie' UNION ALL SELECT 'documentary', 'movie' UNION ALL SELECT 'short-film', 'movie' UNION ALL

  SELECT 'drama', 'drama' UNION ALL SELECT 'comedy', 'drama' UNION ALL SELECT 'action', 'drama' UNION ALL
  SELECT 'romance', 'drama' UNION ALL SELECT 'science-fiction', 'drama' UNION ALL SELECT 'mystery', 'drama' UNION ALL
  SELECT 'thriller', 'drama' UNION ALL SELECT 'horror', 'drama' UNION ALL SELECT 'crime', 'drama' UNION ALL
  SELECT 'adventure', 'drama' UNION ALL SELECT 'fantasy', 'drama' UNION ALL SELECT 'family', 'drama' UNION ALL
  SELECT 'history', 'drama' UNION ALL SELECT 'war', 'drama' UNION ALL SELECT 'wuxia', 'drama' UNION ALL
  SELECT 'costume', 'drama' UNION ALL SELECT 'school', 'drama' UNION ALL SELECT 'urban', 'drama' UNION ALL
  SELECT 'workplace', 'drama' UNION ALL

  SELECT 'reality-show', 'variety' UNION ALL SELECT 'talk-show', 'variety' UNION ALL SELECT 'music', 'variety' UNION ALL
  SELECT 'musical', 'variety' UNION ALL SELECT 'comedy', 'variety' UNION ALL SELECT 'game-show', 'variety' UNION ALL
  SELECT 'parenting', 'variety' UNION ALL SELECT 'travel', 'variety' UNION ALL SELECT 'food', 'variety' UNION ALL
  SELECT 'culture', 'variety' UNION ALL SELECT 'competition', 'variety' UNION ALL SELECT 'gala', 'variety' UNION ALL
  SELECT 'interview', 'variety' UNION ALL SELECT 'documentary', 'variety' UNION ALL

  SELECT 'animation', 'anime' UNION ALL SELECT 'drama', 'anime' UNION ALL SELECT 'comedy', 'anime' UNION ALL
  SELECT 'action', 'anime' UNION ALL SELECT 'romance', 'anime' UNION ALL SELECT 'science-fiction', 'anime' UNION ALL
  SELECT 'fantasy', 'anime' UNION ALL SELECT 'adventure', 'anime' UNION ALL SELECT 'mystery', 'anime' UNION ALL
  SELECT 'thriller', 'anime' UNION ALL SELECT 'horror', 'anime' UNION ALL SELECT 'family', 'anime' UNION ALL
  SELECT 'children', 'anime' UNION ALL SELECT 'sport', 'anime' UNION ALL SELECT 'school', 'anime' UNION ALL
  SELECT 'history', 'anime' UNION ALL SELECT 'war', 'anime' UNION ALL SELECT 'wuxia', 'anime' UNION ALL

  SELECT 'drama', 'short_drama' UNION ALL SELECT 'comedy', 'short_drama' UNION ALL SELECT 'romance', 'short_drama' UNION ALL
  SELECT 'action', 'short_drama' UNION ALL SELECT 'mystery', 'short_drama' UNION ALL SELECT 'fantasy', 'short_drama' UNION ALL
  SELECT 'costume', 'short_drama' UNION ALL SELECT 'urban', 'short_drama' UNION ALL SELECT 'family', 'short_drama' UNION ALL
  SELECT 'workplace', 'short_drama' UNION ALL SELECT 'school', 'short_drama' UNION ALL SELECT 'science-fiction', 'short_drama'
) mapping ON mapping.`tag_code` = `tag`.`code`;

INSERT INTO `tag_source_alias` (`tag_id`, `source_code`, `content_type`, `alias`)
SELECT `tag`.`id`, aliases.`source_code`, aliases.`content_type`, aliases.`alias`
FROM `tag`
JOIN (
  SELECT 'science-fiction' AS `tag_code`, 'pkmp4' AS `source_code`, 'movie' AS `content_type`, '科幻片' AS `alias` UNION ALL
  SELECT 'documentary', 'pkmp4', 'movie', '纪录' UNION ALL
  SELECT 'animation', 'pkmp4', 'movie', '动画片' UNION ALL
  SELECT 'crime', 'pkmp4', 'movie', '罪案' UNION ALL
  SELECT 'science-fiction', 'pkmp4', 'drama', '科幻剧' UNION ALL
  SELECT 'costume', 'pkmp4', 'drama', '古装剧' UNION ALL
  SELECT 'wuxia', 'pkmp4', 'drama', '武侠剧' UNION ALL
  SELECT 'reality-show', 'pkmp4', 'variety', '真人' UNION ALL
  SELECT 'talk-show', 'pkmp4', 'variety', '脱口' UNION ALL
  SELECT 'animation', 'pkmp4', 'anime', '动漫' UNION ALL
  SELECT 'costume', 'pkmp4', 'short_drama', '古装剧' UNION ALL
  SELECT 'urban', 'pkmp4', 'short_drama', '都市剧'
) aliases ON aliases.`tag_code` = `tag`.`code`;
