-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: BADGES — v3 (phần thưởng huy hiệu)
-- ============================================================================
-- Traceability:
--   plan/badge-reward/badge_reward_plan.md, PD-BDG-14 (PO chốt 2026-11-08)
--
-- Mục đích:
--   1. badges: reward_coin, reward_xp (NOT NULL DEFAULT 0), reward_item_code (NULL) + CHECK.
--   2. user_badges: snapshot phần thưởng lúc nhận (NULL = nhận trước khi có thưởng, không cộng bù).
--   3. coin_transactions.source_type, experience_logs.source_type: thêm 'BADGE_REWARD' vào ENUM do Hibernate tạo.
--   4. Giá trị seed: mốc 1 = 20 Xu + 50 XP, mốc 2 = 60 Xu + 150 XP, mốc 3 = 300 XP (khung do Admin gắn sau).
--
-- Ghi chú:
--   - Idempotent: chỉ thêm cột/giá trị khi chưa có (ddl-auto: update ở dev có thể đã tự thêm cột).
--   - ENUM mới dựng từ COLUMN_TYPE hiện tại + 'BADGE_REWARD', nên giữ nguyên mọi giá trị cũ của từng DB.
--   - UPDATE chỉ chạm badge còn 0/0, nên chạy lại không ghi đè giá trị Admin đã sửa.
--   - Chạy TRƯỚC khi deploy backend (prod ddl-auto: validate). Gỡ: migration_badges_rewards_v3_rollback.sql.
-- ============================================================================

DROP PROCEDURE IF EXISTS `bdg_v3_ddl`;
DELIMITER //
CREATE PROCEDURE `bdg_v3_ddl`()
BEGIN
    DECLARE col_type TEXT;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'reward_coin') THEN
        ALTER TABLE `badges`
            ADD COLUMN `reward_coin` INT NOT NULL DEFAULT 0,
            ADD COLUMN `reward_xp` INT NOT NULL DEFAULT 0,
            ADD COLUMN `reward_item_code` VARCHAR(64) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND CONSTRAINT_NAME = 'chk_badges_reward') THEN
        ALTER TABLE `badges` ADD CONSTRAINT `chk_badges_reward` CHECK (`reward_coin` >= 0 AND `reward_xp` >= 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'user_badges' AND COLUMN_NAME = 'reward_coin') THEN
        ALTER TABLE `user_badges`
            ADD COLUMN `reward_coin` INT NULL,
            ADD COLUMN `reward_xp` INT NULL,
            ADD COLUMN `reward_item_code` VARCHAR(64) NULL,
            ADD COLUMN `reward_item_granted` BIT(1) NULL;
    END IF;

    SELECT COLUMN_TYPE INTO col_type FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'coin_transactions' AND COLUMN_NAME = 'source_type';
    IF col_type LIKE 'enum(%' AND col_type NOT LIKE '%''BADGE_REWARD''%' THEN
        SET @ddl = CONCAT('ALTER TABLE `coin_transactions` MODIFY `source_type` ',
            LEFT(col_type, CHAR_LENGTH(col_type) - 1), ',''BADGE_REWARD'') NOT NULL');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    SELECT COLUMN_TYPE INTO col_type FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'experience_logs' AND COLUMN_NAME = 'source_type';
    IF col_type LIKE 'enum(%' AND col_type NOT LIKE '%''BADGE_REWARD''%' THEN
        SET @ddl = CONCAT('ALTER TABLE `experience_logs` MODIFY `source_type` ',
            LEFT(col_type, CHAR_LENGTH(col_type) - 1), ',''BADGE_REWARD'') NOT NULL');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bdg_v3_ddl();
DROP PROCEDURE `bdg_v3_ddl`;

UPDATE `badges` SET `reward_coin` = 20, `reward_xp` = 50
 WHERE `code` IN ('STREAK_7', 'QUIZ_1', 'PERFECT_1', 'WORDS_10') AND `reward_coin` = 0 AND `reward_xp` = 0;
UPDATE `badges` SET `reward_coin` = 60, `reward_xp` = 150
 WHERE `code` IN ('STREAK_30', 'QUIZ_10', 'PERFECT_10', 'WORDS_100') AND `reward_coin` = 0 AND `reward_xp` = 0;
UPDATE `badges` SET `reward_xp` = 300
 WHERE `code` IN ('STREAK_100', 'QUIZ_50', 'PERFECT_50', 'WORDS_500') AND `reward_coin` = 0 AND `reward_xp` = 0;
