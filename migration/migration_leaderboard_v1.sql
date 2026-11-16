-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: LEADERBOARD — v1
-- ============================================================================
-- Traceability: FR-09.05, docs/sa/leaderboard_backend.md §4
--
-- Mục đích: index phủ cho truy vấn Weekly XP theo khoảng thời gian.
--
-- Ghi chú:
--   - Khớp entity vn.ptit.snapvocab.xp.domain.ExperienceLog (@Index idx_experience_logs_leaderboard).
--   - Idempotent: chỉ thêm khi chưa có (ddl-auto: update ở dev có thể đã tự tạo).
--   - InnoDB thêm index online (INPLACE, không khóa ghi) trên MySQL 8 và MariaDB 10.x.
--   - Chạy ở mọi môi trường. Staging/prod (ddl-auto: validate) không tự tạo index.
--   - Rollback: migration_leaderboard_v1_rollback.sql.
-- ============================================================================

DROP PROCEDURE IF EXISTS `lb_v1_ddl`;
DELIMITER //
CREATE PROCEDURE `lb_v1_ddl`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'experience_logs' AND INDEX_NAME = 'idx_experience_logs_leaderboard') THEN
        CREATE INDEX `idx_experience_logs_leaderboard`
            ON `experience_logs` (`created_at`, `source_type`, `user_id`, `amount`, `bonus_amount`);
    END IF;
END //
DELIMITER ;

CALL lb_v1_ddl();
DROP PROCEDURE `lb_v1_ddl`;
