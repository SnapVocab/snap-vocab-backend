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
@Table(name = "quiz_matching_tiles", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_tile_key", columnNames = {
		"quiz_id", "tile_key" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizMatchingTile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quiz_id", nullable = false)
	private Quiz quiz;

	@Column(name = "round_no", nullable = false)
	private int roundNo;

	@Column(name = "tile_key", nullable = false, length = 64)
	private String tileKey;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String label;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Builder.Default
	@Column(nullable = false)
	private boolean matched = false;
}

