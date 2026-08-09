-- 管理端布局偏好跟随管理员账号，在刷新和更换设备后保持一致。

ALTER TABLE `user`
  ADD COLUMN `admin_sidebar_collapsed` tinyint NOT NULL DEFAULT 0 AFTER `avatar_url`,
  ADD CONSTRAINT `chk_user_admin_sidebar_collapsed`
    CHECK (`admin_sidebar_collapsed` IN (0, 1));
