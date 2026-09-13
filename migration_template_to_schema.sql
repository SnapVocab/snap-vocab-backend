-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: BIND TEMPLATE TO SCHEMA & SYSTEM CODES
-- Mục đích:
--   1. Thêm `code` và `is_system` vào bảng `schemas` (System Schema code bất biến)
--   2. Đổi `templates` từ gắn với `topics` sang gắn với `schemas` (schema_id)
--   3. Thêm `code` và `is_default` vào bảng `templates` (cho các chế độ học: STANDARD, LISTENING, REVERSE...)
--   4. Thêm `active_template_id` vào bảng `topics`
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- BƯỚC 1: Cập nhật bảng `schemas`
ALTER TABLE `schemas`
    ADD COLUMN `code` VARCHAR(50) NULL AFTER `id`,
    ADD COLUMN `is_system` BIT(1) NOT NULL DEFAULT 0 AFTER `code`;

CREATE UNIQUE INDEX `uk_schemas_code` ON `schemas` (`code`);

-- BƯỚC 2: Cập nhật bảng `templates`
-- Kiểm tra nếu tồn tại khóa ngoại cũ tới topics thì xóa
SET @fk_drop = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS 
            WHERE CONSTRAINT_SCHEMA = DATABASE() 
              AND TABLE_NAME = 'templates' 
              AND CONSTRAINT_NAME = 'fk_templates_topic'
        ),
        'ALTER TABLE templates DROP FOREIGN KEY fk_templates_topic;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @fk_drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Thêm cột schema_id, code, is_default vào templates
ALTER TABLE `templates`
    ADD COLUMN `schema_id` BIGINT(20) NULL AFTER `id`,
    ADD COLUMN `code` VARCHAR(50) NULL AFTER `schema_id`,
    ADD COLUMN `is_default` BIT(1) NOT NULL DEFAULT 0 AFTER `name`;

-- Cập nhật schema_id cho các template hiện có (nếu có dữ liệu cũ dựa theo topic_schemas)
UPDATE `templates` t
INNER JOIN `topic_schemas` ts ON t.topic_id = ts.topic_id
SET t.schema_id = ts.schema_id
WHERE t.schema_id IS NULL;

-- Nếu vẫn còn template mồ côi thì gán vào schema đầu tiên
UPDATE `templates` SET `schema_id` = (SELECT MIN(id) FROM `schemas`) WHERE `schema_id` IS NULL;

ALTER TABLE `templates` MODIFY COLUMN `schema_id` BIGINT(20) NOT NULL;

-- Xóa cột topic_id trong templates nếu tồn tại
SET @col_drop = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE() 
              AND TABLE_NAME = 'templates' 
              AND COLUMN_NAME = 'topic_id'
        ),
        'ALTER TABLE templates DROP COLUMN topic_id;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @col_drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Tạo khóa ngoại templates -> schemas
ALTER TABLE `templates`
    ADD CONSTRAINT `fk_templates_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE CASCADE;

CREATE INDEX `idx_templates_schema_id` ON `templates` (`schema_id`);

-- BƯỚC 3: Cập nhật bảng `topics` bổ sung active_template_id
ALTER TABLE `topics`
    ADD COLUMN `active_template_id` BIGINT(20) NULL AFTER `description_translation`;

ALTER TABLE `topics`
    ADD CONSTRAINT `fk_topics_active_template` FOREIGN KEY (`active_template_id`) REFERENCES `templates` (`id`) ON DELETE SET NULL;

CREATE INDEX `idx_topics_active_template_id` ON `topics` (`active_template_id`);

SET FOREIGN_KEY_CHECKS = 1;
