package vn.ptit.snapvocab.shop.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.shop.domain.enumeration.CoinSourceType;

@Entity
@Table(
		name = "coin_transactions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_coin_transactions_event_key", columnNames = {"event_key"})
		},
		indexes = {
				@Index(name = "idx_coin_transactions_user_time", columnList = "user_id, created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "balance_after", nullable = false)
	private Long balanceAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private CoinSourceType sourceType;

	@Column(name = "reference_id", length = 64)
	private String referenceId;

	@Column(length = 255)
	private String description;

	@Column(name = "event_key", nullable = false, unique = true, length = 191)
	private String eventKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
