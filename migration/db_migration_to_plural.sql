-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT
-- Mục đích:
--   1. Chuyển dữ liệu từ các bảng số ít sang bảng số nhiều mới tạo
--   2. Xóa các bảng cũ / bảng deprecated (note, card, deck, review_log, flashcard)
--   3. Xóa các bảng số ít sau khi đã di chuyển dữ liệu thành công
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- BƯỚC 1: Xóa các Foreign Key cũ đang trỏ tới các bảng số ít hoặc bảng sắp xóa
DROP PROCEDURE IF EXISTS DropOldForeignKeys;
DELIMITER $$
CREATE PROCEDURE DropOldForeignKeys()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE drop_stmt VARCHAR(500);
    DECLARE cur CURSOR FOR 
        SELECT CONCAT('ALTER TABLE `', TABLE_NAME, '` DROP FOREIGN KEY `', CONSTRAINT_NAME, '`;')
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE REFERENCED_TABLE_NAME IN (
            'user', 'authority', 'user_authority', 
            'word', 'definition', 'word_definition', 
            'card', 'note', 'deck', 'flashcard', 'review_log'
        )
        AND TABLE_SCHEMA = DATABASE();
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO drop_stmt;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @sql = drop_stmt;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL DropOldForeignKeys();
DROP PROCEDURE IF EXISTS DropOldForeignKeys;


-- BƯỚC 2: Làm sạch các bảng số nhiều vừa được Hibernate tự động tạo (đang rỗng hoặc chỉ có dữ liệu mẫu)
TRUNCATE TABLE `user_authorities`;
TRUNCATE TABLE `word_definitions`;
TRUNCATE TABLE `word_relations`;
TRUNCATE TABLE `translations`;
TRUNCATE TABLE `pronunciations`;
TRUNCATE TABLE `definitions`;
TRUNCATE TABLE `words`;
TRUNCATE TABLE `users`;
TRUNCATE TABLE `authorities`;


-- BƯỚC 3: Di chuyển dữ liệu từ bảng cũ sang bảng mới (giữ nguyên toàn bộ ID và quan hệ)

-- 3.1. Di chuyển authorities
INSERT INTO `authorities` (`name`)
SELECT `name` FROM `authority`;

-- 3.2. Di chuyển users
INSERT INTO `users` (
    `id`, `created_at`, `updated_at`, `activated`, `avatar_url`, `bio`, 
    `coin`, `email`, `exp`, `first_name`, `last_name`, `last_studied_at`, 
    `learning_language`, `native_language`, `password_hash`, `streak_days`
)
SELECT 
    `id`, COALESCE(`last_studied_at`, NOW(6)), NOW(6), `activated`, `avatar_url`, `bio`, 
    `coin`, `email`, `exp`, `first_name`, `last_name`, `last_studied_at`, 
    `learning_language`, `native_language`, `password_hash`, `streak_days`
FROM `user`;

-- 3.3. Di chuyển user_authorities
INSERT INTO `user_authorities` (`user_id`, `authority_name`)
SELECT `user_id`, `authority_name` FROM `user_authority`;

-- 3.4. Di chuyển words
INSERT INTO `words` (`id`, `created_at`, `lang_code`, `word`)
SELECT `id`, NOW(6), `lang_code`, `word` FROM `word`;

-- 3.5. Di chuyển definitions
INSERT INTO `definitions` (`id`, `created_at`, `definition`, `definition_lang`, `links`, `pos`, `sub_pos`)
SELECT `id`, NOW(6), `definition`, `definition_lang`, `links`, `pos`, `sub_pos` FROM `definition`;

-- 3.6. Di chuyển word_definitions
INSERT INTO `word_definitions` (`id`, `created_at`, `example`, `definition_id`, `word_id`)
SELECT `id`, NOW(6), `example`, `definition_id`, `word_id` FROM `word_definition`;

-- 3.7. Di chuyển word_relations
INSERT INTO `word_relations` (`id`, `created_at`, `related_word`, `relation_type`, `word_id`)
SELECT `id`, NOW(6), `related_word`, `relation_type`, `word_id` FROM `word_relation`;

-- 3.8. Di chuyển translations
INSERT INTO `translations` (`id`, `created_at`, `lang_code`, `translation`, `word_id`)
SELECT `id`, NOW(6), `lang_code`, `translation`, `word_id` FROM `translation`;

-- 3.9. Di chuyển pronunciations
INSERT INTO `pronunciations` (`id`, `created_at`, `ipa`, `region`, `word_id`)
SELECT `id`, NOW(6), `ipa`, `region`, `word_id` FROM `pronunciation`;


-- BƯỚC 4: Xóa các bảng cũ không còn sử dụng (card, note, deck, review_log, flashcard)
DROP TABLE IF EXISTS `review_log`;
DROP TABLE IF EXISTS `card`;
DROP TABLE IF EXISTS `note_meaning`;
DROP TABLE IF EXISTS `note_pronunciation`;
DROP TABLE IF EXISTS `note`;
DROP TABLE IF EXISTS `deck`;
DROP TABLE IF EXISTS `flashcard`;


-- BƯỚC 5: Xóa các bảng số ít cũ (vì dữ liệu đã được sao chép an toàn sang bảng số nhiều)
DROP TABLE IF EXISTS `word_definition`;
DROP TABLE IF EXISTS `word_relation`;
DROP TABLE IF EXISTS `translation`;
DROP TABLE IF EXISTS `pronunciation`;
DROP TABLE IF EXISTS `definition`;
DROP TABLE IF EXISTS `word`;
DROP TABLE IF EXISTS `user_authority`;
DROP TABLE IF EXISTS `authority`;
DROP TABLE IF EXISTS `user`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- HOÀN TẤT! Khi khởi động lại ứng dụng Spring Boot với ddl-auto: update,
-- Hibernate sẽ tự động tạo lại các Foreign Key chuẩn trỏ vào các bảng số nhiều mới.
-- ============================================================================
