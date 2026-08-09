-- Phase 9: 为高频目录、资源运营、爬虫队列和通知列表建立与过滤/排序一致的复合索引。
-- 仅增加索引，不修改业务数据；索引末尾包含主键以保证游标/分页顺序稳定。

ALTER TABLE `movie`
  ADD KEY `idx_movie_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`);
ALTER TABLE `drama`
  ADD KEY `idx_drama_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`);
ALTER TABLE `variety`
  ADD KEY `idx_variety_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`);
ALTER TABLE `anime`
  ADD KEY `idx_anime_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`);
ALTER TABLE `short_drama`
  ADD KEY `idx_short_drama_public_catalog` (`is_deleted`, `status`, `updated_at`, `id`);

ALTER TABLE `resource_online`
  ADD KEY `idx_resource_online_recent` (`is_deleted`, `created_at`, `id`),
  ADD KEY `idx_resource_online_active_recent`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `created_at`, `id`);
ALTER TABLE `resource_magnet`
  ADD KEY `idx_resource_magnet_recent` (`is_deleted`, `created_at`, `id`),
  ADD KEY `idx_resource_magnet_active_recent`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `created_at`, `id`);
ALTER TABLE `resource_cloud`
  ADD KEY `idx_resource_cloud_recent` (`is_deleted`, `created_at`, `id`),
  ADD KEY `idx_resource_cloud_active_recent`
    (`is_deleted`, `enabled`, `removed_at`, `content_type`, `created_at`, `id`);

ALTER TABLE `crawler_task_log`
  ADD KEY `idx_crawler_job_queue` (`status`, `queued_at`, `id`),
  ADD KEY `idx_crawler_job_page` (`queued_at`, `id`);

ALTER TABLE `admin_notification`
  ADD KEY `idx_admin_notification_recent` (`user_id`, `created_at`, `id`);
