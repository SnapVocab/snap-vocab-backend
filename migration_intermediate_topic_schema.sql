-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: INTRODUCE INDEPENDENT SCHEMA & INTERMEDIATE TOPIC_SCHEMA
-- Mục đích:
--   1. Tạo bảng `schemas` đại diện cho Schema độc lập (1 Schema - N Topics)
--   2. Sao chép dữ liệu schema hiện có từ `topic_schemas` sang `schemas`
--   3. Cập nhật khóa ngoại `schema_id` trong `schema_attribute_groups` trỏ tới `schemas(id)`
--   4. Bổ sung cột `schema_id` trong `topic_schemas` trỏ tới `schemas(id)`
--      (TopicSchema đóng vai trò là entity trung gian giữa Topic và Schema)
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- BƯỚC 1: Tạo bảng `schemas`
CREATE TABLE IF NOT EXISTS `schemas` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `description` TEXT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BƯỚC 2: Sao chép dữ liệu từ topic_schemas & topics sang bảng schemas
INSERT INTO `schemas` (`id`, `name`, `description`, `created_at`, `updated_at`)
SELECT 
    ts.id, 
    COALESCE(t.name, CONCAT('Schema ', ts.id)), 
    t.description, 
    ts.created_at, 
    ts.updated_at
FROM `topic_schemas` ts
LEFT JOIN `topics` t ON ts.topic_id = t.id;

-- BƯỚC 3: Cập nhật ràng buộc trong `schema_attribute_groups` trỏ sang `schemas`
ALTER TABLE `schema_attribute_groups` DROP FOREIGN KEY `fk_schema_attribute_groups_schema`;
ALTER TABLE `schema_attribute_groups`
    ADD CONSTRAINT `fk_schema_attribute_groups_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE CASCADE;

-- BƯỚC 4: Thêm cột `schema_id` vào bảng trung gian `topic_schemas`
ALTER TABLE `topic_schemas` ADD COLUMN `schema_id` BIGINT(20) NULL AFTER `topic_id`;

UPDATE `topic_schemas` SET `schema_id` = `id`;

ALTER TABLE `topic_schemas` MODIFY COLUMN `schema_id` BIGINT(20) NOT NULL;

ALTER TABLE `topic_schemas`
    ADD CONSTRAINT `fk_topic_schemas_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE RESTRICT;

CREATE INDEX `idx_topic_schemas_schema_id` ON `topic_schemas` (`schema_id`);

SET FOREIGN_KEY_CHECKS = 1;
