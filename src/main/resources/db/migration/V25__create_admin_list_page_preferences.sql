-- 为每个管理员保存列表分页偏好；默认 10 条，避免大列表一次性占满页面。
CREATE TABLE `admin_list_page_preference` (
  `user_id` bigint NOT NULL,
  `crawler_logs_size` int NOT NULL DEFAULT '10',
  `operation_logs_size` int NOT NULL DEFAULT '10',
  `resources_size` int NOT NULL DEFAULT '10',
  `notifications_size` int NOT NULL DEFAULT '10',
  `users_size` int NOT NULL DEFAULT '10',
  `content_size` int NOT NULL DEFAULT '10',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_admin_list_page_preference_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_admin_list_page_preference_sizes`
    CHECK (`crawler_logs_size` > 1 AND `operation_logs_size` > 1 AND `resources_size` > 1
      AND `notifications_size` > 1 AND `users_size` > 1 AND `content_size` > 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
