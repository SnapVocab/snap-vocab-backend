-- ============================================================================
-- SNAPVOCAB DATABASE MIGRATION: BADGES — mô tả badge streak (lưu từ đã quét là hoạt động học)
-- ============================================================================
-- Traceability: docs/decisions/daily_mission.md PD-DM-07 (PO 2026-10-22), docs/decisions/badges.md PD-BDG-11.
-- Seeder chỉ chèn badge mới, không sửa badge đã có, nên câu mô tả cũ trong DB phải đổi bằng script này.
-- Chỉ sửa khi vẫn là câu đã seed (không đè bản Admin đã sửa). Idempotent. Chạy cùng đợt phát hành backend,
-- SAU migration_streak_freeze_v1.sql (script đó tạo câu "Làm quiz hoặc ôn thẻ … chuỗi không đứt" mà script này thay).
-- ============================================================================

UPDATE `badges` SET `description` = 'Học (quiz, ôn thẻ hoặc lưu từ đã quét) 7 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_7' AND `description` = 'Làm quiz hoặc ôn thẻ 7 ngày trong một chuỗi không đứt';
UPDATE `badges` SET `description` = 'Học (quiz, ôn thẻ hoặc lưu từ đã quét) 30 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_30' AND `description` = 'Làm quiz hoặc ôn thẻ 30 ngày trong một chuỗi không đứt';
UPDATE `badges` SET `description` = 'Học (quiz, ôn thẻ hoặc lưu từ đã quét) 100 ngày trong một chuỗi không đứt', `updated_at` = NOW(6)
WHERE `code` = 'STREAK_100' AND `description` = 'Làm quiz hoặc ôn thẻ 100 ngày trong một chuỗi không đứt';
