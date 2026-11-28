-- Listening quiz (QuizMode.LISTENING): hear an English word, pick its spelling among words that sound alike.
-- Idempotent (MariaDB). Run BEFORE deploying the backend: quizzes.mode is a MariaDB ENUM that ddl-auto=update does
-- not widen, so creating a LISTENING quiz would fail with "Data truncated for column 'mode'".
-- Old code keeps working after it (one more allowed value, one more nullable column).
--
-- Rollback (only once no LISTENING quiz exists):
--   ALTER TABLE quiz_questions DROP COLUMN meaning;
--   ALTER TABLE quizzes MODIFY mode ENUM('FILL_BLANK','MATCHING','MCQ') NOT NULL;

-- 1) The new mode.
ALTER TABLE quizzes MODIFY mode ENUM('FILL_BLANK','MATCHING','MCQ','LISTENING') NOT NULL;

-- 2) The heard word's meaning, revealed only after the question is answered.
ALTER TABLE quiz_questions ADD COLUMN IF NOT EXISTS meaning TEXT NULL;
