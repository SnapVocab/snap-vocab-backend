package vn.ptit.snapvocab.quiz.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;

@Entity
@Table(name = "quiz_questions", uniqueConstraints = {
		@UniqueConstraint(name = "uk_quiz_question_position", columnNames = { "quiz_id", "position" }),
		@UniqueConstraint(name = "uk_quiz_question_left_key", columnNames = { "quiz_id", "left_key" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quiz_id", nullable = false)
	private Quiz quiz;

	@Column(nullable = false)
	private int position;

	@Column(name = "round_no")
	private Integer roundNo;

	@Column(name = "topic_item_id")
	private Long topicItemId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String prompt;

	@Column(name = "correct_answer", nullable = false, columnDefinition = "TEXT")
	private String correctAnswer;

	/** LISTENING: the word's meaning, shown only once the question is answered (it would give the answer away). */
	@Column(columnDefinition = "TEXT")
	private String meaning;

	@Column(name = "correct_option_key", length = 64)
	private String correctOptionKey;

	@Column(name = "left_key", length = 64)
	private String leftKey;

	@Column(name = "left_display_order")
	private Integer leftDisplayOrder;

	@Column(name = "correct_right_key", length = 64)
	private String correctRightKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private QuizOutcome outcome = QuizOutcome.UNCOMMITTED;

	@Builder.Default
	@Column(nullable = false)
	private boolean resolved = false;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Builder.Default
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "learner_answer", columnDefinition = "TEXT")
	private String learnerAnswer;

	@Column(name = "check_idempotency_key", length = 128)
	private String checkIdempotencyKey;

	@Column(name = "checked_at")
	private Instant checkedAt;

	@OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("displayOrder ASC")
	@Builder.Default
	private List<QuizOption> options = new ArrayList<>();

	public void addOption(QuizOption option) {
		options.add(option);
		option.setQuestion(this);
	}
}

