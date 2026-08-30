-- 为用户片单分页排序提供覆盖前缀索引。
-- 回滚边界：索引为 additive；应用回滚时保留索引，确需移除时另行创建
-- forward migration，不修改已执行的历史迁移。

ALTER TABLE `user_movie_list_item`
  ADD KEY `idx_item_list_added_at_id` (`list_id`, `added_at`, `id`),
  ADD KEY `idx_item_list_rating_id` (`list_id`, `rating`, `id`);
