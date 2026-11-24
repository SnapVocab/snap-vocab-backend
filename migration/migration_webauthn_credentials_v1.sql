-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: WEBAUTHN CREDENTIALS — v1
-- ============================================================================
-- Mục đích:
--   Tạo bảng user_webauthn_credentials lưu Public Key (COSE) của các thiết bị
--   đã đăng ký đăng nhập sinh trắc học (WebAuthn / Passkey).
--
-- Ghi chú:
--   - Kiểu cột khớp DDL Hibernate sinh từ vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential
--     (staging/prod chạy ddl-auto: validate).
--   - Idempotent, chạy được trên cả MariaDB và MySQL 8.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `user_webauthn_credentials` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `credential_id` VARCHAR(512) NOT NULL,
    `user_handle` VARCHAR(128) NOT NULL,
    `public_key_cose` VARBINARY(2048) NOT NULL,
    `aaguid` VARCHAR(64) NULL,
    `sign_count` BIGINT NOT NULL DEFAULT 0,
    `transports` VARCHAR(255) NULL,
    `device_name` VARCHAR(120) NULL,
    `backup_eligible` BIT(1) NOT NULL DEFAULT 0,
    `backup_state` BIT(1) NOT NULL DEFAULT 0,
    `last_used_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uwc_credential_id` (`credential_id`),
    KEY `idx_uwc_user_id` (`user_id`),
    CONSTRAINT `fk_uwc_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

