-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: BADGES — v4 (ảnh huy hiệu trên MinIO)
-- ============================================================================
-- Mục đích:
--   1. badges: thêm image_key (object key MinIO, NULL = chưa có ảnh), bỏ icon_key (biểu tượng vẽ trong app).
--   2. 12 huy hiệu MVP trỏ tới public/badges/<code thường>.png.
--
-- Ghi chú:
--   - Idempotent: chỉ thêm/bỏ cột khi cần; UPDATE chỉ chạm dòng image_key còn NULL.
--   - Upload ảnh TRƯỚC: config/upload-badge-images.sh (12 PNG từ SnapVocab/src/assets/images/badges cũ).
--   - Chạy TRƯỚC khi deploy backend (prod ddl-auto: validate). Gỡ: migration_badges_image_v4_rollback.sql.
-- ============================================================================

DROP PROCEDURE IF EXISTS `bdg_v4_ddl`;
DELIMITER //
CREATE PROCEDURE `bdg_v4_ddl`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'image_key') THEN
        ALTER TABLE `badges` ADD COLUMN `image_key` VARCHAR(255) NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'icon_key') THEN
        ALTER TABLE `badges` DROP COLUMN `icon_key`;
    END IF;
END //
DELIMITER ;

CALL bdg_v4_ddl();
DROP PROCEDURE `bdg_v4_ddl`;

UPDATE `badges` SET `image_key` = CONCAT('public/badges/', LOWER(`code`), '.png')
 WHERE `image_key` IS NULL AND `code` IN ('STREAK_7', 'STREAK_30', 'STREAK_100', 'QUIZ_1', 'QUIZ_10', 'QUIZ_50',
       'PERFECT_1', 'PERFECT_10', 'PERFECT_50', 'WORDS_10', 'WORDS_100', 'WORDS_500');
