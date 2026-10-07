package vn.ptit.snapvocab.mission.domain;

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
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

/** Mission Pool entry (daily_mission.md §7.1). Learners never see it directly: assignment copies a snapshot. */
@Entity
@Table(name = "mission_templates",
		uniqueConstraints = @UniqueConstraint(name = "uq_mission_templates_code", columnNames = "code"))
@Check(name = "chk_mission_templates_quiz_filters", constraints = MissionTemplate.QUIZ_FILTERS_CHECK)
@Check(name = "chk_mission_templates_reward", constraints = MissionTemplate.REWARD_CHECK)
@Check(name = "chk_mission_templates_target", constraints = "target_value >= 1")
@Check(name = "chk_mission_templates_weight", constraints = "weight >= 1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionTemplate extends BaseTimeEntity {

	/** Shared with the {@link UserDailyMission} snapshot, which must satisfy the same rules. */
	static final String QUIZ_FILTERS_CHECK = "(min_quiz_accuracy_percent IS NULL OR min_quiz_accuracy_percent BETWEEN 1 AND 100)"
			+ " AND (quiz_requested_count IS NULL OR quiz_requested_count IN (5, 10, 20))"
			+ " AND (trigger_event = 'QUIZ_COMPLETED' OR (min_quiz_accuracy_percent IS NULL AND quiz_requested_count IS NULL))";
	static final String REWARD_CHECK = "reward_coin >= 0 AND reward_xp >= 0"
			+ " AND (reward_item_fallback_coin IS NULL OR reward_item_fallback_coin >= 0)"
			+ " AND ((reward_item_code IS NULL) = (reward_item_fallback_coin IS NULL))"
			+ " AND (reward_coin > 0 OR reward_xp > 0 OR reward_item_code IS NOT NULL)";

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
	@Column(nullable = false, length = 20)
	private MissionCategory category;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "trigger_event", nullable = false, length = 40)
	private MissionTriggerEvent triggerEvent;

	@Column(name = "min_quiz_accuracy_percent")
	private Integer minQuizAccuracyPercent;

	@Column(name = "quiz_requested_count")
	private Integer quizRequestedCount;

	@Column(name = "target_value", nullable = false)
	private Integer targetValue;

	@Column(name = "reward_coin", nullable = false)
	private Integer rewardCoin;

	@Column(name = "reward_xp", nullable = false)
	private Integer rewardXp;

	@Column(name = "reward_item_code", length = 64)
	private String rewardItemCode;

	@Column(name = "reward_item_fallback_coin")
	private Integer rewardItemFallbackCoin;

	@Column(nullable = false)
	@ColumnDefault("1")
	@Builder.Default
	private Integer weight = 1;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "eligibility_rule", nullable = false, length = 40)
	@ColumnDefault("'NONE'")
	@Builder.Default
	private MissionEligibilityRule eligibilityRule = MissionEligibilityRule.NONE;

	@Column(name = "is_active", nullable = false)
	@ColumnDefault("false")
	@Builder.Default
	private Boolean active = false;

	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Long version = 0L;
}
