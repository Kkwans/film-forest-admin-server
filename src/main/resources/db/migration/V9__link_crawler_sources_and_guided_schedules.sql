-- Phase 8: 来源—适配器关系、人性化计划模型和标准题材筛选。

ALTER TABLE `resource_source`
  ADD COLUMN `code` varchar(50) DEFAULT NULL AFTER `id`;

-- 历史库可能完全没有来源记录；先建立唯一、稳定的七味网生产来源。
INSERT INTO `resource_source` (`name`, `url`, `enabled`, `sort`, `code`)
SELECT '七味网', 'https://www.pkmp4.xyz/', 1, 10, 'pkmp4'
WHERE NOT EXISTS (
  SELECT 1 FROM `resource_source`
  WHERE `name` = '七味网' OR `url` LIKE '%pkmp4%'
);

-- 多条历史七味网记录只选择最早一条作为生产来源，避免唯一 code 迁移失败。
UPDATE `resource_source` source
JOIN (
  SELECT MIN(`id`) AS `canonical_pkmp4_id`
  FROM `resource_source`
  WHERE `name` = '七味网' OR `url` LIKE '%pkmp4%'
) canonical ON 1 = 1
SET source.`code` = CASE
      WHEN source.`id` = canonical.`canonical_pkmp4_id` THEN 'pkmp4'
      WHEN source.`name` = '天堂资源' THEN CONCAT('tiantang-', source.`id`)
      WHEN source.`name` = '非凡资源' THEN CONCAT('feifan-', source.`id`)
      ELSE CONCAT('source-', source.`id`)
    END,
    source.`url` = CASE
      WHEN source.`id` = canonical.`canonical_pkmp4_id`
        THEN COALESCE(NULLIF(source.`url`, ''), 'https://www.pkmp4.xyz/')
      ELSE source.`url`
    END;

-- 本阶段只启用七味网；其余来源保留为明确禁用的扩展位。
UPDATE `resource_source`
SET `enabled` = CASE WHEN `code` = 'pkmp4' THEN 1 ELSE 0 END;

ALTER TABLE `resource_source`
  MODIFY COLUMN `code` varchar(50) NOT NULL,
  ADD UNIQUE KEY `uk_resource_source_code` (`code`),
  ADD CONSTRAINT `chk_resource_source_enabled` CHECK (`enabled` IN (0, 1));

CREATE TABLE `crawler_source_adapter` (
  `source_id` bigint unsigned NOT NULL,
  `adapter_code` varchar(50) NOT NULL,
  `content_type` varchar(20) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`source_id`, `adapter_code`, `content_type`),
  KEY `idx_crawler_adapter_lookup` (`adapter_code`, `content_type`, `enabled`),
  CONSTRAINT `fk_crawler_adapter_source` FOREIGN KEY (`source_id`) REFERENCES `resource_source` (`id`),
  CONSTRAINT `chk_crawler_adapter_type` CHECK (
    `content_type` IN ('movie', 'drama', 'variety', 'anime', 'short_drama')
  ),
  CONSTRAINT `chk_crawler_adapter_enabled` CHECK (`enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `crawler_source_adapter` (`source_id`, `adapter_code`, `content_type`, `enabled`)
SELECT source.`id`, 'pkmp4', types.`content_type`, 1
FROM `resource_source` source
JOIN (
  SELECT 'movie' AS `content_type` UNION ALL
  SELECT 'drama' UNION ALL
  SELECT 'variety' UNION ALL
  SELECT 'anime' UNION ALL
  SELECT 'short_drama'
) types
WHERE source.`code` = 'pkmp4';

UPDATE `crawler_schedule`
SET `content_type` = 'short_drama'
WHERE `content_type` = 'short';

ALTER TABLE `crawler_schedule`
  MODIFY COLUMN `cron_expression` varchar(100) DEFAULT NULL,
  ADD COLUMN `source_id` bigint unsigned DEFAULT NULL AFTER `source_site`,
  ADD COLUMN `adapter_code` varchar(50) DEFAULT NULL AFTER `source_id`,
  ADD COLUMN `schedule_mode` varchar(30) DEFAULT NULL AFTER `cron_expression`,
  ADD COLUMN `schedule_config` json DEFAULT NULL AFTER `schedule_mode`,
  ADD COLUMN `timezone` varchar(50) DEFAULT NULL AFTER `schedule_config`;

UPDATE `crawler_schedule` schedule
JOIN `resource_source` source
  ON source.`code` = 'pkmp4'
SET schedule.`source_id` = source.`id`,
    schedule.`adapter_code` = 'pkmp4',
    schedule.`source_site` = 'pkmp4',
    schedule.`schedule_mode` = CASE
      WHEN schedule.`cron_expression` IS NULL OR schedule.`cron_expression` = '' THEN 'MANUAL'
      ELSE 'CUSTOM_CRON'
    END,
    schedule.`schedule_config` = CASE
      WHEN schedule.`cron_expression` IS NULL OR schedule.`cron_expression` = '' THEN JSON_OBJECT()
      ELSE JSON_OBJECT('cronExpression', schedule.`cron_expression`)
    END,
    schedule.`timezone` = 'Asia/Shanghai';

ALTER TABLE `crawler_schedule`
  MODIFY COLUMN `source_id` bigint unsigned NOT NULL,
  MODIFY COLUMN `adapter_code` varchar(50) NOT NULL,
  MODIFY COLUMN `schedule_mode` varchar(30) NOT NULL DEFAULT 'MANUAL',
  MODIFY COLUMN `schedule_config` json NOT NULL,
  MODIFY COLUMN `timezone` varchar(50) NOT NULL DEFAULT 'Asia/Shanghai',
  ADD KEY `idx_crawler_schedule_source` (`source_id`, `adapter_code`, `content_type`),
  ADD CONSTRAINT `fk_crawler_schedule_source` FOREIGN KEY (`source_id`) REFERENCES `resource_source` (`id`),
  ADD CONSTRAINT `fk_crawler_schedule_adapter` FOREIGN KEY (`source_id`, `adapter_code`, `content_type`)
    REFERENCES `crawler_source_adapter` (`source_id`, `adapter_code`, `content_type`),
  ADD CONSTRAINT `chk_crawler_schedule_mode` CHECK (
    `schedule_mode` IN ('MANUAL', 'INTERVAL', 'DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM_CRON')
  );

CREATE TABLE `crawler_schedule_genre` (
  `schedule_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`schedule_id`, `tag_id`),
  KEY `idx_crawler_schedule_genre_tag` (`tag_id`, `schedule_id`),
  CONSTRAINT `fk_crawler_schedule_genre_schedule` FOREIGN KEY (`schedule_id`)
    REFERENCES `crawler_schedule` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crawler_schedule_genre_tag` FOREIGN KEY (`tag_id`)
    REFERENCES `tag` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 只迁移能匹配当前内容类型标准题材的历史值；语言、地区和未知自由文本不会进入新关系。
INSERT INTO `crawler_schedule_genre` (`schedule_id`, `tag_id`)
SELECT DISTINCT schedule.`id`, tag.`id`
FROM `crawler_schedule` schedule
JOIN JSON_TABLE(
  COALESCE(schedule.`genre_filter`, JSON_ARRAY()),
  '$[*]' COLUMNS (`genre_name` varchar(100) PATH '$')
) legacy_genre
JOIN `tag` tag ON tag.`name` = legacy_genre.`genre_name` AND tag.`is_system` = 1
JOIN `tag_content_type` tag_type
  ON tag_type.`tag_id` = tag.`id` AND tag_type.`content_type` = schedule.`content_type`;
