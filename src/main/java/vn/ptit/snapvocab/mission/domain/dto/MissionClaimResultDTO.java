package vn.ptit.snapvocab.mission.domain.dto;

import vn.ptit.snapvocab.mission.domain.UserDailyMissionClaim;
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;

/** {@code POST /api/me/daily-missions/{id}/claim} (daily_mission.md §8.2). A replay reports the original grant. */
public record MissionClaimResultDTO(Long missionId, DailyMissionStatus status, Granted rewardGranted, long coinBalance,
		long totalXp, boolean replayed) {

	public record Granted(int coin, int xp, String itemCode, String itemName, int itemQuantity, long fallbackCoin) {

		public static Granted of(UserDailyMissionClaim claim, String itemName) {
			return new Granted(claim.getRewardCoin(), claim.getRewardXp(), claim.getRewardItemCode(), itemName,
					claim.getGrantedItemQuantity(), claim.getFallbackCoin());
		}
	}
}
