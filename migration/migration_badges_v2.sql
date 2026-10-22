-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: BADGES — v2 (Admin P2)
-- ============================================================================
-- Traceability:
--   docs/decisions/badges.md §7.1 (cột version), §8.4 (PATCH /api/admin/badges/{id}), PD-BDG-13a–b
--
-- Mục đích:
--   Thêm cột optimistic lock `badges.version` (BIGINT NOT NULL DEFAULT 0) cho Admin sửa nội dung, Bật/Tắt.
--
-- Ghi chú:
--   - Khớp entity vn.ptit.snapvocab.badge.domain.Badge (@Version Long version); staging chạy ddl-auto validate.
--   - Idempotent: chỉ thêm khi chưa có cột (ddl-auto: update có thể đã tự thêm).
--   - Row có sẵn nhận version = 0; không đổi dữ liệu khác, không chạm user_badges.
--   - Chạy TRƯỚC khi deploy backend có PATCH badges, cùng migration_admin_audit_logs_v1.sql (Badges P2 ghi audit).
-- ============================================================================

DROP PROCEDURE IF EXISTS `bdg_v2_ddl`;
DELIMITER //
CREATE PROCEDURE `bdg_v2_ddl`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'version') THEN
        ALTER TABLE `badges` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;
    END IF;
END //
DELIMITER ;

CALL bdg_v2_ddl();
DROP PROCEDURE `bdg_v2_ddl`;
