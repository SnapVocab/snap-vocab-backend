-- ============================================================================
-- SNAPVOCAB REPORT: DAILY MISSION — hiệu quả theo mã nhiệm vụ (chỉ đọc)
-- ============================================================================
-- Traceability: docs/decisions/daily_mission.md PD-DM-14, §12; PD-DM-09 (mất thưởng khi chưa nhận).
-- Chạy hằng tuần bằng tài khoản chỉ đọc. Một câu SELECT, không ghi gì. Thay cho API analytics cho tới khi có
-- backend Dashboard.
--
-- Phạm vi: 28 ngày nhiệm vụ đã kết thúc, tính theo giờ Việt Nam (bỏ hôm nay vì còn đang làm).
-- Giai đoạn người học: số ngày kể từ ngày đầu tiên người đó có nhiệm vụ (không phụ thuộc múi giờ lưu users.created_at).
--   "1. Ngày đầu" = ngày đầu tiên, "2. Tuần đầu" = ngày 2–7, "3. Sau tuần đầu" = từ ngày 8.
-- Cột:
--   assigned          số lần được giao
--   completed_pct     % đã hoàn thành (COMPLETED hoặc CLAIMED)
--   claimed_pct       % đã nhận thưởng
--   expired_pct       % hoàn thành nhưng hết ngày chưa nhận thưởng (PD-DM-09)
-- ============================================================================
SELECT m.code,
       CASE
           WHEN DATEDIFF(m.mission_date, f.first_date) = 0 THEN '1. Ngày đầu'
           WHEN DATEDIFF(m.mission_date, f.first_date) < 7 THEN '2. Tuần đầu'
           ELSE '3. Sau tuần đầu'
       END AS stage,
       COUNT(*) AS assigned,
       ROUND(100 * SUM(m.status IN ('COMPLETED', 'CLAIMED')) / COUNT(*), 1) AS completed_pct,
       ROUND(100 * SUM(m.status = 'CLAIMED') / COUNT(*), 1) AS claimed_pct,
       ROUND(100 * SUM(m.status = 'COMPLETED') / COUNT(*), 1) AS expired_pct
FROM user_daily_missions m
JOIN (SELECT user_id, MIN(mission_date) AS first_date FROM user_daily_missions GROUP BY user_id) f
    ON f.user_id = m.user_id
WHERE m.mission_date < DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+07:00'))
  AND m.mission_date >= DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+07:00')) - INTERVAL 28 DAY
GROUP BY m.code, stage
ORDER BY m.code, stage;
