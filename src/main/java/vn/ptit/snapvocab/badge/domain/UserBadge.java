package vn.ptit.snapvocab.badge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;
import vn.ptit.snapvocab.user.domain.User;

/**
 * A badge a learner earned; granted at most once, never revoked (badges.md §5, §7.2). Append-only except
 * {@code seen_at}. {@code created_at} is {@code earnedAt}: when sync recorded it, not when the action happened (§6.3).
 */
@Entity
@Table(name = "user_badges",
		uniqueConstraints = @UniqueConstraint(name = "uq_user_badges_badge", columnNames = {"user_id", "badge_id"}),
		indexes = @Index(name = "idx_user_badges_badge", columnList = "badge_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBadge extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "badge_id", nullable = false)
	private Badge badge;

	/** Reward snapshot taken at grant (PD-BDG-14); all NULL for badges earned before rewards existed (no backfill). */
	@Column(name = "reward_coin")
	private Integer rewardCoin;

	@Column(name = "reward_xp")
	private Integer rewardXp;

	@Column(name = "reward_item_code", length = 64)
	private String rewardItemCode;

	/** False when the item could not be granted (already owned or not PUBLISHED); there is no Coin fallback. */
	@Column(name = "reward_item_granted")
	private Boolean rewardItemGranted;

	/** NULL until the learner has seen the "new badge" modal (§2.3). */
	@Setter
	@Column(name = "seen_at")
	private Instant seenAt;
}
