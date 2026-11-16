-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: ADMIN STATS (Dashboard + Phân tích) — v1
-- ============================================================================
-- Traceability: docs/sa/admin_overview_backend.md §2.5–§2.6, docs/sa/admin_analytics_backend.md
--
-- Mục đích:
--   1. Bảng scan_detections (G2).
--   2. Cột topic_items.source_scan_id (G3).
--   3. Index theo thời gian cho truy vấn gộp toàn hệ thống (index hiện có đều bắt đầu bằng user_id).
--
-- Ghi chú:
--   - Idempotent (kiểm information_schema trước mỗi lệnh), chạy được trên MariaDB 10.11 và MySQL 8.
--   - Bảng chưa tồn tại thì bỏ qua: trên DB remote, migration_remote_reconcile_v1 xóa learning_events, quizzes,
--     coin_transactions… và Hibernate (ddl-auto: update) tạo lại chúng KÈM các index này (khai trên @Table).
--   - Collation: DB remote trộn utf8mb4_unicode_ci (scan_requests) và utf8mb4_uca1400_ai_ci (topic_items…). Cột
--     so với scan_requests.id (scan_detections.scan_request_id, topic_items.source_scan_id) lấy ĐÚNG collation của
--     scan_requests.id: khác collation thì MariaDB từ chối FK và phép so "=" lỗi 1267 hoặc bỏ qua index.
--   - Thứ tự phát hành: chạy sau các migration khác (RemoteSchemaReconcileMariaDbTest), trước khi deploy backend
--     dùng ddl-auto: validate (staging/prod).
-- ============================================================================

DROP PROCEDURE IF EXISTS `stats_v1_ddl`;
DROP PROCEDURE IF EXISTS `stats_v1_index`;
DROP PROCEDURE IF EXISTS `stats_v1_match`;
DELIMITER //
CREATE PROCEDURE `stats_v1_ddl`(IN `run` BOOLEAN, IN `ddl` VARCHAR(2000))
BEGIN
    IF `run` THEN
        SET @stats_v1_ddl = `ddl`;
        PREPARE stmt FROM @stats_v1_ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
CREATE PROCEDURE `stats_v1_index`(IN `tbl` VARCHAR(64), IN `idx` VARCHAR(64), IN `cols` VARCHAR(200))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = `tbl`)
            AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = `tbl` AND INDEX_NAME = `idx`) THEN
        CALL stats_v1_ddl(TRUE, CONCAT('ALTER TABLE `', `tbl`, '` ADD INDEX `', `idx`, '` (', `cols`, ')'));
    END IF;
END //
-- Đặt lại collation của một cột đã có nếu nó khác scan_requests.id (cột do Hibernate tạo theo mặc định của bảng).
CREATE PROCEDURE `stats_v1_match`(IN `tbl` VARCHAR(64), IN `col` VARCHAR(64), IN `def` VARCHAR(100))
BEGIN
    IF @stats_v1_collation IS NOT NULL AND EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = `tbl` AND COLUMN_NAME = `col`
              AND COLLATION_NAME <> @stats_v1_collation) THEN
        CALL stats_v1_ddl(TRUE,
            CONCAT('ALTER TABLE `', `tbl`, '` MODIFY `', `col`, '` VARCHAR(36) COLLATE ', @stats_v1_collation, ' ', `def`));
    END IF;
END //
DELIMITER ;

SET @stats_v1_collation = (SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'scan_requests' AND COLUMN_NAME = 'id');

-- BƯỚC 1: scan_detections (G2), chỉ khi đã có scan_requests (FK)
CALL stats_v1_ddl(@stats_v1_collation IS NOT NULL,
    CONCAT('CREATE TABLE IF NOT EXISTS `scan_detections` (
        `id` BIGINT NOT NULL AUTO_INCREMENT,
        `scan_request_id` VARCHAR(36) NOT NULL,
        `user_id` BIGINT NOT NULL,
        `created_at` DATETIME(6) NOT NULL,
        `label` VARCHAR(120) NOT NULL,
        `headword` VARCHAR(120) NULL,
        `reliability` VARCHAR(10) NOT NULL,
        `has_word` BIT(1) NOT NULL,
        PRIMARY KEY (`id`),
        KEY `idx_scan_detections_created` (`created_at`),
        KEY `idx_scan_detections_label` (`label`, `created_at`),
        KEY `idx_scan_detections_scan` (`scan_request_id`),
        CONSTRAINT `fk_scan_detections_scan` FOREIGN KEY (`scan_request_id`) REFERENCES `scan_requests` (`id`) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=', @stats_v1_collation));

-- BƯỚC 2: topic_items.source_scan_id (G3)
SET @stats_v1_add_col = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'topic_items') > 0
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'topic_items' AND COLUMN_NAME = 'source_scan_id') = 0;
CALL stats_v1_ddl(@stats_v1_add_col,
    CONCAT('ALTER TABLE `topic_items` ADD COLUMN `source_scan_id` VARCHAR(36)',
        IF(@stats_v1_collation IS NULL, '', CONCAT(' COLLATE ', @stats_v1_collation)), ' NULL'));
CALL stats_v1_match('topic_items', 'source_scan_id', 'NULL');
CALL stats_v1_match('scan_detections', 'scan_request_id', 'NOT NULL');

-- BƯỚC 3: index (bảng, tên, cột)
CALL stats_v1_index('topic_items', 'idx_topic_items_source_scan', '`source_scan_id`');
CALL stats_v1_index('topic_items', 'idx_topic_items_created', '`created_at`');
CALL stats_v1_index('learning_events', 'idx_learning_events_date_type', '`learning_date`, `event_type`');
CALL stats_v1_index('scan_requests', 'idx_scan_requests_created', '`created_at`');
CALL stats_v1_index('coin_transactions', 'idx_coin_transactions_source_time', '`source_type`, `created_at`');
CALL stats_v1_index('user_daily_missions', 'idx_udm_date_status', '`mission_date`, `status`');
CALL stats_v1_index('quizzes', 'idx_quizzes_status_completed', '`status`, `completed_at`');
CALL stats_v1_index('fsrs_records', 'idx_fsrs_state_stability', '`state`, `stability`');

DROP PROCEDURE `stats_v1_match`;
DROP PROCEDURE `stats_v1_index`;
DROP PROCEDURE `stats_v1_ddl`;
