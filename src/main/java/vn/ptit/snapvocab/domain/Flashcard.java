package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.CardStatus;

@Entity
@Table(name = "flashcard")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Flashcard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@Size(min = 1, max = 1000)
	@Column(name = "front_text", length = 1000, nullable = false)
	private String frontText;

	@NotNull
	@Size(min = 1, max = 1000)
	@Column(name = "back_text", length = 1000, nullable = false)
	private String backText;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private CardStatus status;

	@Column(name = "due_date")
	private Instant dueDate;

	@DecimalMin(value = "1")
	@Column(name = "ease_factor")
	private Float easeFactor;

	@Min(value = 0)
	@Column(name = "interval_days")
	private Integer intervalDays;

	@Min(value = 0)
	@Column(name = "reps")
	private Integer reps;

	@Min(value = 0)
	@Column(name = "lapses")
	private Integer lapses;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions", "flashcards"},
			allowSetters = true
	)
	private Word word;
}
