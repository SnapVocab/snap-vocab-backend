package vn.ptit.snapvocab.badge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;

/**
 * Badge catalog entry (badges.md §7.1). {@code code}, {@code metric} and {@code threshold} never change after
 * creation: a new threshold is a new badge (§5.3). Deactivating stops new grants; earners keep seeing it (§5.4).
 */
@Entity
@Table(name = "badges",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_badges_code", columnNames = "code"),
				@UniqueConstraint(name = "uq_badges_metric_threshold", columnNames = {"metric", "threshold"})})
@Check(name = "chk_badges_threshold", constraints = "threshold >= 1")
@Check(name = "chk_badges_reward", constraints = "reward_coin >= 0 AND reward_xp >= 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64, updatable = false)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 255)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30, updatable = false)
	private BadgeMetric metric;

	@Column(nullable = false, updatable = false)
	private Integer threshold;

	/** MinIO object key of the badge art ({@code public/badges/…}); the app and Admin read its download URL. */
	@Column(name = "image_key", length = 255)
	private String imageKey;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(name = "is_active", nullable = false)
	@ColumnDefault("true")
	@Builder.Default
	private Boolean active = true;

	/** Reward (PD-BDG-14), added once inside the sync that grants the badge; earners keep what they got (snapshot). */
	@Column(name = "reward_coin", nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Integer rewardCoin = 0;

	@Column(name = "reward_xp", nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Integer rewardXp = 0;

	/** Code of a reward-only AVATAR_FRAME/CARD_FRAME (PUBLISHED, not purchasable), usually on a series' last tier. */
	@Column(name = "reward_item_code", length = 64)
	private String rewardItemCode;

	/** Admin optimistic lock (badges.md §8.4, P2); column added by migration_badges_v2.sql. */
	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Long version = 0L;
}
