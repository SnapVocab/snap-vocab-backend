package vn.ptit.snapvocab.mission.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
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
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.user.domain.User;

/**
 * One of the learner's five missions of a day (daily_mission.md §7.2). The template definition is snapshotted at
 * assignment, so later template edits never change what was shown or what the claim pays.
 */
@Entity
@Table(name = "user_daily_missions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_user_daily_missions_slot", columnNames = {"user_id", "mission_date", "slot"}),
				@UniqueConstraint(name = "uq_user_daily_missions_template",
						columnNames = {"user_id", "mission_date", "mission_template_id"})
		},
		indexes = {
				@Index(name = "idx_user_daily_missions_template", columnList = "mission_template_id"),
				@Index(name = "idx_udm_date_status", columnList = "mission_date, status")
		})
@Check(name = "chk_user_daily_missions_slot", constraints = "slot BETWEEN 1 AND 5")
@Check(name = "chk_user_daily_missions_target", constraints = "target_value >= 1")
@Check(name = "chk_user_daily_missions_progress", constraints = "current_progress >= 0 AND current_progress <= target_value")
@Check(name = "chk_user_daily_missions_status", constraints = "status IN ('IN_PROGRESS', 'COMPLETED', 'CLAIMED')")
@Check(name = "chk_user_daily_missions_completed",
		constraints = "(status = 'IN_PROGRESS') = (completed_at IS NULL) AND (status = 'IN_PROGRESS' OR current_progress = target_value)")
@Check(name = "chk_user_daily_missions_claimed", constraints = "(status = 'CLAIMED') = (claimed_at IS NOT NULL)")
@Check(name = "chk_user_daily_missions_quiz_filters", constraints = MissionTemplate.QUIZ_FILTERS_CHECK)
@Check(name = "chk_user_daily_missions_reward", constraints = MissionTemplate.REWARD_CHECK)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDailyMission extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mission_template_id", nullable = false)
	private MissionTemplate template;

	@Column(name = "mission_date", nullable = false)
	private LocalDate missionDate;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(nullable = false)
	private Integer slot;

	@Column(nullable = false, length = 64)
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

	@Column(name = "current_progress", nullable = false)
	@ColumnDefault("0")
	@Builder.Default
	private Integer currentProgress = 0;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private DailyMissionStatus status = DailyMissionStatus.IN_PROGRESS;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "claimed_at")
	private Instant claimedAt;

	/** Snapshot of {@code template} for {@code date}, IN_PROGRESS at 0 (daily_mission.md §4.2). */
	public static UserDailyMission assign(User user, MissionTemplate template, LocalDate date, int slot) {
		return UserDailyMission.builder()
				.user(user)
				.template(template)
				.missionDate(date)
				.slot(slot)
				.code(template.getCode())
				.name(template.getName())
				.description(template.getDescription())
				.category(template.getCategory())
				.triggerEvent(template.getTriggerEvent())
				.minQuizAccuracyPercent(template.getMinQuizAccuracyPercent())
				.quizRequestedCount(template.getQuizRequestedCount())
				.targetValue(template.getTargetValue())
				.rewardCoin(template.getRewardCoin())
				.rewardXp(template.getRewardXp())
				.rewardItemCode(template.getRewardItemCode())
				.rewardItemFallbackCoin(template.getRewardItemFallbackCoin())
				.build();
	}

	/** Snapshot filters (daily_mission.md §6.2); accuracy compared in integers, §5.1. */
	public boolean accepts(MissionFacts facts) {
		if (quizRequestedCount != null && quizRequestedCount != facts.requestedCount()) {
			return false;
		}
		if (minQuizAccuracyPercent != null) {
			return facts.totalUnits() > 0
					&& (long) facts.correctCount() * 100 >= (long) facts.totalUnits() * minQuizAccuracyPercent;
		}
		return true;
	}

	/** +1, clamped at target; reaching the target is the single IN_PROGRESS → COMPLETED transition. */
	public void advance(Instant actionInstant) {
		currentProgress = Math.min(targetValue, currentProgress + 1);
		if (currentProgress.equals(targetValue)) {
			status = DailyMissionStatus.COMPLETED;
			completedAt = actionInstant;
		}
	}
}
