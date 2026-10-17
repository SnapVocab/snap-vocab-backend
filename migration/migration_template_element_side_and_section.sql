-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: TEMPLATE ELEMENT CARD SIDE & SECTION METADATA
-- Mục đích:
--   1. Thêm cột `side` ('FRONT' / 'BACK') vào bảng `template_elements`
--   2. Thêm cột `section_label` (tên phần hiển thị) vào bảng `template_elements`
--   3. Thêm cột `repeatable` (cờ lặp lại 1-n) vào bảng `template_elements`
--   4. Migrate dữ liệu cũ: Phân định FRONT / BACK cho các phần tử hiện tại
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- BƯỚC 1: Bổ sung các cột mới vào bảng `template_elements` nếu chưa có
SET @add_side_col = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE() 
              AND TABLE_NAME = 'template_elements' 
              AND COLUMN_NAME = 'side'
        ),
        'ALTER TABLE `template_elements` ADD COLUMN `side` VARCHAR(10) NOT NULL DEFAULT \'FRONT\' AFTER `type`;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @add_side_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_section_label_col = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE() 
              AND TABLE_NAME = 'template_elements' 
              AND COLUMN_NAME = 'section_label'
        ),
        'ALTER TABLE `template_elements` ADD COLUMN `section_label` VARCHAR(100) NULL AFTER `side`;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @add_section_label_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_repeatable_col = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = DATABASE() 
              AND TABLE_NAME = 'template_elements' 
              AND COLUMN_NAME = 'repeatable'
        ),
        'ALTER TABLE `template_elements` ADD COLUMN `repeatable` BIT(1) NULL DEFAULT 0 AFTER `section_label`;',
        'SELECT 1;'
    )
);
PREPARE stmt FROM @add_repeatable_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- BƯỚC 2: Migrate dữ liệu cũ
-- Mặc định gán các phần tử nằm sau SECTION_BREAK đầu tiên của template sang `side = 'BACK'`
UPDATE `template_elements` te
JOIN (
    SELECT template_id, MIN(position) AS break_pos
    FROM `template_elements`
    WHERE type = 'SECTION_BREAK'
    GROUP BY template_id
) breaks ON te.template_id = breaks.template_id
SET te.side = 'BACK'
WHERE te.position >= breaks.break_pos;

-- Cập nhật section_label và repeatable cho các SECTION_BREAK
UPDATE `template_elements`
SET section_label = 'Nghĩa & Ví dụ',
    repeatable = 1
WHERE type = 'SECTION_BREAK' AND side = 'BACK' AND (section_label IS NULL OR section_label = '');

UPDATE `template_elements`
SET section_label = 'Thông tin chung',
    repeatable = 0
WHERE type = 'SECTION_BREAK' AND side = 'FRONT' AND (section_label IS NULL OR section_label = '');

SET FOREIGN_KEY_CHECKS = 1;

