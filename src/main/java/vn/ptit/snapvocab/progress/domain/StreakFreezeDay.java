package vn.ptit.snapvocab.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.user.domain.User;

/** A missed study day a Streak Freeze unit covered; the row is the receipt of that unit (streak_freeze.md §5). */
@Entity
@Table(
		name = "streak_freeze_days",
		uniqueConstraints = @UniqueConstraint(name = "uq_streak_freeze_day", columnNames = {"user_id", "frozen_date"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreakFreezeDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** Study day (GMT+7, ProgressService.ZONE) the chain was kept alive on. */
	@Column(name = "frozen_date", nullable = false)
	private LocalDate frozenDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_item_id", nullable = false)
	private ShopItem shopItem;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
