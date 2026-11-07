-- Per-learner settings. Do not import the legacy global daily_goal into each
-- collection: no row explicitly means the product defaults 20 new / 200 review.
CREATE TABLE IF NOT EXISTS collection_study_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    collection_id BIGINT NOT NULL,
    new_cards_per_day INT NOT NULL DEFAULT 20,
    review_cards_per_day INT NOT NULL DEFAULT 200,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_collection_study_settings_user_collection UNIQUE (user_id, collection_id),
    CONSTRAINT fk_collection_study_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_study_settings_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    CONSTRAINT chk_collection_study_new CHECK (new_cards_per_day BETWEEN 0 AND 9999),
    CONSTRAINT chk_collection_study_review CHECK (review_cards_per_day BETWEEN 0 AND 99999)
);
