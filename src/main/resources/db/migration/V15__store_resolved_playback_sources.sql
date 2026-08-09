-- 区分真实播放地址与来源站页面，使用户端可内嵌播放并保留明确外跳降级。

ALTER TABLE `resource_online`
  ADD COLUMN `source_page_url` varchar(1000) DEFAULT NULL AFTER `source_url`,
  ADD COLUMN `playback_type` varchar(20) NOT NULL DEFAULT 'EXTERNAL_PAGE' AFTER `source_page_url`,
  ADD CONSTRAINT `chk_resource_online_playback_type`
    CHECK (`playback_type` IN ('HLS', 'VIDEO', 'EMBED', 'EXTERNAL_PAGE'));

UPDATE `resource_online`
SET `source_page_url` = `source_url`, `playback_type` = 'EXTERNAL_PAGE'
WHERE `source_url` LIKE 'https://www.pkmp4.xyz/py/%'
   OR `source_url` LIKE 'https://pkmp4.xyz/py/%';
