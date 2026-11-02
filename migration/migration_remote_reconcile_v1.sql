-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: REMOTE RECONCILE — v1
-- ============================================================================
-- Traceability:
--   REL-DM-01: docs/decisions/daily_mission.md §0; docs/reviews/remote_schema_drift_2026-10-22.md
--
-- Mục đích: DB remote (MariaDB 10.11) mang schema của một nhánh backend khác. Nhóm chốt 2026-10-22 DB chạy code
-- nhánh này. Script xóa các bảng thuộc schema nhánh kia để Hibernate (ddl-auto: update) và các migration của nhánh
-- này tạo lại đúng entity:
--   1. Bảng chỉ nhánh kia có (mission UUID, quiz policy/attempt, reward grant, outbox…).
--   2. Bảng trùng tên nhưng khác phiên bản: mission_templates, user_daily_missions, learning_events (id BINARY(16)),
--      3 bảng mission còn lại (để v1 tạo lại kèm FK), quizzes/quiz_* (cột NOT NULL code này không ghi),
--      coin_transactions, experience_logs (enum còn DAILY_CHEST/WEEKLY_CHEST), user_items.
--   3. shop_items (rỗng) được SỬA tại chỗ, không xóa: migration_streak_freeze_v1 cần FK tới nó trước khi backend chạy.
--
-- An toàn:
--   - Chỉ xóa bảng RỖNG. Kiểm tra toàn bộ trước; một bảng còn dòng ⇒ SIGNAL RR_NOT_EMPTY, không bảng nào bị xóa.
--   - Mỗi bảng có dấu hiệu nhận biết bản nhánh kia; DB đã đúng schema nhánh này thì script không làm gì.
--   - Idempotent. Bảng con có FK tới bảng bị xóa (booster_activations, quiz_matching_attempts) được giữ; FK nối lại
--     khi bảng cha được tạo lại cùng tên, cùng kiểu khóa.
--
-- Thứ tự phát hành (kiểm bằng RemoteSchemaReconcileMariaDbTest trên MariaDB 10.11):
--   1. Backup DB.  2. Script này.
--   3. migration_notifications_v1, _v2, migration_admin_audit_logs_v1, migration_badges_v1, _v2,
--      migration_streak_freeze_v1, migration_badges_streak_copy_v2, migration_daily_mission_v1, _triggers_v2,
--      migration_remove_theme_v1.
--   4. Deploy backend (ddl-auto: update tạo lại các bảng đã xóa).  5. Đổi sang ddl-auto: validate.
-- ============================================================================

DROP PROCEDURE IF EXISTS `rr_tmp_reconcile`;

DELIMITER //
CREATE PROCEDURE `rr_tmp_reconcile`()
BEGIN
    DECLARE t VARCHAR(64);
    DECLARE done INT DEFAULT 0;
    DECLARE cur CURSOR FOR SELECT `name` FROM `rr_tmp_drop`;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    DROP TEMPORARY TABLE IF EXISTS `rr_tmp_drop`;
    CREATE TEMPORARY TABLE `rr_tmp_drop` (`name` VARCHAR(64) PRIMARY KEY, `alter_only` BOOLEAN NOT NULL DEFAULT FALSE);

    -- 1. Tables only the other branch has.
    INSERT INTO `rr_tmp_drop` (`name`)
    SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
        'learning_event_deliveries', 'mission_admin_audits', 'mission_config_state', 'mission_configs',
        'mission_daily_sets', 'mission_notification_outbox', 'mission_progress_events', 'quiz_attempt_answers',
        'quiz_attempts', 'quiz_coverage_items', 'quiz_daily_reward_usage', 'quiz_item_mode_stats', 'quiz_policy_state',
        'quiz_policy_versions', 'quiz_selection_states', 'quiz_topic_item_practice_stats', 'request_idempotencies',
        'reward_grants', 'topic_schemas', 'user_inventories');

    -- 2. Same name, other version: a column only that version has, or a non-BIGINT id.
    INSERT INTO `rr_tmp_drop` (`name`, `alter_only`)
    SELECT DISTINCT m.t, m.t = 'shop_items' FROM (
        SELECT 'mission_templates' AS t, 'id' AS c, 'binary' AS dt
        UNION ALL SELECT 'user_daily_missions', 'id', 'binary'
        UNION ALL SELECT 'user_daily_mission_progress_logs', 'id', NULL
        UNION ALL SELECT 'user_daily_mission_claims', 'id', NULL
        UNION ALL SELECT 'user_word_credits', 'id', NULL
        UNION ALL SELECT 'learning_events', 'id', 'binary'
        UNION ALL SELECT 'quizzes', 'policy_version_id', NULL
        UNION ALL SELECT 'quiz_questions', 'answer_key', NULL
        UNION ALL SELECT 'quiz_matching_tiles', 'source_question_id', NULL
        UNION ALL SELECT 'quiz_question_options', 'position', NULL
        UNION ALL SELECT 'coin_transactions', 'source_id', NULL
        UNION ALL SELECT 'experience_logs', 'balance_after', NULL
        UNION ALL SELECT 'shop_items', 'is_active', NULL
        UNION ALL SELECT 'user_items', 'item_type', 'enum') m
    JOIN information_schema.COLUMNS col ON col.TABLE_SCHEMA = DATABASE() AND col.TABLE_NAME = m.t
         AND col.COLUMN_NAME = m.c AND (m.dt IS NULL OR col.DATA_TYPE = m.dt)
    -- The 3 BIGINT mission tables only go with the UUID user_daily_missions (they lack its FK).
    WHERE m.c <> 'id' OR m.dt IS NOT NULL OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS u WHERE u.TABLE_SCHEMA = DATABASE()
           AND u.TABLE_NAME = 'user_daily_missions' AND u.COLUMN_NAME = 'id' AND u.DATA_TYPE = 'binary');

    -- Check every table before dropping any.
    OPEN cur;
    check_loop: LOOP
        FETCH cur INTO t;
        IF done THEN LEAVE check_loop; END IF;
        SET @rr_sql = CONCAT('SELECT COUNT(*) INTO @rr_rows FROM `', t, '`');
        PREPARE stmt FROM @rr_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        IF @rr_rows > 0 THEN
            SET @rr_msg = CONCAT('RR_NOT_EMPTY: ', t, ' has ', @rr_rows, ' rows; nothing was dropped');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @rr_msg;
        END IF;
    END LOOP;
    CLOSE cur;

    SELECT GROUP_CONCAT(CONCAT('`', `name`, '`') ORDER BY `name`) INTO @rr_list FROM `rr_tmp_drop` WHERE NOT `alter_only`;
    IF @rr_list IS NOT NULL THEN
        SET FOREIGN_KEY_CHECKS = 0;
        SET @rr_sql = CONCAT('DROP TABLE ', @rr_list);
        PREPARE stmt FROM @rr_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET FOREIGN_KEY_CHECKS = 1;
    END IF;
    IF EXISTS (SELECT 1 FROM `rr_tmp_drop` WHERE `name` = 'shop_items') THEN
        ALTER TABLE `shop_items`
            DROP COLUMN `is_active`,
            MODIFY COLUMN `name` VARCHAR(100) NOT NULL,
            MODIFY COLUMN `type` VARCHAR(30) NOT NULL,
            MODIFY COLUMN `price` BIGINT NULL,
            MODIFY COLUMN `theme_key` VARCHAR(50) NULL;
    END IF;
    DROP TEMPORARY TABLE `rr_tmp_drop`;
END//
DELIMITER ;

CALL `rr_tmp_reconcile`();
DROP PROCEDURE `rr_tmp_reconcile`;
