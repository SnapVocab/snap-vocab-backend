-- ============================================================================
-- ROLLBACK: migration_badges_image_v4.sql (ảnh huy hiệu trên MinIO)
-- ============================================================================
-- Thêm lại icon_key (giá trị theo chỉ số như seed cũ), bỏ image_key. Ảnh trên MinIO giữ nguyên.
-- Chạy SAU khi đã quay backend về bản dùng icon_key.
-- ============================================================================

DROP PROCEDURE IF EXISTS `bdg_v4_rollback`;
DELIMITER //
CREATE PROCEDURE `bdg_v4_rollback`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'icon_key') THEN
        ALTER TABLE `badges` ADD COLUMN `icon_key` VARCHAR(50) NOT NULL DEFAULT 'award';
        UPDATE `badges` SET `icon_key` = CASE `metric`
            WHEN 'LONGEST_STREAK_DAYS' THEN 'flame'
            WHEN 'QUIZ_COMPLETED' THEN 'clipboard-check'
            WHEN 'PERFECT_QUIZ' THEN 'target'
            WHEN 'DISTINCT_WORDS_ADDED' THEN 'book-open'
            ELSE 'award' END;
        ALTER TABLE `badges` ALTER COLUMN `icon_key` DROP DEFAULT;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'image_key') THEN
        ALTER TABLE `badges` DROP COLUMN `image_key`;
    END IF;
END //
DELIMITER ;

CALL bdg_v4_rollback();
DROP PROCEDURE `bdg_v4_rollback`;
