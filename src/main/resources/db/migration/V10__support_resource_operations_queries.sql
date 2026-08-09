-- Phase 8: 统一资源运营状态、内容类型和管理端分页索引。

ALTER TABLE `resource_online`
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 AFTER `removed_at`,
  ADD CONSTRAINT `chk_resource_online_enabled` CHECK (`enabled` IN (0, 1)),
  ADD KEY `idx_resource_online_admin_list`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `source_code`, `created_at`);

ALTER TABLE `resource_magnet`
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 AFTER `removed_at`,
  ADD CONSTRAINT `chk_resource_magnet_enabled` CHECK (`enabled` IN (0, 1)),
  ADD KEY `idx_resource_magnet_admin_list`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `source_code`, `created_at`);

ALTER TABLE `resource_cloud`
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 AFTER `removed_at`,
  ADD CONSTRAINT `chk_resource_cloud_enabled` CHECK (`enabled` IN (0, 1)),
  ADD KEY `idx_resource_cloud_admin_list`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `source_code`, `created_at`);

UPDATE `resource_online` SET `content_type` = 'short_drama' WHERE `content_type` = 'short';
UPDATE `resource_magnet` SET `content_type` = 'short_drama' WHERE `content_type` = 'short';
UPDATE `resource_cloud` SET `content_type` = 'short_drama' WHERE `content_type` = 'short';
