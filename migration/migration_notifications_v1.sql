-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: NOTIFICATIONS — v1
-- ============================================================================
-- Traceability:
--   SS-15 Notification: docs/decisions/notification.md §4.1 (schema)
--
-- Mục đích:
--   1. notifications: type NOT NULL (CHECK enum), content LONGTEXT, thêm route + dedupe_key (UNIQUE), index created_at.
--   2. user_notifications: index cho list theo cursor và đếm chưa đọc.
--   3. Tạo bảng device_tokens (FK ON DELETE CASCADE).
--
-- Ghi chú:
--   - Kiểu cột khớp DDL Hibernate sinh từ entity vn.ptit.snapvocab.notification.domain.*.
--   - ddl-auto: update KHÔNG đổi cột đã có: bảng cũ còn `content` TINYTEXT (tối đa 255 byte, @Lob mặc định)
--     và `type` VARCHAR(50) NULL. Vì vậy phải chạy script ở mọi môi trường.
--   - Idempotent: mỗi ADD chỉ chạy khi cột/index/CHECK chưa có, vì ddl-auto: update có thể đã tự thêm
--     route, dedupe_key, index và bảng device_tokens trước khi script chạy.
--   - Type ngoài enum (nếu có) được đổi thành SYSTEM trước khi thêm CHECK.
--   - Phải chạy trước khi deploy lên môi trường ddl-auto: validate (staging). Yêu cầu bảng users(id).
-- ============================================================================

DROP PROCEDURE IF EXISTS `notif_v1_ddl`;
DELIMITER //
CREATE PROCEDURE `notif_v1_ddl`(IN `missing` BOOLEAN, IN `ddl` VARCHAR(500))
BEGIN
    IF `missing` THEN
        SET @notif_v1_ddl = `ddl`;
        PREPARE stmt FROM @notif_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- BƯỚC 1: notifications
UPDATE `notifications` SET `type` = 'SYSTEM' WHERE `type` IS NULL OR `type` NOT IN ('SRS_REMINDER', 'SYSTEM');

ALTER TABLE `notifications`
    MODIFY COLUMN `content` LONGTEXT NOT NULL,
    MODIFY COLUMN `type` VARCHAR(30) NOT NULL;

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'route'),
    'ALTER TABLE `notifications` ADD COLUMN `route` VARCHAR(100) NULL AFTER `type`');

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'dedupe_key'),
    'ALTER TABLE `notifications` ADD COLUMN `dedupe_key` VARCHAR(120) NULL AFTER `route`');

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'uq_notifications_dedupe_key'),
    'ALTER TABLE `notifications` ADD CONSTRAINT `uq_notifications_dedupe_key` UNIQUE (`dedupe_key`)');

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notifications_created_at'),
    'ALTER TABLE `notifications` ADD INDEX `idx_notifications_created_at` (`created_at`)');

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'notifications' AND CONSTRAINT_TYPE = 'CHECK'),
    'ALTER TABLE `notifications` ADD CHECK (`type` IN (''SRS_REMINDER'', ''SYSTEM''))');

-- BƯỚC 2: user_notifications (cột giữ nguyên)
CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user_notifications' AND INDEX_NAME = 'idx_un_user_id'),
    'ALTER TABLE `user_notifications` ADD INDEX `idx_un_user_id` (`user_id`, `id`)');

CALL notif_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user_notifications' AND INDEX_NAME = 'idx_un_user_read'),
    'ALTER TABLE `user_notifications` ADD INDEX `idx_un_user_read` (`user_id`, `is_read`)');

-- BƯỚC 3: device_tokens
CREATE TABLE IF NOT EXISTS `device_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `token` VARCHAR(255) NOT NULL,
    `platform` VARCHAR(10) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `last_seen_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_device_tokens_token` (`token`),
    KEY `idx_device_tokens_user` (`user_id`),
    KEY `idx_device_tokens_last_seen` (`last_seen_at`),
    CONSTRAINT `fk_device_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CHECK (`platform` IN ('IOS', 'ANDROID'))
) ENGINE=InnoDB;

DROP PROCEDURE `notif_v1_ddl`;
