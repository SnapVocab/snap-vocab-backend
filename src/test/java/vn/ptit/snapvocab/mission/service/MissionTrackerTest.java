package vn.ptit.snapvocab.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.UserDailyMission;
import vn.ptit.snapvocab.mission.domain.enumeration.DailyMissionStatus;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;

class MissionTrackerTest {

	@Test
	void wordHashNormalizesCaseWhitespaceAndUnicodeComposition() {
		byte[] apple = MissionTracker.wordHash("apple");
		assertThat(MissionTracker.wordHash("  APPLE\t")).isEqualTo(apple);
		assertThat(MissionTracker.wordHash("\u00a0APPLE\u202f")).isEqualTo(apple);
		assertThat(MissionTracker.wordHash("ice   cream")).isEqualTo(MissionTracker.wordHash("Ice Cream"));
		assertThat(MissionTracker.wordHash("café")).isEqualTo(MissionTracker.wordHash("café"));
		assertThat(MissionTracker.wordHash("apples")).isNotEqualTo(apple);
		assertThat(apple).hasSize(32);
	}

	@Test
	void largeValidWeightsDoNotOverflowTheRandomBound() {
		List<MissionTemplate> catalog = without(MissionCatalogSeeder.mvpCatalog(), "USE_XP_BOOSTER", "BUY_SHOP_ITEM");
		catalog.forEach(t -> t.setWeight(Integer.MAX_VALUE));
		assertThat(MissionTracker.choose(catalog, Set.of())).hasSize(5).doesNotHaveDuplicates();
	}

	@Test
	void missionDayFollowsAsiaHoChiMinh() {
		assertThat(MissionTracker.missionDate(Instant.parse("2026-10-05T16:59:59.999999Z"))).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(MissionTracker.missionDate(Instant.parse("2026-10-05T17:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 6));
	}

	@Test
	void invariantNeedsFourCoreNonePlusASlotFiveOrAFifthCore() {
		List<MissionTemplate> mvp = MissionCatalogSeeder.mvpCatalog();
		assertThat(MissionTracker.satisfiesInvariant(mvp)).isTrue();
		assertThat(MissionTracker.satisfiesInvariant(without(mvp, "KEEP_DAILY_STREAK"))).as("booster/shop are not NONE").isFalse();
		assertThat(MissionTracker.satisfiesInvariant(without(mvp, "QUIZ_SCORE_80"))).isFalse();
		List<MissionTemplate> fiveCore = new ArrayList<>(without(mvp, "KEEP_DAILY_STREAK"));
		fiveCore.add(template("EXTRA", MissionCategory.SCAN, MissionEligibilityRule.NONE));
		assertThat(MissionTracker.satisfiesInvariant(fiveCore)).isTrue();
	}

	@Test
	void choiceFillsSlotsByCategoryWithCoreFallbackAndDistinctTemplates() {
		List<MissionTemplate> mvp = without(MissionCatalogSeeder.mvpCatalog(), "USE_XP_BOOSTER", "BUY_SHOP_ITEM");
		for (int i = 0; i < 50; i++) {
			List<MissionTemplate> chosen = MissionTracker.choose(mvp, Set.of());
			assertThat(chosen).hasSize(5).doesNotHaveDuplicates();
			// Slot 1 (SCAN) and 3 (FLASHCARD/SRS) have no template: they fall back to any remaining core one.
			assertThat(chosen.subList(0, 4)).extracting(MissionTemplate::getCode).containsExactlyInAnyOrder(
					"ADD_5_WORDS", "QUIZ_SCORE_80", "FINISH_2_SHORT_QUIZZES", "PERFECT_MINI_QUIZ");
			assertThat(chosen.get(4).getCode()).isEqualTo("KEEP_DAILY_STREAK");
		}
		assertThat(MissionTracker.choose(without(mvp, "KEEP_DAILY_STREAK"), Set.of())).isNull();
	}

	@Test
	void softPreferenceFallsBackToYesterdaysTemplateWhenNothingElseFits() {
		List<MissionTemplate> mvp = without(MissionCatalogSeeder.mvpCatalog(), "USE_XP_BOOSTER", "BUY_SHOP_ITEM");
		List<MissionTemplate> chosen = MissionTracker.choose(mvp, Set.of("KEEP_DAILY_STREAK", "ADD_5_WORDS"));
		assertThat(chosen).extracting(MissionTemplate::getCode).contains("KEEP_DAILY_STREAK", "ADD_5_WORDS");
	}

	@Test
	void snapshotFiltersAndAdvanceClampAtTarget() {
		UserDailyMission perfect = UserDailyMission.assign(null,
				MissionCatalogSeeder.mvpCatalog().stream().filter(t -> t.getCode().equals("PERFECT_MINI_QUIZ")).findFirst()
						.orElseThrow(), LocalDate.now(), 4);
		assertThat(perfect.accepts(MissionFacts.quiz(5, 5, 5))).isTrue();
		assertThat(perfect.accepts(MissionFacts.quiz(5, 4, 5))).isFalse();
		assertThat(perfect.accepts(MissionFacts.quiz(10, 10, 10))).isFalse();

		Instant at = Instant.parse("2026-10-05T03:00:00Z");
		perfect.advance(at);
		perfect.advance(at.plusSeconds(1));
		assertThat(perfect.getCurrentProgress()).isEqualTo(1);
		assertThat(perfect.getStatus()).isEqualTo(DailyMissionStatus.COMPLETED);
		assertThat(perfect.getCompletedAt()).isEqualTo(at.plusSeconds(1));
	}

	private static List<MissionTemplate> without(List<MissionTemplate> catalog, String... codes) {
		Set<String> drop = Set.of(codes);
		return catalog.stream().filter(t -> !drop.contains(t.getCode())).toList();
	}

	private static MissionTemplate template(String code, MissionCategory category, MissionEligibilityRule rule) {
		return MissionTemplate.builder().code(code).category(category).eligibilityRule(rule).active(true).build();
	}
}
