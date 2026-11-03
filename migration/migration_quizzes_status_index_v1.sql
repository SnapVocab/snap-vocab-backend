-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION SCRIPT: QUIZZES — covering index cho đếm quiz theo user
-- ============================================================================
-- Traceability:
--   docs/decisions/badges.md §8.4 (GET /api/admin/badges/{id}/refill-estimate, PD-BDG-13d), §6.1 (sync)
--
-- Mục đích:
--   Thêm `idx_quiz_user_status (user_id, status, correct_count, total_units)` để đếm quiz COMPLETED / điểm tuyệt đối
--   của từng user chỉ đọc index. Đo trên MariaDB 10.11 (100k user, 3M quiz, buffer pool 128 MB): ước tính cấp bù
--   QUIZ 6 phút → 0,25 s, PERFECT 5,5 phút → 0,6 s.
--
-- Ghi chú:
--   - Khớp entity vn.ptit.snapvocab.quiz.domain.Quiz (@Index idx_quiz_user_status); prod chạy ddl-auto validate,
--     validate không tạo index nên phải chạy script này.
--   - Idempotent; không đổi dữ liệu. Tạo index online (InnoDB), có thể chạy trước hoặc sau khi deploy backend.
-- ============================================================================

DROP PROCEDURE IF EXISTS `quiz_idx_v1_ddl`;
DELIMITER //
CREATE PROCEDURE `quiz_idx_v1_ddl`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'quizzes' AND INDEX_NAME = 'idx_quiz_user_status') THEN
        CREATE INDEX `idx_quiz_user_status` ON `quizzes` (`user_id`, `status`, `correct_count`, `total_units`);
    END IF;
END //
DELIMITER ;

CALL quiz_idx_v1_ddl();
DROP PROCEDURE `quiz_idx_v1_ddl`;
