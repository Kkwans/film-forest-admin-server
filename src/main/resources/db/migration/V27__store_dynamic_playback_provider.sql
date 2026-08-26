-- 保存来源播放页提供的动态线路名称（例如“非凡”“量子”）。
-- 历史在线播放记录保持 NULL；不执行历史页面回抓或数据补齐。
ALTER TABLE `resource_online`
  ADD COLUMN `provider_name` varchar(100) DEFAULT NULL AFTER `source_name`;
