-- ============================================================================
-- MẪU MIGRATION SỬA CATALOG DAILY MISSION (copy thành migration_daily_mission_catalog_<yyyymmdd>_<mô tả>.sql)
-- ============================================================================
-- Traceability: docs/decisions/daily_mission.md §4.2 (invariant), §7.2 (snapshot), §7.7 (sửa catalog bằng SQL);
--   REL-DM-01 (Proposed).
--
-- Quy tắc:
--   - Chỉ DML trên `mission_templates` (INSERT / UPDATE). Không DELETE: tắt bằng is_active = 0.
--   - Không sửa `code` sau khi tạo; không chạm `user_daily_missions` (snapshot đã cấp giữ nguyên).
--   - Toàn bộ thay đổi + CALL dm_assert_catalog_invariant() chạy trong MỘT transaction. Vi phạm invariant ⇒
--     handler ROLLBACK rồi RESIGNAL: không thay đổi nào được commit và client nhận lỗi SQLSTATE 45000.
--   - Không đặt DDL (CREATE/ALTER/DROP TABLE) trong khối này: DDL MySQL tự COMMIT ngầm, phá vỡ rollback.
--   - Cần procedure dm_assert_catalog_invariant từ migration_daily_mission_v1.sql.
-- Cơ chế rollback được kiểm chứng bởi MissionMigrationScriptTest trên MySQL local.
-- ============================================================================

DROP PROCEDURE IF EXISTS `dm_tmp_catalog_change`;

DELIMITER //
CREATE PROCEDURE `dm_tmp_catalog_change`()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- >>> Thay đổi catalog ở đây. Ví dụ:
    -- UPDATE `mission_templates` SET `weight` = 2, `version` = `version` + 1, `updated_at` = NOW(6)
    --  WHERE `code` = 'KEEP_DAILY_STREAK';
    -- <<<

    CALL `dm_assert_catalog_invariant`();
    COMMIT;
END//
DELIMITER ;

CALL `dm_tmp_catalog_change`();
DROP PROCEDURE IF EXISTS `dm_tmp_catalog_change`;
