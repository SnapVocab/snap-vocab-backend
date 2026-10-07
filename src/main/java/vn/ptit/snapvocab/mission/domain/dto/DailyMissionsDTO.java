package vn.ptit.snapvocab.mission.domain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

/** {@code GET /api/me/daily-missions} (daily_mission.md §8.1). */
public record DailyMissionsDTO(LocalDate missionDate, String timezone, Instant serverNow, Instant resetAt,
		List<Mission> missions) {

	public record Mission(Long id, String code, String name, String description, MissionCategory category,
			MissionTriggerEvent triggerEvent, int slot, int targetValue, int currentProgress, DailyMissionStatus status,
			Reward reward, Instant completedAt, Instant claimedAt) {

		public static Mission of(UserDailyMission m) {
			return new Mission(m.getId(), m.getCode(), m.getName(), m.getDescription(), m.getCategory(),
					m.getTriggerEvent(), m.getSlot(), m.getTargetValue(), m.getCurrentProgress(), m.getStatus(),
					new Reward(m.getRewardCoin(), m.getRewardXp(), m.getRewardItemCode(), m.getRewardItemFallbackCoin()),
					m.getCompletedAt(), m.getClaimedAt());
		}
	}

	public record Reward(int coin, int xp, String itemCode, Integer itemFallbackCoin) {
	}
}
