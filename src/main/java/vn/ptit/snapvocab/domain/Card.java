package vn.ptit.snapvocab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vn.ptit.snapvocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.domain.enumeration.CardType;

@Table(
		name = "card",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uq_card_note_type",
						columnNames = {"note_id", "card_type"}
				)
		}
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Card {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "note_id", nullable = false)
	private Note note;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false)
	private CardType cardType;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_state", nullable = false)
	private CardState cardState;

	private Instant dueAt;

	private Double stability;
	private Double difficulty;
	private Integer repetitions;
	private Integer lapses;
	private Instant lastReviewedAt;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;
}
