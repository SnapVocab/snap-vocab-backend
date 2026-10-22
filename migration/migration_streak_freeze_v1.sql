-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION: STREAK FREEZE (Đóng băng chuỗi)
-- ============================================================================
-- Traceability: docs/decisions/streak_freeze.md §5, §9 (PO duyệt 2026-10-21);
--               docs/decisions/shop.md §9; docs/decisions/badges.md §4.1 (PD-SF-06).
--
-- Mục đích:
--   1. `shop_items.type`, `shop_items.theme_key`, `user_items.item_type` → VARCHAR.
--      Hibernate `ddl-auto` đã tạo chúng thành MySQL ENUM, và `ddl-auto: update` không bao giờ thêm giá trị enum,
--      nên insert `STREAK_FREEZE` (hay một ThemeKey mới) sẽ lỗi "Data truncated". VARCHAR là kiểu shop.md §9 và
--      migration_shop.sql quy định; entity đã khai báo `@JdbcTypeCode(SqlTypes.VARCHAR)`.
--      Chỉ đổi cột đang là ENUM: schema dựng bằng migration_shop.sql (đã VARCHAR, có composite FK) không bị chạm.
--   2. Tạo bảng `streak_freeze_days` (ngày được giữ chuỗi; biên lai của mỗi vật phẩm đã dùng).
--   3. Sửa mô tả badge STREAK_7/30/100, chỉ khi vẫn là câu đã seed (không đè bản Admin đã sửa).
--
-- Thứ tự phát hành: chạy script này TRƯỚC khi deploy backend có Streak Freeze. Backend đọc `streak_freeze_days`
-- ở mọi lần đọc chuỗi, kể cả khi chưa ai có vật phẩm.
-- Idempotent: chạy lại nhiều lần an toàn.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- BƯỚC 1: ENUM → VARCHAR (chỉ khi cột đang là ENUM)
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `shop_items` MODIFY `type` VARCHAR(30) NOT NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_items' AND COLUMN_NAME = 'type' AND DATA_TYPE = 'enum'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `shop_items` MODIFY `theme_key` VARCHAR(50) NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_items' AND COLUMN_NAME = 'theme_key' AND DATA_TYPE = 'enum'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- `equipped_slot` (STORED, IF(equipped = 1, item_type, NULL)) được MySQL tính lại khi đổi kiểu `item_type`.
SET @sql = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `user_items` MODIFY `item_type` VARCHAR(30) NOT NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_items' AND COLUMN_NAME = 'item_type' AND DATA_TYPE = 'enum'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- BƯỚC 2: Bảng `streak_freeze_days`
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `streak_freeze_days` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `frozen_date` DATE NOT NULL,                -- ngày học được giữ (GMT+7)
    `shop_item_id` BIGINT NOT NULL,             -- item đã bị trừ
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_streak_freeze_day` (`user_id`, `frozen_date`),
    KEY `idx_streak_freeze_days_item` (`shop_item_id`),
    CONSTRAINT `fk_streak_freeze_days_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_streak_freeze_days_item` FOREIGN KEY (`shop_item_id`) REFERENCES `shop_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 3: Mô tả badge streak (PD-SF-06): ngày được giữ nối chuỗi nhưng không cộng ngày
-- ----------------------------------------------------------------------------
UPDATE `badges` SET `description` = 'Làm quiz hoặc ôn thẻ 7 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_7' AND `description` = 'Làm quiz hoặc ôn thẻ mỗi ngày, 7 ngày liên tiếp';
UPDATE `badges` SET `description` = 'Làm quiz hoặc ôn thẻ 30 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_30' AND `description` = 'Làm quiz hoặc ôn thẻ mỗi ngày, 30 ngày liên tiếp';
UPDATE `badges` SET `description` = 'Làm quiz hoặc ôn thẻ 100 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_100' AND `description` = 'Làm quiz hoặc ôn thẻ mỗi ngày, 100 ngày liên tiếp';
