-- Current streak has a baseline of 1, including users who have not studied yet.
-- Longest streak and last_studied_at retain the actual study history.
UPDATE `users` SET `streak_days` = 1 WHERE `streak_days` IS NULL OR `streak_days` < 1;
ALTER TABLE `users` MODIFY COLUMN `streak_days` INT NOT NULL DEFAULT 1;
