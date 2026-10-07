package vn.ptit.snapvocab.mission.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

/** Admin view of a catalog row (daily_mission.md §8.3). */
public record MissionTemplateDTO(Long id, String code, String name, String description, MissionCategory category,
		MissionTriggerEvent triggerEvent, Integer minQuizAccuracyPercent, Integer quizRequestedCount,
		Integer targetValue, Integer rewardCoin, Integer rewardXp, String rewardItemCode,
		Integer rewardItemFallbackCoin, Integer weight, MissionEligibilityRule eligibilityRule,
		@JsonProperty("isActive") Boolean isActive, Long version, Instant createdAt, Instant updatedAt) {

	public static MissionTemplateDTO of(MissionTemplate t) {
		return new MissionTemplateDTO(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getCategory(),
				t.getTriggerEvent(), t.getMinQuizAccuracyPercent(), t.getQuizRequestedCount(), t.getTargetValue(),
				t.getRewardCoin(), t.getRewardXp(), t.getRewardItemCode(), t.getRewardItemFallbackCoin(),
				t.getWeight(), t.getEligibilityRule(), t.getActive(), t.getVersion(), t.getCreatedAt(),
				t.getUpdatedAt());
	}
}
