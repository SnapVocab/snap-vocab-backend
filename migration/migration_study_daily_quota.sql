-- Daily study quota is scoped to a user, collection, and Vietnam-local study date.
-- The unique keys are correctness constraints used by the transactional quota flow.
CREATE TABLE IF NOT EXISTS study_daily_progress (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	collection_id BIGINT NOT NULL,
	study_date DATE NOT NULL,
	created_at TIMESTAMP(6) NOT NULL,
	updated_at TIMESTAMP(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_study_daily_progress_user_collection_date
		UNIQUE (user_id, collection_id, study_date),
	CONSTRAINT fk_study_daily_progress_user
		FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_study_daily_progress_collection
		FOREIGN KEY (collection_id) REFERENCES collections (id)
);

CREATE TABLE IF NOT EXISTS study_daily_cards (
	id BIGINT NOT NULL AUTO_INCREMENT,
	user_id BIGINT NOT NULL,
	collection_id BIGINT NOT NULL,
	study_date DATE NOT NULL,
	topic_item_id BIGINT NOT NULL,
	created_at TIMESTAMP(6) NOT NULL,
	updated_at TIMESTAMP(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_study_daily_card_user_collection_date_item
		UNIQUE (user_id, collection_id, study_date, topic_item_id),
	CONSTRAINT fk_study_daily_card_user
		FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_study_daily_card_collection
		FOREIGN KEY (collection_id) REFERENCES collections (id),
	CONSTRAINT fk_study_daily_card_topic_item
		FOREIGN KEY (topic_item_id) REFERENCES topic_items (id)
);

-- Seed today's quota from cards reviewed before this table was introduced.
-- last_review is stored as UTC LocalDateTime by StudyService, shifted to the app's current +07:00 study zone.
INSERT IGNORE INTO study_daily_cards (
	user_id, collection_id, study_date, topic_item_id, created_at, updated_at
)
SELECT
	fr.user_id,
	c.id,
	DATE(DATE_ADD(fr.last_review, INTERVAL 7 HOUR)),
	ti.id,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
FROM fsrs_records fr
JOIN topic_items ti ON ti.id = fr.topic_item_id
JOIN topics t ON t.id = ti.topic_id
JOIN collections c ON c.id = t.collection_id
WHERE fr.last_review IS NOT NULL
	AND DATE(DATE_ADD(fr.last_review, INTERVAL 7 HOUR)) = DATE(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 7 HOUR));
