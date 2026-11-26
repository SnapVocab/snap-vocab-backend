-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: ADMIN NỘI DUNG HỌC TẬP — v1
-- ============================================================================
-- Mục đích:
--   1. Từ điển sửa được và xóa mềm (FR-13.02, quy tắc "xóa từ vựng phải là soft-delete"):
--      words.deleted_at (NULL = đang dùng), words.version (khóa lạc quan cho PUT /api/admin/words/{id}).
--   2. Mẫu thẻ sửa được và đặt mặc định (FR-13.07, snapAdmin templates_ui.md §8.5): templates.version.
--   2b. Ẩn chủ đề hệ thống (OQ-CNT-01, PO 2026-11-26): topics.hidden_at / hidden_by / hidden_reason.
--   3. Nhật ký thao tác: thêm CARD_TEMPLATE vào chk_aal_target_type và 10 hành động mới vào chk_aal_action
--      (WORD_DELETED/RESTORED, TOPIC_UPDATED/DELETED/HIDDEN/RESTORED, TOPIC_ITEM_UPDATED/DELETED,
--      CARD_TEMPLATE_UPDATED/DEFAULT_CHANGED).
--
-- Ghi chú:
--   - Chỉ thêm cột (có DEFAULT) và nới CHECK; không sửa, không xóa dữ liệu. Chạy được trên MariaDB 10.11 và MySQL 8.
--   - Diễn tập 2026-11-26 trên bản sao prod mới (377.350 words): 0,3 giây, chạy lại không đổi gì.
--   - Chạy migration TRƯỚC khi khởi động backend mới: backend (ddl-auto validate) từ chối khởi động
--     khi thiếu templates.version.
--   - Idempotent: cột kiểm information_schema trước; CHECK luôn DROP rồi ADD lại cùng tên với danh sách đầy đủ
--     (cách migration_admin_audit_logs_v1.sql dặn khi thêm giá trị enum).
--   - Thứ tự phát hành: chạy TRƯỚC khi deploy backend có các API admin nội dung (prod dùng ddl-auto: validate).
--   - Rollback: migration_admin_content_v1_rollback.sql (chỉ chạy khi chưa có bản ghi nào dùng giá trị mới).
-- ============================================================================

DROP PROCEDURE IF EXISTS `content_v1_column`;
DELIMITER //
CREATE PROCEDURE `content_v1_column`(IN `tbl` VARCHAR(64), IN `col` VARCHAR(64), IN `def` VARCHAR(200))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = `tbl` AND COLUMN_NAME = `col`) THEN
        SET @content_v1_ddl = CONCAT('ALTER TABLE `', `tbl`, '` ADD COLUMN `', `col`, '` ', `def`);
        PREPARE stmt FROM @content_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL content_v1_column('words', 'deleted_at', 'DATETIME(6) NULL');
CALL content_v1_column('words', 'version', 'BIGINT NOT NULL DEFAULT 0');
CALL content_v1_column('templates', 'version', 'BIGINT NOT NULL DEFAULT 0');
CALL content_v1_column('topics', 'hidden_at', 'DATETIME(6) NULL');
CALL content_v1_column('topics', 'hidden_by', 'VARCHAR(254) NULL');
CALL content_v1_column('topics', 'hidden_reason', 'VARCHAR(500) NULL');

DROP PROCEDURE IF EXISTS `content_v1_column`;

DROP PROCEDURE IF EXISTS `content_v1_check`;
DELIMITER //
CREATE PROCEDURE `content_v1_check`(IN `name` VARCHAR(64), IN `clause` VARCHAR(2000))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
            AND TABLE_NAME = 'admin_audit_logs' AND CONSTRAINT_NAME = `name`) THEN
        SET @content_v1_ddl = CONCAT('ALTER TABLE `admin_audit_logs` DROP CONSTRAINT `', `name`, '`');
        PREPARE stmt FROM @content_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
    SET @content_v1_ddl = CONCAT('ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `', `name`, '` CHECK (', `clause`, ')');
    PREPARE stmt FROM @content_v1_ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END //
DELIMITER ;

CALL content_v1_check('chk_aal_target_type', '`target_type` IN (''SHOP_ITEM'', ''MISSION_TEMPLATE'', ''BADGE'', ''COLLECTION'', ''TOPIC'', ''NOTIFICATION'', ''WORD'', ''SCAN_LABEL'', ''LEARNER'', ''CARD_TEMPLATE'')');
CALL content_v1_check('chk_aal_action', '`action` IN (''SHOP_ITEM_CREATED'', ''SHOP_ITEM_UPDATED'', ''SHOP_ITEM_PUBLISHED'', ''SHOP_ITEM_ARCHIVED'', ''SHOP_ITEM_DELETED'', ''MISSION_TEMPLATE_CREATED'', ''MISSION_TEMPLATE_UPDATED'', ''MISSION_TEMPLATE_ENABLED'', ''MISSION_TEMPLATE_DISABLED'', ''BADGE_UPDATED'', ''BADGE_ENABLED'', ''BADGE_DISABLED'', ''COLLECTION_CREATED'', ''COLLECTION_UPDATED'', ''COLLECTION_DELETED'', ''TOPIC_CREATED'', ''TOPIC_UPDATED'', ''TOPIC_DELETED'', ''TOPIC_HIDDEN'', ''TOPIC_RESTORED'', ''TOPIC_ITEM_ADDED'', ''TOPIC_ITEM_UPDATED'', ''TOPIC_ITEM_DELETED'', ''TOPIC_TEMPLATE_CHANGED'', ''TOPIC_SCHEMA_CHANGED'', ''CARD_TEMPLATE_UPDATED'', ''CARD_TEMPLATE_DEFAULT_CHANGED'', ''SYSTEM_NOTIFICATION_SENT'', ''WORD_UPDATED'', ''WORD_DELETED'', ''WORD_RESTORED'', ''SCAN_LABEL_MAPPED'', ''LEARNER_BANNED'', ''LEARNER_UNBANNED'', ''LEARNER_PASSWORD_RESET_SENT'')');

DROP PROCEDURE IF EXISTS `content_v1_check`;
