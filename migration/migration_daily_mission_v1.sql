-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: DAILY MISSION — v1
-- ============================================================================
-- Traceability:
--   SS-13 Daily Mission: docs/decisions/daily_mission.md §7 (schema), §4.2 (catalog invariant), §7.7 (seed / SQL)
--   Database Specification: docs/db/database.md §4
--   REL-DM-01 (Proposed, chưa được PO duyệt): migration có version cho production.
--
-- Mục đích:
--   1. Tạo 5 bảng: mission_templates, user_daily_missions, user_daily_mission_progress_logs,
--      user_daily_mission_claims, user_word_credits (FK RESTRICT, UNIQUE, CHECK, index).
--   2. Tạo procedure dm_assert_catalog_invariant(): SIGNAL lỗi khi catalog active vi phạm invariant §4.2.
--      Mọi migration sửa catalog gọi procedure này trong cùng transaction trước COMMIT
--      (mẫu: migration_daily_mission_catalog_template.sql).
--
-- Ghi chú:
--   - Kiểu cột khớp DDL Hibernate sinh từ entity vn.ptit.snapvocab.mission.domain.* (BIT(1) là kiểu Hibernate dùng
--     cho Boolean trên MySQL); được kiểm chứng bằng MissionMigrationScriptTest trên MySQL local.
--   - DDL của MySQL tự COMMIT ngầm: script này KHÔNG rollback được theo transaction. Nó idempotent
--     (CREATE TABLE IF NOT EXISTS, DROP/CREATE PROCEDURE) nên chạy lại an toàn sau khi sửa lỗi.
--   - Script KHÔNG seed catalog: tên UI của 3 template còn chờ COPY-DM-01.
--   - Chưa áp vào production. Trước khi đổi production sang ddl-auto: validate phải kiểm tra toàn bộ schema hiện hữu
--     (REL-DM-01); script này chỉ phủ 5 bảng mission.
--   - Yêu cầu bảng users(id) đã tồn tại.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `mission_templates` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NOT NULL,
    `category` VARCHAR(20) NOT NULL,
    `trigger_event` VARCHAR(40) NOT NULL,
    `min_quiz_accuracy_percent` INT NULL,
    `quiz_requested_count` INT NULL,
    `target_value` INT NOT NULL,
    `reward_coin` INT NOT NULL,
    `reward_xp` INT NOT NULL,
    `reward_item_code` VARCHAR(64) NULL,
    `reward_item_fallback_coin` INT NULL,
    `weight` INT NOT NULL DEFAULT 1,
    `eligibility_rule` VARCHAR(40) NOT NULL DEFAULT 'NONE',
    `is_active` BIT(1) NOT NULL DEFAULT b'0',
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mission_templates_code` (`code`),
    CONSTRAINT `chk_mission_templates_quiz_filters` CHECK (
        (`min_quiz_accuracy_percent` IS NULL OR `min_quiz_accuracy_percent` BETWEEN 1 AND 100)
        AND (`quiz_requested_count` IS NULL OR `quiz_requested_count` IN (5, 10, 20))
        AND (`trigger_event` = 'QUIZ_COMPLETED' OR (`min_quiz_accuracy_percent` IS NULL AND `quiz_requested_count` IS NULL))),
    CONSTRAINT `chk_mission_templates_reward` CHECK (
        `reward_coin` >= 0 AND `reward_xp` >= 0
        AND (`reward_item_fallback_coin` IS NULL OR `reward_item_fallback_coin` >= 0)
        AND ((`reward_item_code` IS NULL) = (`reward_item_fallback_coin` IS NULL))
        AND (`reward_coin` > 0 OR `reward_xp` > 0 OR `reward_item_code` IS NOT NULL)),
    CONSTRAINT `chk_mission_templates_target` CHECK (`target_value` >= 1),
    CONSTRAINT `chk_mission_templates_weight` CHECK (`weight` >= 1),
    -- Enum domains, unnamed like Hibernate's (MySQL names them mission_templates_chk_1..3 in this order).
    CHECK (`category` IN ('SCAN', 'VOCABULARY', 'FLASHCARD', 'SRS', 'QUIZ', 'RETENTION', 'SHOP')),
    CHECK (`eligibility_rule` IN ('NONE', 'HAS_IDLE_XP_BOOSTER', 'HAS_PURCHASABLE_ITEM', 'HAS_STUDY_CARDS_FOR_TARGET')),
    CHECK (`trigger_event` IN ('QUIZ_COMPLETED', 'LEARNING_ACTIVITY_RECORDED', 'TOPIC_ITEM_CREATED', 'SHOP_ITEM_PURCHASED', 'BOOSTER_USED', 'SRS_REVIEW_COMPLETED', 'SCAN_WORD_SAVED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_daily_missions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `mission_template_id` BIGINT NOT NULL,
    `mission_date` DATE NOT NULL,
    `slot` TINYINT NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NOT NULL,
    `category` VARCHAR(20) NOT NULL,
    `trigger_event` VARCHAR(40) NOT NULL,
    `min_quiz_accuracy_percent` INT NULL,
    `quiz_requested_count` INT NULL,
    `target_value` INT NOT NULL,
    `reward_coin` INT NOT NULL,
    `reward_xp` INT NOT NULL,
    `reward_item_code` VARCHAR(64) NULL,
    `reward_item_fallback_coin` INT NULL,
    `current_progress` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL,
    `completed_at` DATETIME(6) NULL,
    `claimed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_daily_missions_slot` (`user_id`, `mission_date`, `slot`),
    UNIQUE KEY `uq_user_daily_missions_template` (`user_id`, `mission_date`, `mission_template_id`),
    KEY `idx_user_daily_missions_template` (`mission_template_id`),
    CONSTRAINT `fk_user_daily_missions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_daily_missions_template` FOREIGN KEY (`mission_template_id`) REFERENCES `mission_templates` (`id`),
    CONSTRAINT `chk_user_daily_missions_slot` CHECK (`slot` BETWEEN 1 AND 5),
    CONSTRAINT `chk_user_daily_missions_target` CHECK (`target_value` >= 1),
    CONSTRAINT `chk_user_daily_missions_progress` CHECK (`current_progress` >= 0 AND `current_progress` <= `target_value`),
    CONSTRAINT `chk_user_daily_missions_status` CHECK (`status` IN ('IN_PROGRESS', 'COMPLETED', 'CLAIMED')),
    CONSTRAINT `chk_user_daily_missions_completed` CHECK (
        (`status` = 'IN_PROGRESS') = (`completed_at` IS NULL)
        AND (`status` = 'IN_PROGRESS' OR `current_progress` = `target_value`)),
    CONSTRAINT `chk_user_daily_missions_claimed` CHECK ((`status` = 'CLAIMED') = (`claimed_at` IS NOT NULL)),
    CONSTRAINT `chk_user_daily_missions_quiz_filters` CHECK (
        (`min_quiz_accuracy_percent` IS NULL OR `min_quiz_accuracy_percent` BETWEEN 1 AND 100)
        AND (`quiz_requested_count` IS NULL OR `quiz_requested_count` IN (5, 10, 20))
        AND (`trigger_event` = 'QUIZ_COMPLETED' OR (`min_quiz_accuracy_percent` IS NULL AND `quiz_requested_count` IS NULL))),
    CONSTRAINT `chk_user_daily_missions_reward` CHECK (
        `reward_coin` >= 0 AND `reward_xp` >= 0
        AND (`reward_item_fallback_coin` IS NULL OR `reward_item_fallback_coin` >= 0)
        AND ((`reward_item_code` IS NULL) = (`reward_item_fallback_coin` IS NULL))
        AND (`reward_coin` > 0 OR `reward_xp` > 0 OR `reward_item_code` IS NOT NULL)),
    -- Enum domains, unnamed like Hibernate's (user_daily_missions_chk_1..3 in this order).
    CHECK (`category` IN ('SCAN', 'VOCABULARY', 'FLASHCARD', 'SRS', 'QUIZ', 'RETENTION', 'SHOP')),
    CHECK (`status` IN ('IN_PROGRESS', 'COMPLETED', 'CLAIMED')),
    CHECK (`trigger_event` IN ('QUIZ_COMPLETED', 'LEARNING_ACTIVITY_RECORDED', 'TOPIC_ITEM_CREATED', 'SHOP_ITEM_PURCHASED', 'BOOSTER_USED', 'SRS_REVIEW_COMPLETED', 'SCAN_WORD_SAVED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_daily_mission_progress_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_daily_mission_id` BIGINT NOT NULL,
    `source_key` VARCHAR(191) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mission_progress_logs_source` (`user_daily_mission_id`, `source_key`),
    CONSTRAINT `fk_mission_progress_logs_mission` FOREIGN KEY (`user_daily_mission_id`) REFERENCES `user_daily_missions` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_daily_mission_claims` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_daily_mission_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `reward_coin` INT NOT NULL,
    `reward_xp` INT NOT NULL,
    `reward_item_code` VARCHAR(64) NULL,
    `granted_item_quantity` INT NOT NULL DEFAULT 0,
    `fallback_coin` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mission_claims_mission` (`user_daily_mission_id`),
    UNIQUE KEY `uq_mission_claims_key` (`user_id`, `idempotency_key`),
    CONSTRAINT `fk_mission_claims_mission` FOREIGN KEY (`user_daily_mission_id`) REFERENCES `user_daily_missions` (`id`),
    CONSTRAINT `fk_mission_claims_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `chk_mission_claims_amounts` CHECK (
        `reward_coin` >= 0 AND `reward_xp` >= 0 AND `granted_item_quantity` >= 0 AND `fallback_coin` >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_word_credits` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `word_hash` BINARY(32) NOT NULL,
    `user_daily_mission_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_word_credits_word` (`user_id`, `word_hash`),
    KEY `idx_user_word_credits_mission` (`user_daily_mission_id`),
    CONSTRAINT `fk_user_word_credits_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_word_credits_mission` FOREIGN KEY (`user_daily_mission_id`) REFERENCES `user_daily_missions` (`id`)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Invariant catalog (daily_mission.md §4.2), cùng công thức MissionTracker.satisfiesInvariant:
-- trong các template active có eligibility_rule = 'NONE': core >= 4 VÀ (slot-5 >= 1 HOẶC core >= 5).
-- ----------------------------------------------------------------------------
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
