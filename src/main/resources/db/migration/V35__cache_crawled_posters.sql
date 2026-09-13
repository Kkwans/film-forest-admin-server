-- 为五类内容保留爬虫原始海报地址，并记录本地文件对应的来源。
-- poster_url 继续作为现有 API 的展示字段：备份成功后指向本地 assets endpoint。
-- 备份失败时保留当前 poster_url，避免来源挂掉时覆盖已经可用的本地图片。

ALTER TABLE `movie`
  MODIFY COLUMN `poster_url` varchar(1000) DEFAULT NULL,
  ADD COLUMN `poster_source_url` varchar(1000) DEFAULT NULL AFTER `poster_url`,
  ADD COLUMN `poster_backup_source_url` varchar(1000) DEFAULT NULL AFTER `poster_source_url`;

ALTER TABLE `drama`
  MODIFY COLUMN `poster_url` varchar(1000) DEFAULT NULL,
  ADD COLUMN `poster_source_url` varchar(1000) DEFAULT NULL AFTER `poster_url`,
  ADD COLUMN `poster_backup_source_url` varchar(1000) DEFAULT NULL AFTER `poster_source_url`;

ALTER TABLE `variety`
  MODIFY COLUMN `poster_url` varchar(1000) DEFAULT NULL,
  ADD COLUMN `poster_source_url` varchar(1000) DEFAULT NULL AFTER `poster_url`,
  ADD COLUMN `poster_backup_source_url` varchar(1000) DEFAULT NULL AFTER `poster_source_url`;

ALTER TABLE `anime`
  MODIFY COLUMN `poster_url` varchar(1000) DEFAULT NULL,
  ADD COLUMN `poster_source_url` varchar(1000) DEFAULT NULL AFTER `poster_url`,
  ADD COLUMN `poster_backup_source_url` varchar(1000) DEFAULT NULL AFTER `poster_source_url`;

ALTER TABLE `short_drama`
  MODIFY COLUMN `poster_url` varchar(1000) DEFAULT NULL,
  ADD COLUMN `poster_source_url` varchar(1000) DEFAULT NULL AFTER `poster_url`,
  ADD COLUMN `poster_backup_source_url` varchar(1000) DEFAULT NULL AFTER `poster_source_url`;

UPDATE `movie`
   SET `poster_source_url` = `poster_url`
 WHERE `poster_source_url` IS NULL
   AND `poster_url` IS NOT NULL
   AND `poster_url` <> '';

UPDATE `drama`
   SET `poster_source_url` = `poster_url`
 WHERE `poster_source_url` IS NULL
   AND `poster_url` IS NOT NULL
   AND `poster_url` <> '';

UPDATE `variety`
   SET `poster_source_url` = `poster_url`
 WHERE `poster_source_url` IS NULL
   AND `poster_url` IS NOT NULL
   AND `poster_url` <> '';

UPDATE `anime`
   SET `poster_source_url` = `poster_url`
 WHERE `poster_source_url` IS NULL
   AND `poster_url` IS NOT NULL
   AND `poster_url` <> '';

UPDATE `short_drama`
   SET `poster_source_url` = `poster_url`
 WHERE `poster_source_url` IS NULL
   AND `poster_url` IS NOT NULL
   AND `poster_url` <> '';
