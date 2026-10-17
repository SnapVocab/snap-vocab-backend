-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: QUIZ (FR-06, docs/decisions/quiz.md)
-- Mục đích:
--   1. Thêm cột `hidden` vào `template_fields` (binding ngữ nghĩa chỉ phía server,
--      không render; Quiz resolve qua semantic_role).
--   2. Tạo các bảng Quiz: quizzes, quiz_questions, quiz_question_options,
--      quiz_matching_tiles, quiz_matching_attempts.
-- Ghi chú:
--   Môi trường dùng `ddl-auto: update` sẽ tự tạo cột/bảng từ entity;
--   script này dành cho môi trường quản lý schema thủ công. Idempotent.
--   Binding ẩn (main.translation → NATIVE_TRANSLATION, examples.content →
--   EXAMPLE_SENTENCE) do SemanticBindingMigration thêm lúc khởi động, không ở đây.
-- ============================================================================

-- BƯỚC 1: template_fields.hidden
SET @add_hidden_col = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'template_fields'
              AND COLUMN_NAME = 'hidden'
        ),
        'ALTER TABLE `template_fields` ADD COLUMN `hidden` BIT(1) NOT NULL DEFAULT 0;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @add_hidden_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- BƯỚC 2: Quiz tables
CREATE TABLE IF NOT EXISTS `quizzes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `topic_name` VARCHAR(255) NOT NULL,
    `mode` VARCHAR(20) NOT NULL,
    `direction` VARCHAR(10) NULL,
    `status` VARCHAR(20) NOT NULL,
    `requested_count` INT NOT NULL,
    `create_idempotency_key` VARCHAR(128) NOT NULL,
    `total_units` INT NULL,
    `correct_count` INT NULL,
    `incorrect_count` INT NULL,
    `completed_at` DATETIME(6) NULL,
    `cancelled_at` DATETIME(6) NULL,
    `version` BIGINT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quiz_user_create_key` (`user_id`, `create_idempotency_key`),
    KEY `idx_quiz_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_quizzes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quiz_questions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `quiz_id` BIGINT NOT NULL,
    `position` INT NOT NULL,
    `round_no` INT NULL,
    `topic_item_id` BIGINT NULL,
    `prompt` TEXT NOT NULL,
    `correct_answer` TEXT NOT NULL,
    `correct_option_key` VARCHAR(64) NULL,
    `left_key` VARCHAR(64) NULL,
    `left_display_order` INT NULL,
    `correct_right_key` VARCHAR(64) NULL,
    `outcome` VARCHAR(20) NOT NULL,
    `resolved` BIT(1) NOT NULL,
    `resolved_at` DATETIME(6) NULL,
    `attempt_count` INT NOT NULL,
    `learner_answer` TEXT NULL,
    `check_idempotency_key` VARCHAR(128) NULL,
    `checked_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quiz_question_position` (`quiz_id`, `position`),
    UNIQUE KEY `uk_quiz_question_left_key` (`quiz_id`, `left_key`),
    CONSTRAINT `fk_quiz_questions_quiz` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quiz_question_options` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `question_id` BIGINT NOT NULL,
    `option_key` VARCHAR(64) NOT NULL,
    `label` TEXT NOT NULL,
    `display_order` INT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quiz_option_key` (`question_id`, `option_key`),
    CONSTRAINT `fk_quiz_options_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quiz_matching_tiles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `quiz_id` BIGINT NOT NULL,
    `round_no` INT NOT NULL,
    `tile_key` VARCHAR(64) NOT NULL,
    `label` TEXT NOT NULL,
    `display_order` INT NOT NULL,
    `matched` BIT(1) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quiz_tile_key` (`quiz_id`, `tile_key`),
    CONSTRAINT `fk_quiz_tiles_quiz` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quiz_matching_attempts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `quiz_id` BIGINT NOT NULL,
    `question_id` BIGINT NOT NULL,
    `round_no` INT NOT NULL,
    `left_key` VARCHAR(64) NOT NULL,
    `right_key` VARCHAR(64) NOT NULL,
    `correct` BIT(1) NOT NULL,
    `attempt_no` INT NOT NULL,
    `round_matched_count` INT NOT NULL,
    `round_total_pairs` INT NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quiz_attempt_key` (`quiz_id`, `idempotency_key`),
    CONSTRAINT `fk_quiz_attempts_quiz` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`),
    CONSTRAINT `fk_quiz_attempts_question` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
