-- 七味网详情页使用“情色”原词；V28 只覆盖了“伦理片”等别名，导致该题材无法入库。
INSERT IGNORE INTO `tag_source_alias` (`tag_id`, `source_code`, `content_type`, `alias`)
SELECT `id`, 'pkmp4', 'movie', '情色'
FROM `tag`
WHERE `code` = 'erotic';

-- 回滚边界：仅移除 source_code=pkmp4、content_type=movie、alias=情色 的别名行，
-- 不删除标准题材或已有内容题材关联。
