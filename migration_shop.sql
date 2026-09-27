-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: SHOP, INVENTORY, XP BOOSTER & WALLET
-- ============================================================================
-- Traceability:
--   FR-09.06, FR-09.07 (docs/spec/specs.md)
--   BF-12 (docs/spec/buss_mainflow.md)
--   Canonical Architecture Decision: docs/decisions/shop.md (§3, §7, §9)
--   Database Specification: docs/db/database.md (§Gamification & Shop)
--
-- Mục đích:
--   1. Xóa bảng skeleton cũ `user_inventories` (được thay thế bằng `user_items`).
--   2. Tạo / chuẩn hóa bảng `shop_items` (Catalog: THEME, AVATAR_FRAME, XP_BOOSTER).
--   3. Tạo bảng `user_items` (Quyền sở hữu, virtual generated column cho equipped slot).
--   4. Tạo bảng `booster_activations` (Lượt kích hoạt XP Booster, server wall-clock).
--   5. Tạo bảng `coin_transactions` (Ledger append-only cho biến động Coin).
--   6. Tạo bảng `experience_logs` (Lưu lịch sử XP kèm base/bonus cho XP Booster).
--   7. Ràng buộc toàn vẹn dữ liệu (CHECK constraint trên users.coin, unique indexes).
--
-- Ghi chú môi trường:
--   - Môi trường dev dùng `ddl-auto: update` sẽ tự sinh/cập nhật bảng từ JPA Entity.
--   - Script này dành cho môi trường quản lý schema thủ công (Staging/Production).
--   - Idempotent: an toàn khi chạy lại nhiều lần.
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- BƯỚC 1: Dọn dẹp skeleton cũ
-- ----------------------------------------------------------------------------
-- `user_inventories` chỉ là skeleton cũ, chưa có dữ liệu sản xuất, thay bằng `user_items`
DROP TABLE IF EXISTS `user_inventories`;

-- Nếu bảng `shop_items` cũ tồn tại dưới dạng skeleton (có cột legacy `is_active`
-- và chưa có cấu trúc canonical mới), xóa để khởi tạo lại sạch sẽ.
SET @drop_legacy_shop_items = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'shop_items'
              AND COLUMN_NAME = 'is_active'
        ),
        'DROP TABLE IF EXISTS `shop_items`;',
        'SELECT 1;'
    )
);
PREPARE stmt_drop_legacy FROM @drop_legacy_shop_items;
EXECUTE stmt_drop_legacy;
DEALLOCATE PREPARE stmt_drop_legacy;

