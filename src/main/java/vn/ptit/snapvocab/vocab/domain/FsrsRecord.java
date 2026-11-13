package vn.ptit.snapvocab.vocab.domain;

import vn.ptit.snapvocab.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fsrs_records", uniqueConstraints = {
		@UniqueConstraint(name = "uq_fsrs_user_topic_item", columnNames = { "user_id", "topic_item_id" })
}, indexes = {
		@Index(name = "idx_fsrs_user_due_state", columnList = "user_id, due, state"),
		@Index(name = "idx_fsrs_state_stability", columnList = "state, stability")
})
public class FsrsRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "topic_item_id", nullable = false)
	private TopicItem topicItem;

	// a tinyint in the ERD -> ORDINAL keeps the compact storage; switch to
	// EnumType.STRING if you'd rather trade a byte for readability/safety.
	@Enumerated(EnumType.ORDINAL)
	@Column(nullable = false)
	private CardState state;

	@Column(nullable = false)
	private LocalDateTime due;

	@Column(nullable = false)
	private Float stability;

	@Column(nullable = false)
	private Float difficulty;

	/** Short-term learning step required to restore the java-fsrs Card. */
	@Column
	private Integer step;

	/** Last review instant; java-fsrs schedules in UTC. */
	@Column(name = "last_review")
	private LocalDateTime lastReview;

	@Builder.Default
	@Column(nullable = false)
	private Integer reps = 0;

	@Builder.Default
	@Column(nullable = false)
	private Integer lapses = 0;
}

