-- Add the eligibility rule used by study-card-targeted mission templates.
-- Safe to run on a fresh schema and on a database that already has v1 applied.

SET @old_template_eligibility_check = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mission_templates'
      AND CONSTRAINT_NAME IN ('mission_templates_chk_2', 'CONSTRAINT_2')
      AND CONSTRAINT_TYPE = 'CHECK'
    LIMIT 1
);
SET @drop_template_eligibility_check = IF(
    @old_template_eligibility_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `mission_templates` DROP CONSTRAINT `', @old_template_eligibility_check, '`')
);
PREPARE stmt_drop_template_eligibility_check FROM @drop_template_eligibility_check;
EXECUTE stmt_drop_template_eligibility_check;
DEALLOCATE PREPARE stmt_drop_template_eligibility_check;

SET @add_template_eligibility_check = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'mission_templates'
              AND CONSTRAINT_NAME = 'chk_mission_templates_eligibility_rule_values'
              AND CONSTRAINT_TYPE = 'CHECK'
        ),
        'ALTER TABLE `mission_templates` ADD CONSTRAINT `chk_mission_templates_eligibility_rule_values` CHECK (`eligibility_rule` IN (''NONE'', ''HAS_IDLE_XP_BOOSTER'', ''HAS_PURCHASABLE_ITEM'', ''HAS_STUDY_CARDS_FOR_TARGET''))',
        'SELECT 1'
    )
);
PREPARE stmt_add_template_eligibility_check FROM @add_template_eligibility_check;
EXECUTE stmt_add_template_eligibility_check;
DEALLOCATE PREPARE stmt_add_template_eligibility_check;