-- ----------------------------------------------------------------------------
-- BƯỚC 2: Bảng `shop_items` (Danh mục vật phẩm Shop)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shop_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `type` VARCHAR(30) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) NULL,
    `icon_key` VARCHAR(255) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    `purchasable` BOOLEAN NOT NULL DEFAULT FALSE,
    `price` BIGINT NULL,
    `available_from` DATETIME(6) NULL,
    `available_until` DATETIME(6) NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `theme_key` VARCHAR(50) NULL,
    `frame_asset_key` VARCHAR(255) NULL,
    `boost_duration_minutes` INT NULL,
    `max_quantity` INT NULL,
    `published_at` DATETIME(6) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_shop_items_code` (`code`),
    UNIQUE KEY `uq_shop_items_theme_key` (`theme_key`),
    UNIQUE KEY `uq_shop_items_id_type` (`id`, `type`),
    KEY `idx_shop_items_catalog` (`status`, `purchasable`, `type`, `sort_order`),
    CONSTRAINT `chk_shop_items_price` CHECK (`price` IS NULL OR `price` > 0),
    CONSTRAINT `chk_shop_items_purchasable` CHECK (NOT `purchasable` OR `price` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 3: Bảng `user_items` (Bản ghi sở hữu vật phẩm của người dùng)
-- ----------------------------------------------------------------------------
-- Lưu ý:
-- 1. Composite FK `(shop_item_id, item_type) REFERENCES shop_items(id, type)`:
--    Đảm bảo tuyệt đối ở tầng DB rằng `user_items.item_type` LUÔN BẰNG `shop_items.type`.
-- 2. Cột `equipped_slot` sinh tự động bằng IF(equipped, item_type, NULL).
--    Trong MySQL, các giá trị NULL không xung đột trong UNIQUE index, do đó:
--    - Item chưa equip (equipped = 0) có slot = NULL -> có thể sở hữu nhiều item.
--    - Item đã equip (equipped = 1) có slot = item_type -> kết hợp với composite FK
--      ở trên, DB đảm bảo đồng thời: tối đa 1 THEME và 1 AVATAR_FRAME được equip
--      trên mỗi user ngay tại tầng DB, loại bỏ hoàn toàn khả năng lệch dữ liệu.
CREATE TABLE IF NOT EXISTS `user_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `shop_item_id` BIGINT NOT NULL,
    `item_type` VARCHAR(30) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `equipped` BOOLEAN NOT NULL DEFAULT FALSE,
    `equipped_slot` VARCHAR(30) GENERATED ALWAYS AS (IF(`equipped` = 1, `item_type`, NULL)) STORED,
    `acquired_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_items_owner` (`user_id`, `shop_item_id`),
    UNIQUE KEY `uq_user_items_equipped` (`user_id`, `equipped_slot`),
    KEY `idx_user_items_user` (`user_id`),
    CONSTRAINT `chk_user_items_quantity` CHECK (`quantity` >= 0),
    CONSTRAINT `fk_user_items_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_items_shop_item` FOREIGN KEY (`shop_item_id`, `item_type`) REFERENCES `shop_items` (`id`, `type`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 4: Bảng `booster_activations` (Lịch sử & trạng thái kích hoạt XP Booster)
-- ----------------------------------------------------------------------------
-- Quản lý wall-clock timer phía server. Lazy expiration: active ⇔ now < expires_at.
-- Idempotency key ngăn chặn kích hoạt trùng lặp do retry mạng.
CREATE TABLE IF NOT EXISTS `booster_activations` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `shop_item_id` BIGINT NOT NULL,
    `started_at` DATETIME(6) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_booster_activation_key` (`user_id`, `idempotency_key`),
    KEY `idx_booster_activation_active` (`user_id`, `expires_at`),
    CONSTRAINT `fk_booster_activations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_booster_activations_shop_item` FOREIGN KEY (`shop_item_id`) REFERENCES `shop_items` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 5: Bảng `coin_transactions` (Sổ cái Coin Ledger append-only)
-- ----------------------------------------------------------------------------
-- Mọi biến động Coin (mua item, nhận thưởng, điều chỉnh) đều ghi nhận tại đây.
-- `amount` mang dấu: âm = chi tiêu, dương = cộng thưởng.
-- `balance_after` lưu số dư sau giao dịch phục vụ đối soát và hiển thị ví.
CREATE TABLE IF NOT EXISTS `coin_transactions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `amount` BIGINT NOT NULL,
    `balance_after` BIGINT NOT NULL,
    `source_type` VARCHAR(30) NOT NULL,
    `reference_id` VARCHAR(64) NULL,
    `description` VARCHAR(255) NULL,
    `event_key` VARCHAR(191) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_coin_transactions_event_key` (`event_key`),
    KEY `idx_coin_transactions_user_time` (`user_id`, `created_at`),
    CONSTRAINT `chk_coin_transactions_amount` CHECK (`amount` <> 0),
    CONSTRAINT `chk_coin_transactions_balance_after` CHECK (`balance_after` >= 0),
    CONSTRAINT `fk_coin_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 6: Bảng `experience_logs` (Nhật ký XP, hỗ trợ tracking XP Booster)
-- ----------------------------------------------------------------------------
-- Tách bạch base_amount và bonus_amount để kiểm toán chính xác hiệu quả Booster.
-- bonus_amount chỉ áp dụng cho FLASHCARD_REVIEW và QUIZ trong thời gian active.
CREATE TABLE IF NOT EXISTS `experience_logs` (
    `id` VARCHAR(36) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `amount` INT NOT NULL,
    `base_amount` INT NOT NULL,
    `bonus_amount` INT NOT NULL DEFAULT 0,
    `booster_activation_id` BIGINT NULL,
    `source_type` VARCHAR(30) NOT NULL,
    `event_key` VARCHAR(191) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_experience_logs_event_key` (`event_key`),
    KEY `idx_experience_logs_user_time` (`user_id`, `created_at`),
    CONSTRAINT `chk_experience_logs_amount` CHECK (`amount` = `base_amount` + `bonus_amount`),
    CONSTRAINT `fk_experience_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_experience_logs_booster` FOREIGN KEY (`booster_activation_id`) REFERENCES `booster_activations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- BƯỚC 7: Cập nhật & Ràng buộc bảo vệ bảng `users`
-- ----------------------------------------------------------------------------
-- Đảm bảo cột `coin` tồn tại trên bảng `users` với giá trị mặc định 0
SET @add_user_coin_col = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'users'
              AND COLUMN_NAME = 'coin'
        ),
        'ALTER TABLE `users` ADD COLUMN `coin` BIGINT NOT NULL DEFAULT 0;',
        'SELECT 1;'
    )
);
PREPARE stmt_user_coin FROM @add_user_coin_col;
EXECUTE stmt_user_coin;
DEALLOCATE PREPARE stmt_user_coin;

-- Ràng buộc CHECK đảm bảo số dư coin của user không bao giờ âm (MySQL 8.0.16+)
SET @add_coin_check = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'users'
              AND CONSTRAINT_NAME = 'chk_users_coin'
        ),
        'ALTER TABLE `users` ADD CONSTRAINT `chk_users_coin` CHECK (`coin` >= 0);',
        'SELECT 1;'
    )
);
PREPARE stmt_check FROM @add_coin_check;
EXECUTE stmt_check;
DEALLOCATE PREPARE stmt_check;

SET FOREIGN_KEY_CHECKS = 1;
