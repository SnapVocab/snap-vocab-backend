-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: USER SETTINGS & AVATAR OBJECT KEY
-- Mục đích:
--   1. Tạo bảng `user_settings` lưu tùy chọn của từng Learner (MH-PROFILE-03)
--   2. Chuyển `users.avatar_url` từ presigned URL (hết hạn) sang object key
-- Ghi chú:
--   Môi trường dùng `ddl-auto: update` sẽ tự tạo bảng từ entity UserSettings;
--   script này dành cho môi trường quản lý schema thủ công. BƯỚC 2 vẫn nên chạy
--   ở mọi môi trường vì Hibernate không chuyển đổi dữ liệu sẵn có.
-- ============================================================================

-- BƯỚC 1: Bảng cài đặt người dùng (1 Learner - 1 dòng)
CREATE TABLE IF NOT EXISTS `user_settings` (
    `user_id` BIGINT(20) NOT NULL,
    `ui_language` VARCHAR(10) NOT NULL DEFAULT 'vi',
    `theme` VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    `daily_goal` INT NOT NULL DEFAULT 20,
    `srs_reminder_at` TIME(6) NULL,
    `push_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_settings_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BƯỚC 2: avatar_url giờ lưu object key thay vì presigned URL.
-- URL cũ có dạng https://<host>/<bucket>/<object-key>?X-Amz-...  -> lấy phần
-- object key: bỏ query string, rồi bỏ phần đường dẫn trước object key.
-- Đổi 'snapvocab' thành tên bucket của môi trường nếu khác.
UPDATE `users`
SET `avatar_url` = SUBSTRING_INDEX(SUBSTRING_INDEX(`avatar_url`, '?', 1), '/snapvocab/', -1)
WHERE `avatar_url` LIKE 'http%/snapvocab/%';

-- Ảnh cũ không nằm trong bucket hiện tại thì không suy ra được key; xóa đi để
-- người dùng tải lại avatar, thay vì hiển thị một URL đã hết hạn.
UPDATE `users`
SET `avatar_url` = NULL
WHERE `avatar_url` LIKE 'http%';
