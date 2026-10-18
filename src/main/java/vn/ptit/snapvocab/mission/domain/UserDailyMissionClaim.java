package vn.ptit.snapvocab.mission.domain;

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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;
import vn.ptit.snapvocab.user.domain.User;

/**
 * Append-only receipt of one claim (daily_mission.md §7.4) and the idempotency anchor of the item reward:
 * {@code ShopRewardService} does not dedupe items by event key.
 */
@Entity
@Table(name = "user_daily_mission_claims",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_mission_claims_mission", columnNames = "user_daily_mission_id"),
				@UniqueConstraint(name = "uq_mission_claims_key", columnNames = {"user_id", "idempotency_key"})
		})
@Check(name = "chk_mission_claims_amounts",
		constraints = "reward_coin >= 0 AND reward_xp >= 0 AND granted_item_quantity >= 0 AND fallback_coin >= 0")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyMissionClaim extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_daily_mission_id", nullable = false)
	private UserDailyMission userDailyMission;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "reward_coin", nullable = false)
	private Integer rewardCoin;

	@Column(name = "reward_xp", nullable = false)
	private Integer rewardXp;

	@Column(name = "reward_item_code", length = 64)
	private String rewardItemCode;

	@Column(name = "granted_item_quantity", nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Integer grantedItemQuantity = 0;

	@Column(name = "fallback_coin", nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Long fallbackCoin = 0L;
}
