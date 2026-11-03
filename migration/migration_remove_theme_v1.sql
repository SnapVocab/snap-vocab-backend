-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION: GỠ VẬT PHẨM THEME (Bộ màu)
-- ============================================================================
-- Traceability: docs/decisions/shop.md (PD-SHX-06 bị thay thế 2026-11-02: bỏ THEME, chỉ giữ sáng/tối hệ thống).
--
-- Mục đích:
--   1. Xóa mọi vật phẩm `shop_items.type = 'THEME'` cùng bản ghi sở hữu (`user_items`, `user_inventories` cũ).
--      App chưa phát hành, chưa ai mua theme: không hoàn Coin.
--   2. Bỏ `uq_shop_items_theme_key` và cột `shop_items.theme_key`.
--
-- Thứ tự phát hành: chạy script này TRƯỚC khi deploy backend không còn THEME (enum ShopItemType không còn giá trị
-- THEME, Hibernate sẽ lỗi khi đọc một dòng THEME còn sót).
-- Idempotent: chạy lại nhiều lần an toàn.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- BƯỚC 1: Xóa dữ liệu THEME (bảng con trước, FK RESTRICT)
-- Bảng con có thể chưa tồn tại: trên remote, migration_remote_reconcile_v1 xóa `user_items` và Hibernate chỉ tạo lại
-- khi deploy backend (sau các migration).
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(COUNT(*) > 0, 'DELETE FROM `user_items` WHERE `item_type` = ''THEME''', 'SELECT 1')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_items'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) > 0,
        'DELETE FROM `user_inventories` WHERE `item_id` IN (SELECT `id` FROM `shop_items` WHERE `type` = ''THEME'')',
        'SELECT 1')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_inventories'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM `shop_items` WHERE `type` = 'THEME';

-- ----------------------------------------------------------------------------
-- BƯỚC 2: Bỏ unique key và cột theme_key
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `shop_items` DROP INDEX `uq_shop_items_theme_key`', 'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_items' AND INDEX_NAME = 'uq_shop_items_theme_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `shop_items` DROP COLUMN `theme_key`', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_items' AND COLUMN_NAME = 'theme_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
