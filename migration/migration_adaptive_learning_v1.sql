-- Adaptive learning: AI mnemonics for leech words + forgetting forecast (no table for the forecast, it is computed).
-- Idempotent (MariaDB). Dev databases get the column and table from ddl-auto=update; the backfill below still applies.
--
-- Rollback:
--   DROP TABLE IF EXISTS word_mnemonics;
--   ALTER TABLE fsrs_records DROP COLUMN forget_count;

-- 1) Every "Quên" (AGAIN) per card, also while it is being learned (lapses only counts forgetting a learned card).
ALTER TABLE fsrs_records ADD COLUMN IF NOT EXISTS forget_count INT NOT NULL DEFAULT 0;

-- Backfill from the review log (one SRS_REVIEW_COMPLETED event per rating, kept since 2026-10-31)...
UPDATE fsrs_records fr
JOIN (
    SELECT user_id,
           CAST(JSON_VALUE(metadata, '$.topicItemId') AS UNSIGNED) AS topic_item_id,
           COUNT(*) AS forgets
    FROM learning_events
    WHERE event_type = 'SRS_REVIEW_COMPLETED' AND JSON_VALUE(metadata, '$.rating') = 'AGAIN'
    GROUP BY user_id, CAST(JSON_VALUE(metadata, '$.topicItemId') AS UNSIGNED)
) again ON again.user_id = fr.user_id AND again.topic_item_id = fr.topic_item_id
SET fr.forget_count = GREATEST(fr.forget_count, again.forgets);

-- ...and never below lapses, for cards reviewed before the log existed.
UPDATE fsrs_records SET forget_count = lapses WHERE forget_count < lapses;

-- 2) The learner's own AI mnemonic per card (system cards are shared, so it is not written into the card).
CREATE TABLE IF NOT EXISTS word_mnemonics (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    topic_item_id BIGINT       NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    keyword       VARCHAR(120) NULL,
    content       TEXT         NOT NULL,
    provider_name VARCHAR(100) NULL,
    model_name    VARCHAR(150) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_word_mnemonics_user_item UNIQUE (user_id, topic_item_id),
    CONSTRAINT fk_word_mnemonics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_word_mnemonics_topic_item FOREIGN KEY (topic_item_id) REFERENCES topic_items (id) ON DELETE CASCADE
);
