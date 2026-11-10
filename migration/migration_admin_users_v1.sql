-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: ADMIN NGƯỜI HỌC (khóa / mở khóa tài khoản) — v1
-- ============================================================================
-- Mục đích: 3 cột khóa tài khoản trên users (banned_at, ban_reason, banned_by). Không dùng cột activated:
-- activated = đã xác minh email, khóa là trạng thái riêng.
--
-- Ghi chú:
--   - Idempotent (kiểm information_schema trước mỗi lệnh), chạy được trên MariaDB 10.11 và MySQL 8.
--   - Thứ tự phát hành: chạy sau migration_admin_stats_v1, trước khi deploy backend dùng ddl-auto: validate.
-- ============================================================================

DROP PROCEDURE IF EXISTS `users_v1_column`;
DELIMITER //
CREATE PROCEDURE `users_v1_column`(IN `col` VARCHAR(64), IN `def` VARCHAR(200))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'users' AND COLUMN_NAME = `col`) THEN
        SET @users_v1_ddl = CONCAT('ALTER TABLE `users` ADD COLUMN `', `col`, '` ', `def`);
        PREPARE stmt FROM @users_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL users_v1_column('banned_at', 'DATETIME(6) NULL');
CALL users_v1_column('ban_reason', 'VARCHAR(500) NULL');
CALL users_v1_column('banned_by', 'VARCHAR(254) NULL');

DROP PROCEDURE IF EXISTS `users_v1_column`;
