-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: NOTIFICATIONS — v2
-- ============================================================================
-- Traceability:
--   SS-15 Notification: docs/decisions/notification.md §2 (type STREAK_WARNING, OQ-NOTIF-06 = B)
--
-- Mục đích: thêm `STREAK_WARNING` vào CHECK enum `type` của `notifications`. `ddl-auto: update` không sửa CHECK
-- đã có, nên thiếu bước này thì job J4 ghi lỗi.
--
-- Ghi chú:
--   - Chạy SAU migration_notifications_v1.sql, TRƯỚC khi deploy backend có J4.
--   - Idempotent: chỉ đổi CHECK còn thiếu giá trị mới; giữ nguyên tên constraint (v1 để MySQL đặt tên
--     notifications_chk_N, Hibernate cũng vậy).
-- ============================================================================

SET @chk = (
    SELECT c.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS c
    JOIN information_schema.TABLE_CONSTRAINTS t
        ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
    WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'notifications' AND t.CONSTRAINT_TYPE = 'CHECK'
        AND c.CHECK_CLAUSE LIKE '%SRS_REMINDER%' AND c.CHECK_CLAUSE NOT LIKE '%STREAK_WARNING%'
);
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `notifications` DROP CHECK `', @chk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `notifications` ADD CONSTRAINT `', @chk, '` CHECK ',
    '(`type` IN (''SRS_REMINDER'', ''SYSTEM'', ''STREAK_WARNING''))'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
