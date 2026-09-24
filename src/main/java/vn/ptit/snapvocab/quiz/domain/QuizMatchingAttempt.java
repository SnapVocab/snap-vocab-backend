package vn.ptit.snapvocab.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseCreatedAtEntity;

@Entity
@Table(name = "quiz_matching_attempts", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_attempt_key", columnNames = {
		"quiz_id", "idempotency_key" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizMatchingAttempt extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quiz_id", nullable = false)
	private Quiz quiz;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private QuizQuestion question;

	@Column(name = "round_no", nullable = false)
	private int roundNo;

	@Column(name = "left_key", nullable = false, length = 64)
	private String leftKey;

	@Column(name = "right_key", nullable = false, length = 64)
	private String rightKey;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;

	@Column(name = "round_matched_count", nullable = false)
	private int roundMatchedCount;

	@Column(name = "round_total_pairs", nullable = false)
	private int roundTotalPairs;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;
}
