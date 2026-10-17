package vn.ptit.snapvocab.study.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.TopicItem;

/** Ensures a TopicItem consumes a collection's daily quota at most once per day. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "study_daily_cards", uniqueConstraints = @UniqueConstraint(
		name = "uq_study_daily_card_user_collection_date_item",
		columnNames = { "user_id", "collection_id", "study_date", "topic_item_id" }))
public class StudyDailyCard extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_id", nullable = false)
	private Collection collection;

	@jakarta.persistence.Column(name = "study_date", nullable = false)
	private LocalDate studyDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_item_id", nullable = false)
	private TopicItem topicItem;
}
