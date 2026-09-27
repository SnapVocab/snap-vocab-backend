package vn.ptit.snapvocab.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.User;

@Entity
@Table(
		name = "booster_activations",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_booster_activation_key", columnNames = {"user_id", "idempotency_key"})
		},
		indexes = {
				@Index(name = "idx_booster_activation_active", columnList = "user_id, expires_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoosterActivation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_item_id", nullable = false)
	private ShopItem shopItem;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isActive(Instant now) {
		return now != null && now.isBefore(expiresAt);
	}

	public long getRemainingSeconds(Instant now) {
		if (now == null || !isActive(now)) {
			return 0L;
		}
		return Math.max(0L, Duration.between(now, expiresAt).getSeconds());
	}
}
