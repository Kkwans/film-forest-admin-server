ALTER TABLE `user`
  ADD COLUMN `password_algorithm` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY_SHA256' AFTER `password_hash`,
  ADD COLUMN `must_change_password` tinyint NOT NULL DEFAULT '0' AFTER `password_algorithm`,
  ADD COLUMN `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' AFTER `status`,
  ADD CONSTRAINT `chk_user_password_algorithm`
    CHECK (`password_algorithm` IN ('LEGACY_SHA256', 'BCRYPT')),
  ADD CONSTRAINT `chk_user_must_change_password`
    CHECK (`must_change_password` IN (0, 1)),
  ADD CONSTRAINT `chk_user_role`
    CHECK (`role` IN ('USER', 'ADMIN'));

UPDATE `user`
SET `password_algorithm` = 'BCRYPT'
WHERE CHAR_LENGTH(`password_hash`) = 60
  AND (`password_hash` LIKE '$2a$%'
    OR `password_hash` LIKE '$2b$%'
    OR `password_hash` LIKE '$2y$%');

UPDATE `user`
SET `role` = 'ADMIN'
WHERE `username` = 'admin'
  AND `is_deleted` = 0;
