-- Rollback of migration_leaderboard_v1.sql: drops the leaderboard index (idempotent). Safe while the backend still
-- runs; the snapshot queries only get slower.
DROP PROCEDURE IF EXISTS `lb_v1_rollback`;
DELIMITER //
CREATE PROCEDURE `lb_v1_rollback`()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'experience_logs' AND INDEX_NAME = 'idx_experience_logs_leaderboard') THEN
        DROP INDEX `idx_experience_logs_leaderboard` ON `experience_logs`;
    END IF;
END //
DELIMITER ;

CALL lb_v1_rollback();
DROP PROCEDURE `lb_v1_rollback`;
