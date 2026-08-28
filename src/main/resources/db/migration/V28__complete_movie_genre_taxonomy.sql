-- 补齐电影公开题材：V8 的标准字典遗漏了情色、同性、黑色电影，儿童也未关联到 movie。
-- 本迁移只扩充标准字典、内容类型和来源别名；不猜测或改写现有影片题材。

INSERT INTO `tag` (`name`, `code`, `color`, `sort_order`, `usage_count`, `is_system`)
VALUES
  ('情色', 'erotic', '#a16207', 235, 0, 1),
  ('同性', 'lgbtq', '#8b5f82', 236, 0, 1),
  ('黑色电影', 'film-noir', '#4b5563', 237, 0, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `color` = VALUES(`color`),
  `is_system` = 1,
  `sort_order` = VALUES(`sort_order`);

INSERT IGNORE INTO `tag_content_type` (`tag_id`, `content_type`)
SELECT `id`, 'movie'
FROM `tag`
WHERE `code` IN ('erotic', 'lgbtq', 'film-noir', 'children');

INSERT IGNORE INTO `tag_source_alias` (`tag_id`, `source_code`, `content_type`, `alias`)
SELECT `tag`.`id`, aliases.`source_code`, 'movie', aliases.`alias`
FROM `tag`
JOIN (
  SELECT 'erotic' AS `tag_code`, 'pkmp4' AS `source_code`, '伦理片' AS `alias` UNION ALL
  SELECT 'erotic', 'pkmp4', '伦理' UNION ALL
  SELECT 'erotic', 'pkmp4', '情色片' UNION ALL
  SELECT 'erotic', 'pkmp4', '成人' UNION ALL
  SELECT 'lgbtq', 'pkmp4', '同性恋' UNION ALL
  SELECT 'film-noir', 'pkmp4', '黑色'
) aliases ON aliases.`tag_code` = `tag`.`code`;

-- 回滚边界：仅在这些题材仍无 content_tag 关联时，删除上述别名、movie 类型关系，
-- 再删除 erotic/lgbtq/film-noir 三个 tag；children 为既有题材，不删除。
