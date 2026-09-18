-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: SCAN REQUESTS (BF-06)
-- Mục đích:
--   Tạo bảng `scan_requests` lưu mỗi lượt nhận diện ảnh (job chạy nền).
--   Bảng này cũng là nguồn đếm quota scan/ngày: mọi dòng có status khác FAILED
--   đều tính 1 lượt, nên lượt lỗi tự động không bị trừ.
-- Ghi chú:
--   Môi trường dùng `ddl-auto: update` sẽ tự tạo bảng từ entity ScanRequest;
--   script này dành cho môi trường quản lý schema thủ công.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `scan_requests` (
    `id` VARCHAR(36) NOT NULL,
    `user_id` BIGINT(20) NOT NULL,
    `object_key` VARCHAR(512) NULL,
    `status` VARCHAR(20) NOT NULL,
    `error_code` VARCHAR(40) NULL,
    `error_message` VARCHAR(1000) NULL,
    `result_json` LONGTEXT NULL,
    `model_version` VARCHAR(100) NULL,
    `detection_count` INT NULL,
    `processing_time_ms` BIGINT(20) NULL,
    `started_at` DATETIME(6) NULL,
    `finished_at` DATETIME(6) NULL,
    `version` BIGINT(20) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_scan_requests_user_created` (`user_id`, `created_at`),
    KEY `idx_scan_requests_status` (`status`),
    CONSTRAINT `fk_scan_requests_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
