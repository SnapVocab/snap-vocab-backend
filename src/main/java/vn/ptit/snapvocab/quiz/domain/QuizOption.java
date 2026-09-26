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

@Entity
@Table(name = "quiz_question_options", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_option_key", columnNames = {
		"question_id", "option_key" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private QuizQuestion question;

	@Column(name = "option_key", nullable = false, length = 64)
	private String optionKey;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String label;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;
}

