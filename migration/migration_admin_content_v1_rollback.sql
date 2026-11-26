-- ============================================================================
-- ROLLBACK: migration_admin_content_v1.sql
-- ============================================================================
-- Chỉ chạy SAU KHI đã quay backend về bản không có API admin nội dung (bản đó không đọc các cột này).
-- Từ đã xóa mềm / chủ đề đã ẩn sẽ hiện lại khi bỏ cột: script dừng nếu còn từ xóa mềm hoặc chủ đề đang ẩn,
-- và dừng nếu nhật ký đã có hành động mới (CHECK cũ sẽ không nhận các dòng đó).
-- ============================================================================

DROP PROCEDURE IF EXISTS `content_v1_rollback`;
DELIMITER //
CREATE PROCEDURE `content_v1_rollback`()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'words' AND COLUMN_NAME = 'deleted_at') THEN
        IF (SELECT COUNT(*) FROM `words` WHERE `deleted_at` IS NOT NULL) > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'words has soft-deleted rows: restore them first';
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'topics' AND COLUMN_NAME = 'hidden_at') THEN
        IF (SELECT COUNT(*) FROM `topics` WHERE `hidden_at` IS NOT NULL) > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'topics has hidden rows: restore them first';
        END IF;
    END IF;
    IF (SELECT COUNT(*) FROM `admin_audit_logs` WHERE `action` IN ('TOPIC_UPDATED', 'TOPIC_DELETED', 'TOPIC_HIDDEN', 'TOPIC_RESTORED',
            'TOPIC_ITEM_UPDATED', 'TOPIC_ITEM_DELETED', 'CARD_TEMPLATE_UPDATED', 'CARD_TEMPLATE_DEFAULT_CHANGED',
            'WORD_DELETED', 'WORD_RESTORED')) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'admin_audit_logs already holds v1 actions: keep the wider CHECK';
    END IF;

    ALTER TABLE `admin_audit_logs` DROP CONSTRAINT `chk_aal_target_type`;
    ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_target_type` CHECK (`target_type` IN ('SHOP_ITEM',
        'MISSION_TEMPLATE', 'BADGE', 'COLLECTION', 'TOPIC', 'NOTIFICATION', 'WORD', 'SCAN_LABEL', 'LEARNER'));
    ALTER TABLE `admin_audit_logs` DROP CONSTRAINT `chk_aal_action`;
    ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_action` CHECK (`action` IN ('SHOP_ITEM_CREATED',
        'SHOP_ITEM_UPDATED', 'SHOP_ITEM_PUBLISHED', 'SHOP_ITEM_ARCHIVED', 'SHOP_ITEM_DELETED',
        'MISSION_TEMPLATE_CREATED', 'MISSION_TEMPLATE_UPDATED', 'MISSION_TEMPLATE_ENABLED',
        'MISSION_TEMPLATE_DISABLED', 'BADGE_UPDATED', 'BADGE_ENABLED', 'BADGE_DISABLED', 'COLLECTION_CREATED',
        'COLLECTION_UPDATED', 'COLLECTION_DELETED', 'TOPIC_CREATED', 'TOPIC_ITEM_ADDED', 'TOPIC_TEMPLATE_CHANGED',
        'TOPIC_SCHEMA_CHANGED', 'SYSTEM_NOTIFICATION_SENT', 'WORD_UPDATED', 'SCAN_LABEL_MAPPED', 'LEARNER_BANNED',
        'LEARNER_UNBANNED', 'LEARNER_PASSWORD_RESET_SENT'));

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'words' AND COLUMN_NAME = 'deleted_at') THEN
        ALTER TABLE `words` DROP COLUMN `deleted_at`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'words' AND COLUMN_NAME = 'version') THEN
        ALTER TABLE `words` DROP COLUMN `version`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'templates' AND COLUMN_NAME = 'version') THEN
        ALTER TABLE `templates` DROP COLUMN `version`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'topics' AND COLUMN_NAME = 'hidden_at') THEN
        ALTER TABLE `topics` DROP COLUMN `hidden_at`, DROP COLUMN `hidden_by`, DROP COLUMN `hidden_reason`;
    END IF;
END //
DELIMITER ;

CALL content_v1_rollback();
DROP PROCEDURE IF EXISTS `content_v1_rollback`;
