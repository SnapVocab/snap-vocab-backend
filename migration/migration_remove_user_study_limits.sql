-- Run with the backend stopped, before starting the version that removes these fields.
-- Collection settings are the sole source of daily study quotas. Queue batching is internal.
ALTER TABLE user_settings
    DROP COLUMN IF EXISTS daily_goal,
    DROP COLUMN IF EXISTS study_session_card_limit;
