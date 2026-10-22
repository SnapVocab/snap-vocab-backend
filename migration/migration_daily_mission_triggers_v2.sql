-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION: DAILY MISSION — trigger SRS_REVIEW_COMPLETED (slot 3), SCAN_WORD_SAVED (slot 1),
-- điều kiện cấp HAS_STUDY_CARDS_FOR_TARGET (biến thể STUDY_10_CARDS)
-- ============================================================================
-- Traceability: docs/decisions/daily_mission.md PD-DM-07, PD-DM-12, §4.3, §5.1, §6.1.
--
-- Mục đích: thêm giá trị `SRS_REVIEW_COMPLETED`, `SCAN_WORD_SAVED` vào CHECK enum `trigger_event` của `mission_templates` và
-- `user_daily_missions`, và `HAS_STUDY_CARDS_FOR_TARGET` vào CHECK `eligibility_rule` của `mission_templates`.
-- `ddl-auto: update` không sửa CHECK đã có, nên thiếu bước này thì seeder chèn các template mới sẽ lỗi
-- và backend KHÔNG khởi động được. Cuối script định nghĩa lại dm_assert_catalog_invariant() (PD-DM-13).
--
-- Thứ tự phát hành: chạy script này TRƯỚC khi deploy backend có hai template trên.
-- Seeder tạo cả hai ở trạng thái TẮT; Admin chỉ bật sau khi bản app hiểu trigger mới đã lên store.
-- Idempotent: chỉ đổi CHECK còn thiếu giá trị mới; giữ nguyên tên constraint (Hibernate đặt <table>_chk_N).
-- ============================================================================

-- mission_templates
SET @chk = (
    SELECT c.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS c
    JOIN information_schema.TABLE_CONSTRAINTS t
        ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
    WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'mission_templates' AND t.CONSTRAINT_TYPE = 'CHECK'
        AND c.CHECK_CLAUSE LIKE '%BOOSTER_USED%' AND c.CHECK_CLAUSE NOT LIKE '%SCAN_WORD_SAVED%'
);
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `mission_templates` DROP CHECK `', @chk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `mission_templates` ADD CONSTRAINT `', @chk, '` CHECK ',
    '(`trigger_event` IN (''QUIZ_COMPLETED'', ''LEARNING_ACTIVITY_RECORDED'', ''TOPIC_ITEM_CREATED'', ',
    '''SHOP_ITEM_PURCHASED'', ''BOOSTER_USED'', ''SRS_REVIEW_COMPLETED'', ''SCAN_WORD_SAVED''))'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- user_daily_missions
SET @chk = (
    SELECT c.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS c
    JOIN information_schema.TABLE_CONSTRAINTS t
        ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
    WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'user_daily_missions' AND t.CONSTRAINT_TYPE = 'CHECK'
        AND c.CHECK_CLAUSE LIKE '%BOOSTER_USED%' AND c.CHECK_CLAUSE NOT LIKE '%SCAN_WORD_SAVED%'
);
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `user_daily_missions` DROP CHECK `', @chk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `user_daily_missions` ADD CONSTRAINT `', @chk, '` CHECK ',
    '(`trigger_event` IN (''QUIZ_COMPLETED'', ''LEARNING_ACTIVITY_RECORDED'', ''TOPIC_ITEM_CREATED'', ',
    '''SHOP_ITEM_PURCHASED'', ''BOOSTER_USED'', ''SRS_REVIEW_COMPLETED'', ''SCAN_WORD_SAVED''))'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mission_templates.eligibility_rule (+ HAS_STUDY_CARDS_FOR_TARGET)
SET @chk = (
    SELECT c.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS c
    JOIN information_schema.TABLE_CONSTRAINTS t
        ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
    WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'mission_templates' AND t.CONSTRAINT_TYPE = 'CHECK'
        AND c.CHECK_CLAUSE LIKE '%HAS_PURCHASABLE_ITEM%' AND c.CHECK_CLAUSE NOT LIKE '%HAS_STUDY_CARDS_FOR_TARGET%'
);
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `mission_templates` DROP CHECK `', @chk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(@chk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `mission_templates` ADD CONSTRAINT `', @chk, '` CHECK ',
    '(`eligibility_rule` IN (''NONE'', ''HAS_IDLE_XP_BOOSTER'', ''HAS_PURCHASABLE_ITEM'', ''HAS_STUDY_CARDS_FOR_TARGET''))'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Invariant: chỉ đếm template dùng trigger mà mọi bản app hiển thị được (PD-DM-13), như MissionTracker.satisfiesInvariant.
DROP PROCEDURE IF EXISTS `dm_assert_catalog_invariant`;

DELIMITER //
CREATE PROCEDURE `dm_assert_catalog_invariant`()
BEGIN
    DECLARE core_count INT;
    DECLARE slot5_count INT;
    SELECT COALESCE(SUM(`category` IN ('SCAN', 'VOCABULARY', 'FLASHCARD', 'SRS', 'QUIZ')), 0),
           COALESCE(SUM(`category` IN ('RETENTION', 'SHOP')), 0)
      INTO core_count, slot5_count
      FROM `mission_templates`
     WHERE `is_active` = 1 AND `eligibility_rule` = 'NONE'
       -- Only triggers every app can render (MissionTriggerEvent.LEGACY, PD-DM-13).
       AND `trigger_event` IN ('QUIZ_COMPLETED', 'LEARNING_ACTIVITY_RECORDED', 'TOPIC_ITEM_CREATED',
                               'SHOP_ITEM_PURCHASED', 'BOOSTER_USED');
    IF NOT (core_count >= 4 AND (slot5_count >= 1 OR core_count >= 5)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'MISSION_POOL_INSUFFICIENT: active catalog violates daily_mission.md 4.2 invariant';
    END IF;
END//
DELIMITER ;
