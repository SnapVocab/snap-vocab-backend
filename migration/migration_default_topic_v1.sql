-- Default topic per learner (buss_mainflow.md BF-07 step 2: "Topic gần nhất -> Topic mặc định -> Đổi Topic").
-- A plain id, no foreign key: the learner may delete that topic; POST /api/topics/default then creates a new one.
-- No backfill: existing learners get their default topic on first save.
-- Rollback: ALTER TABLE users DROP COLUMN default_topic_id;
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_topic_id BIGINT NULL;
