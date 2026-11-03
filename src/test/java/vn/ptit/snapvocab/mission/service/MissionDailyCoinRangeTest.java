package vn.ptit.snapvocab.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;

/** admin_audit_log_backend.md §4.5.2 with the 75 Coin cap of PD-DM-02 (PO 2026-10-22). */
class MissionDailyCoinRangeTest {

	private static List<MissionTemplate> seedActive() {
		return MissionCatalogSeeder.mvpCatalog().stream().filter(MissionTemplate::getActive).toList();
	}

	private static MissionTemplate byCode(List<MissionTemplate> catalog, String code) {
		return catalog.stream().filter(t -> t.getCode().equals(code)).findFirst().orElseThrow();
	}

	@Test
	void seedCatalogTopsOutAtTheApprovedCap() {
		assertThat(MissionTracker.dailyCoinRange(seedActive())).containsExactly(60, 75);
		assertThat(AdminMissionTemplateService.rewardGuardrail(null, new int[] {60, 75})).isNull();
	}

	@Test
	void raisingAddFiveWordsPastTheCapNeedsAReasonLoweringDoesNot() {
		List<MissionTemplate> catalog = seedActive();
		int[] before = MissionTracker.dailyCoinRange(catalog);
		byCode(catalog, "ADD_5_WORDS").setRewardCoin(40);
		int[] after = MissionTracker.dailyCoinRange(catalog);
		assertThat(after).containsExactly(85, 100);
		assertThat(AdminMissionTemplateService.rewardGuardrail(before, after))
				.isEqualTo("Thưởng mỗi ngày lên 85–100 Coin, vượt mức 75 Coin/ngày đã duyệt.");
		// Back down, or an edit that does not raise an over-cap ceiling: no guardrail.
		assertThat(AdminMissionTemplateService.rewardGuardrail(after, before)).isNull();
		assertThat(AdminMissionTemplateService.rewardGuardrail(after, after)).isNull();
		assertThat(AdminMissionTemplateService.rewardGuardrail(null, new int[] {80, 80}))
				.isEqualTo("Thưởng mỗi ngày lên 80 Coin, vượt mức 75 Coin/ngày đã duyệt.");
	}

	@Test
	void gatedTemplatesAndItemFallbackCoinCountInTheCeiling() {
		List<MissionTemplate> catalog = seedActive();
		MissionTemplate booster = byCode(catalog, "USE_XP_BOOSTER");
		booster.setRewardCoin(50); // HAS_IDLE_XP_BOOSTER: still reachable for a learner who passes that gate
		assertThat(MissionTracker.dailyCoinRange(catalog)).containsExactly(60, 115);
		booster.setRewardCoin(5);
		byCode(catalog, "KEEP_DAILY_STREAK").setRewardItemCode("XP_BOOSTER_X2_15M");
		byCode(catalog, "KEEP_DAILY_STREAK").setRewardItemFallbackCoin(10);
		assertThat(MissionTracker.dailyCoinRange(catalog)).containsExactly(60, 85);
		assertThat(MissionTracker.dailyCoinRange(List.of())).isNull();
		assertThat(MissionEligibilityRule.values()).hasSize(4); // dailyCoinRange walks 2^3 gate mixes
	}
}
