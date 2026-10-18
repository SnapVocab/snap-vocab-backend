-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: BADGES — v1
-- ============================================================================
-- Traceability:
--   SS-13 Badges: docs/decisions/badges.md §7 (schema), §7.4 (seed / SQL)
--   Database Specification: docs/db/database.md §4
--
-- Mục đích:
--   Tạo 3 bảng badges, user_badges, user_badge_word_credits (FK RESTRICT, UNIQUE, CHECK, index).
--
-- Ghi chú:
--   - Kiểu cột khớp DDL Hibernate sinh từ entity vn.ptit.snapvocab.badge.domain.* (BIT(1) cho Boolean trên MySQL).
--   - DDL của MySQL tự COMMIT ngầm, không rollback được; script idempotent (CREATE TABLE IF NOT EXISTS).
--   - Script KHÔNG seed catalog: BadgeCatalogSeeder chèn 12 badge §4.1 lúc khởi động (chỉ code chưa có).
--     Sửa name/description/icon_key/sort_order/is_active của badge đã có: viết migration UPDATE riêng.
--     Không bao giờ UPDATE code/metric/threshold (badges.md §5.3).
--   - Phải chạy trước khi deploy lên môi trường ddl-auto: validate (staging).
--   - Yêu cầu bảng users(id) đã tồn tại.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `badges` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NOT NULL,
    `metric` VARCHAR(30) NOT NULL,
    `threshold` INT NOT NULL,
    `icon_key` VARCHAR(50) NOT NULL,
    `sort_order` INT NOT NULL,
    `is_active` BIT(1) NOT NULL DEFAULT b'1',
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_badges_code` (`code`),
    UNIQUE KEY `uq_badges_metric_threshold` (`metric`, `threshold`),
    CONSTRAINT `chk_badges_threshold` CHECK (`threshold` >= 1),
    CHECK (`metric` IN ('LONGEST_STREAK_DAYS', 'QUIZ_COMPLETED', 'PERFECT_QUIZ', 'DISTINCT_WORDS_ADDED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_badges` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `badge_id` BIGINT NOT NULL,
    `seen_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_badges_badge` (`user_id`, `badge_id`),
    KEY `idx_user_badges_badge` (`badge_id`),
    CONSTRAINT `fk_user_badges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_badges_badge` FOREIGN KEY (`badge_id`) REFERENCES `badges` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_badge_word_credits` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `word_hash` BINARY(32) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_badge_word_credits_word` (`user_id`, `word_hash`),
    CONSTRAINT `fk_user_badge_word_credits_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;
