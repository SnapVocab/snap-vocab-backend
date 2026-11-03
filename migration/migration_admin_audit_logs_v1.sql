-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: ADMIN AUDIT LOGS — v1
-- ============================================================================
-- Traceability:
--   PD-ADS-10 (snapAdmin/docs/design/design.md §8), docs/sa/admin_audit_log_backend.md §3
--
-- Mục đích:
--   Tạo bảng admin_audit_logs. Chỉ thêm, không sửa bảng cũ.
--
-- Ghi chú:
--   - Kiểu cột khớp DDL Hibernate sinh từ vn.ptit.snapvocab.audit.domain.AdminAuditLog (staging: ddl-auto validate).
--   - Idempotent. ddl-auto: update có thể đã tự tạo bảng (khi đó thiếu CHECK): script chỉ thêm CHECK còn thiếu.
--   - Không gắn collation: dùng mặc định của database (MariaDB ở dev/prod, MySQL 8 ở local/test).
--   - Không FK tới users: bản ghi audit phải còn khi tài khoản admin bị xóa.
--   - mission_admin_audits (trống, không code nào dùng) giữ nguyên; xóa bằng script riêng sau khi xác nhận rỗng trên prod.
--   - Thêm giá trị enum sau này: DROP CONSTRAINT rồi ADD lại CHECK cùng tên (cú pháp chạy được trên cả MariaDB và MySQL 8).
--   - Chạy TRƯỚC khi deploy backend có module audit (cùng đợt với Admin FE-AUD-01: archive bắt buộc lý do).
--   - 2026-10-22: thêm BADGES / BADGE / BADGE_UPDATED|ENABLED|DISABLED (Badges P2) ngay trong v1, vì v1 chưa chạy ở
--     môi trường nào. Nếu đã chạy bản cũ ở đâu đó: DROP 3 CHECK chk_aal_area/target_type/action rồi chạy lại script.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `admin_audit_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `vn_day` DATE NOT NULL,
    `actor_id` BIGINT NULL,
    `actor_email` VARCHAR(255) NULL,
    `area` VARCHAR(20) NOT NULL,
    `action` VARCHAR(40) NOT NULL,
    `target_type` VARCHAR(30) NOT NULL,
    `target_id` VARCHAR(255) NOT NULL,
    `target_name` VARCHAR(255) NOT NULL,
    `target_code` VARCHAR(120) NULL,
    `target_parents` VARCHAR(100) NULL,
    `changes` LONGTEXT NOT NULL,
    `reason` VARCHAR(500) NULL,
    `guardrail` VARCHAR(500) NULL,
    `economy_change` BIT(1) NOT NULL,
    `object_version` BIGINT NULL,
    `search_text` VARCHAR(1500) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_aal_day` (`vn_day`),
    KEY `idx_aal_target` (`target_type`, `target_id`, `vn_day`),
    KEY `idx_aal_actor` (`actor_id`, `vn_day`),
    KEY `idx_aal_area` (`area`, `vn_day`)
) ENGINE=InnoDB;

DROP PROCEDURE IF EXISTS `aal_v1_ddl`;
DELIMITER //
CREATE PROCEDURE `aal_v1_ddl`(IN `missing` BOOLEAN, IN `ddl` VARCHAR(2000))
BEGIN
    IF `missing` THEN
        SET @aal_v1_ddl = `ddl`;
        PREPARE stmt FROM @aal_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL aal_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_logs' AND CONSTRAINT_NAME = 'chk_aal_area'),
    'ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_area` CHECK (`area` IN (''SHOP'', ''MISSIONS'', ''BADGES'', ''DICTIONARY'', ''TOPICS'', ''AI_SCAN'', ''LEARNERS'', ''NOTIFICATIONS''))');

CALL aal_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_logs' AND CONSTRAINT_NAME = 'chk_aal_target_type'),
    'ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_target_type` CHECK (`target_type` IN (''SHOP_ITEM'', ''MISSION_TEMPLATE'', ''BADGE'', ''COLLECTION'', ''TOPIC'', ''NOTIFICATION'', ''WORD'', ''SCAN_LABEL'', ''LEARNER''))');

CALL aal_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_logs' AND CONSTRAINT_NAME = 'chk_aal_action'),
    'ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_action` CHECK (`action` IN (''SHOP_ITEM_CREATED'', ''SHOP_ITEM_UPDATED'', ''SHOP_ITEM_PUBLISHED'', ''SHOP_ITEM_ARCHIVED'', ''SHOP_ITEM_DELETED'', ''MISSION_TEMPLATE_CREATED'', ''MISSION_TEMPLATE_UPDATED'', ''MISSION_TEMPLATE_ENABLED'', ''MISSION_TEMPLATE_DISABLED'', ''BADGE_UPDATED'', ''BADGE_ENABLED'', ''BADGE_DISABLED'', ''COLLECTION_CREATED'', ''COLLECTION_UPDATED'', ''COLLECTION_DELETED'', ''TOPIC_CREATED'', ''TOPIC_ITEM_ADDED'', ''TOPIC_TEMPLATE_CHANGED'', ''TOPIC_SCHEMA_CHANGED'', ''SYSTEM_NOTIFICATION_SENT'', ''WORD_UPDATED'', ''SCAN_LABEL_MAPPED'', ''LEARNER_BANNED'', ''LEARNER_UNBANNED'', ''LEARNER_PASSWORD_RESET_SENT''))');

CALL aal_v1_ddl(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_logs' AND CONSTRAINT_NAME = 'chk_aal_changes_json'),
    'ALTER TABLE `admin_audit_logs` ADD CONSTRAINT `chk_aal_changes_json` CHECK (JSON_VALID(`changes`))');

DROP PROCEDURE `aal_v1_ddl`;
