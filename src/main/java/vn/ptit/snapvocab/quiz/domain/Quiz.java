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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;

@Entity
@Table(name = "quizzes", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_user_create_key", columnNames = {
		"user_id",
		"create_idempotency_key" }), indexes = @Index(name = "idx_quiz_user_created", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "topic_id", nullable = false)
	private Long topicId;

	@Column(name = "topic_name", nullable = false)
	private String topicName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizMode mode;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private QuizDirection direction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizStatus status;

	@Column(name = "requested_count", nullable = false)
	private int requestedCount;

	@Column(name = "create_idempotency_key", nullable = false, length = 128)
	private String createIdempotencyKey;

	@Column(name = "total_units")
	private Integer totalUnits;

	@Column(name = "correct_count")
	private Integer correctCount;

	@Column(name = "incorrect_count")
	private Integer incorrectCount;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Version
	private Long version;

	@OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	@Builder.Default
	private List<QuizQuestion> questions = new ArrayList<>();

	@OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("roundNo ASC, displayOrder ASC")
	@Builder.Default
	private List<QuizMatchingTile> tiles = new ArrayList<>();

	public void addQuestion(QuizQuestion question) {
		questions.add(question);
		question.setQuiz(this);
	}

	public void addTile(QuizMatchingTile tile) {
		tiles.add(tile);
		tile.setQuiz(this);
	}
}

