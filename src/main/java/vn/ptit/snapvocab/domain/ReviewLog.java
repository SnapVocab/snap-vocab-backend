package vn.ptit.snapvocab.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.enumeration.ReviewRating;

@Entity
@Table(name = "review_log")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReviewLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReviewRating rating;

	@Column(nullable = false)
	private Instant reviewedAt;

	private Integer responseTimeMs;

	private Instant previousDueAt;

	private Instant nextDueAt;

	private Double previousStability;

	private Double nextStability;

	private Double previousDifficulty;

	private Double nextDifficulty;
}
