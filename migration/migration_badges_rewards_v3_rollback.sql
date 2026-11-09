-- ============================================================================
-- ROLLBACK: migration_badges_rewards_v3.sql (phần thưởng huy hiệu)
-- ============================================================================
-- Gỡ toàn bộ phần thưởng huy hiệu, đưa schema về trước v3:
--   1. Trừ lại Xu/XP đã cộng (users.coin/exp không xuống dưới 0 nếu người học đã tiêu Xu thưởng).
--   2. Xoá khung đã cấp từ huy hiệu (user_items khớp snapshot reward_item_granted = 1).
--   3. Xoá dòng sổ BADGE_REWARD, bỏ 'BADGE_REWARD' khỏi ENUM, bỏ các cột/CHECK v3.
-- Huy hiệu đã nhận (user_badges) vẫn giữ. Chạy SAU khi đã quay backend về bản không có thưởng.
-- ============================================================================

START TRANSACTION;

UPDATE `users` u
  JOIN (SELECT `user_id`, SUM(`amount`) AS total FROM `coin_transactions`
         WHERE `source_type` = 'BADGE_REWARD' GROUP BY `user_id`) t ON t.`user_id` = u.`id`
   SET u.`coin` = GREATEST(0, u.`coin` - t.total);

UPDATE `users` u
  JOIN (SELECT `user_id`, SUM(`amount`) AS total FROM `experience_logs`
         WHERE `source_type` = 'BADGE_REWARD' GROUP BY `user_id`) t ON t.`user_id` = u.`id`
   SET u.`exp` = GREATEST(0, u.`exp` - t.total);

DELETE ui FROM `user_items` ui
  JOIN `shop_items` si ON si.`id` = ui.`shop_item_id`
  JOIN `user_badges` ub ON ub.`user_id` = ui.`user_id` AND ub.`reward_item_code` = si.`code`
 WHERE ub.`reward_item_granted` = 1;

DELETE FROM `coin_transactions` WHERE `source_type` = 'BADGE_REWARD';
DELETE FROM `experience_logs` WHERE `source_type` = 'BADGE_REWARD';

COMMIT;

DROP PROCEDURE IF EXISTS `bdg_v3_rollback`;
DELIMITER //
CREATE PROCEDURE `bdg_v3_rollback`()
BEGIN
    DECLARE col_type TEXT;

    SELECT COLUMN_TYPE INTO col_type FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'coin_transactions' AND COLUMN_NAME = 'source_type';
    IF col_type LIKE '%,''BADGE_REWARD''%' THEN
        SET @ddl = CONCAT('ALTER TABLE `coin_transactions` MODIFY `source_type` ',
            REPLACE(col_type, ',''BADGE_REWARD''', ''), ' NOT NULL');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    SELECT COLUMN_TYPE INTO col_type FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'experience_logs' AND COLUMN_NAME = 'source_type';
    IF col_type LIKE '%,''BADGE_REWARD''%' THEN
        SET @ddl = CONCAT('ALTER TABLE `experience_logs` MODIFY `source_type` ',
            REPLACE(col_type, ',''BADGE_REWARD''', ''), ' NOT NULL');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND CONSTRAINT_NAME = 'chk_badges_reward') THEN
        ALTER TABLE `badges` DROP CHECK `chk_badges_reward`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'badges' AND COLUMN_NAME = 'reward_coin') THEN
        ALTER TABLE `badges` DROP COLUMN `reward_coin`, DROP COLUMN `reward_xp`, DROP COLUMN `reward_item_code`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'user_badges' AND COLUMN_NAME = 'reward_coin') THEN
        ALTER TABLE `user_badges` DROP COLUMN `reward_coin`, DROP COLUMN `reward_xp`,
            DROP COLUMN `reward_item_code`, DROP COLUMN `reward_item_granted`;
    END IF;
END //
DELIMITER ;

CALL bdg_v3_rollback();
DROP PROCEDURE `bdg_v3_rollback`;
