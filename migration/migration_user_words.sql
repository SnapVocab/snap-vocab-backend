-- Personal vocabulary, independent of TopicItem and scan history.
-- Apply before deploying to environments using ddl-auto: validate.
CREATE TABLE IF NOT EXISTS `user_words` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `word_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_words_user_word` (`user_id`, `word_id`),
    CONSTRAINT `fk_user_words_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_words_word` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
