-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: INTRODUCE SCHEMA ENTITY & RENAME EAV
-- Mục đích:
--   1. Tạo bảng `topic_schemas` đại diện cho Schema Entity (1 Topic - 1 Schema)
--   2. Tạo dữ liệu schema mặc định cho các Topic hiện có
--   3. Đổi tên `topic_attribute_groups` -> `schema_attribute_groups`
--   4. Đổi quan hệ `topic_id` -> `schema_id` trong `schema_attribute_groups`
--   5. Đổi tên `topic_attributes` -> `schema_attributes`
--   6. Đổi cột `topic_attribute_id` -> `schema_attribute_id` trong
--      `topic_item_attribute_values` và `template_fields`
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- BƯỚC 1: Tạo bảng `topic_schemas`
CREATE TABLE IF NOT EXISTS `topic_schemas` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
    `topic_id` BIGINT(20) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_topic_schema_topic_id` (`topic_id`),
    CONSTRAINT `fk_topic_schemas_topic` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BƯỚC 2: Tạo bản ghi schema cho tất cả các Topic hiện có
INSERT INTO `topic_schemas` (`topic_id`, `created_at`, `updated_at`)
SELECT `id`, COALESCE(`created_at`, NOW(6)), COALESCE(`updated_at`, NOW(6))
FROM `topics`
ORDER BY `id`;

-- BƯỚC 3: Đổi tên bảng `topic_attribute_groups` -> `schema_attribute_groups`
RENAME TABLE `topic_attribute_groups` TO `schema_attribute_groups`;

-- BƯỚC 4: Thêm và điền cột `schema_id` cho `schema_attribute_groups`
ALTER TABLE `schema_attribute_groups` ADD COLUMN `schema_id` BIGINT(20) NULL AFTER `id`;

UPDATE `schema_attribute_groups` sag
JOIN `topic_schemas` ts ON sag.topic_id = ts.topic_id
SET sag.schema_id = ts.id;

ALTER TABLE `schema_attribute_groups` MODIFY COLUMN `schema_id` BIGINT(20) NOT NULL;

-- BƯỚC 5: Cập nhật ràng buộc và xóa cột `topic_id` cũ trong `schema_attribute_groups`
ALTER TABLE `schema_attribute_groups` DROP FOREIGN KEY `FK25gramx39fmucjfqbwg4rkef9`;
ALTER TABLE `schema_attribute_groups` DROP INDEX `uq_topic_group_name`;
ALTER TABLE `schema_attribute_groups` DROP COLUMN `topic_id`;

ALTER TABLE `schema_attribute_groups`
    ADD CONSTRAINT `uq_schema_group_name` UNIQUE (`schema_id`, `name`),
    ADD CONSTRAINT `fk_schema_attribute_groups_schema` FOREIGN KEY (`schema_id`) REFERENCES `topic_schemas` (`id`) ON DELETE CASCADE;

-- BƯỚC 6: Đổi tên bảng `topic_attributes` -> `schema_attributes`
RENAME TABLE `topic_attributes` TO `schema_attributes`;

-- BƯỚC 7: Đổi tên cột `topic_attribute_id` -> `schema_attribute_id` trong `topic_item_attribute_values`
ALTER TABLE `topic_item_attribute_values` DROP FOREIGN KEY `FK4pld595129inv6d2jd73qxox5`;
ALTER TABLE `topic_item_attribute_values` DROP INDEX `uq_group_instance_attribute`;
ALTER TABLE `topic_item_attribute_values` CHANGE COLUMN `topic_attribute_id` `schema_attribute_id` BIGINT(20) NOT NULL;

ALTER TABLE `topic_item_attribute_values`
    ADD CONSTRAINT `uq_group_instance_attribute` UNIQUE (`group_instance_id`, `schema_attribute_id`),
    ADD CONSTRAINT `fk_tiav_schema_attribute` FOREIGN KEY (`schema_attribute_id`) REFERENCES `schema_attributes` (`id`) ON DELETE CASCADE;

-- BƯỚC 8: Đổi tên cột `topic_attribute_id` -> `schema_attribute_id` trong `template_fields`
ALTER TABLE `template_fields` DROP FOREIGN KEY `FK4aoxwi6y2ui3wd5ethgq43o4y`;
ALTER TABLE `template_fields` CHANGE COLUMN `topic_attribute_id` `schema_attribute_id` BIGINT(20) NOT NULL;

ALTER TABLE `template_fields`
    ADD CONSTRAINT `fk_template_fields_schema_attribute` FOREIGN KEY (`schema_attribute_id`) REFERENCES `schema_attributes` (`id`) ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS = 1;
