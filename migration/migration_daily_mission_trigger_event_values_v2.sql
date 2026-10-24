-- Add mission trigger values introduced after migration_daily_mission_v1.sql.
-- Safe to run on a fresh schema and on a database that already has v1 applied.

SET @old_template_trigger_check = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mission_templates'
      AND CONSTRAINT_NAME IN ('mission_templates_chk_3', 'CONSTRAINT_3')
      AND CONSTRAINT_TYPE = 'CHECK'
    LIMIT 1
);
SET @drop_template_trigger_check = IF(
    @old_template_trigger_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `mission_templates` DROP CONSTRAINT `', @old_template_trigger_check, '`')
);
PREPARE stmt_drop_template_trigger_check FROM @drop_template_trigger_check;
EXECUTE stmt_drop_template_trigger_check;
DEALLOCATE PREPARE stmt_drop_template_trigger_check;

SET @add_template_trigger_check = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'mission_templates'
              AND CONSTRAINT_NAME = 'chk_mission_templates_trigger_event_values'
              AND CONSTRAINT_TYPE = 'CHECK'
        ),
        'ALTER TABLE `mission_templates` ADD CONSTRAINT `chk_mission_templates_trigger_event_values` CHECK (`trigger_event` IN (''QUIZ_COMPLETED'', ''LEARNING_ACTIVITY_RECORDED'', ''TOPIC_ITEM_CREATED'', ''SHOP_ITEM_PURCHASED'', ''BOOSTER_USED'', ''SRS_REVIEW_COMPLETED'', ''SCAN_WORD_SAVED''))',
        'SELECT 1'
    )
);
PREPARE stmt_add_template_trigger_check FROM @add_template_trigger_check;
EXECUTE stmt_add_template_trigger_check;
DEALLOCATE PREPARE stmt_add_template_trigger_check;

SET @old_daily_trigger_check = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_daily_missions'
      AND CONSTRAINT_NAME IN ('user_daily_missions_chk_3', 'CONSTRAINT_3')
      AND CONSTRAINT_TYPE = 'CHECK'
    LIMIT 1
);
SET @drop_daily_trigger_check = IF(
    @old_daily_trigger_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `user_daily_missions` DROP CONSTRAINT `', @old_daily_trigger_check, '`')
);
PREPARE stmt_drop_daily_trigger_check FROM @drop_daily_trigger_check;
EXECUTE stmt_drop_daily_trigger_check;
DEALLOCATE PREPARE stmt_drop_daily_trigger_check;

SET @add_daily_trigger_check = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_daily_missions'
              AND CONSTRAINT_NAME = 'chk_user_daily_missions_trigger_event_values'
              AND CONSTRAINT_TYPE = 'CHECK'
        ),
        'ALTER TABLE `user_daily_missions` ADD CONSTRAINT `chk_user_daily_missions_trigger_event_values` CHECK (`trigger_event` IN (''QUIZ_COMPLETED'', ''LEARNING_ACTIVITY_RECORDED'', ''TOPIC_ITEM_CREATED'', ''SHOP_ITEM_PURCHASED'', ''BOOSTER_USED'', ''SRS_REVIEW_COMPLETED'', ''SCAN_WORD_SAVED''))',
        'SELECT 1'
    )
);
PREPARE stmt_add_daily_trigger_check FROM @add_daily_trigger_check;
EXECUTE stmt_add_daily_trigger_check;
DEALLOCATE PREPARE stmt_add_daily_trigger_check;
