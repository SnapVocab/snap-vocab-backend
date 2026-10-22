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
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;

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
		assertThat(MissionTracker.satisfiesInvariant(without(mvp, "KEEP_DAILY_STREAK", "ADD_3_WORDS")))
				.as("booster/shop are not NONE").isFalse();
		assertThat(MissionTracker.satisfiesInvariant(without(mvp, "QUIZ_SCORE_80", "ADD_3_WORDS"))).isFalse();
		List<MissionTemplate> fiveCore = new ArrayList<>(without(mvp, "KEEP_DAILY_STREAK", "ADD_3_WORDS"));
		// SCAN/SRS templates are active but use new triggers: an older app never sees them, so they do not count.
		assertThat(fiveCore).extracting(MissionTemplate::getCode).contains("SCAN_SAVE_3_WORDS", "STUDY_5_CARDS");
		fiveCore.add(template("EXTRA", MissionCategory.SCAN, MissionEligibilityRule.NONE));
		assertThat(MissionTracker.satisfiesInvariant(fiveCore)).isTrue();
	}

	@Test
	void choiceFillsSlotsByCategoryWithCoreFallbackAndDistinctTemplates() {
		List<MissionTemplate> mvp = legacy(without(MissionCatalogSeeder.mvpCatalog(), "USE_XP_BOOSTER", "BUY_SHOP_ITEM",
				"ADD_3_WORDS"));
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
	void ownSlotsPickBeforeFallbackSoSlotsOneAndThreeKeepTheirTemplates() { // PD-DM-07
		List<MissionTemplate> catalog = MissionCatalogSeeder.mvpCatalog();
		catalog.forEach(t -> t.setActive(true));
		// STUDY_10_CARDS is gated (HAS_STUDY_CARDS_FOR_TARGET): a learner short of cards is handed the rest.
		List<MissionTemplate> mvp = without(catalog, "USE_XP_BOOSTER", "BUY_SHOP_ITEM", "STUDY_10_CARDS");
		for (int i = 0; i < 50; i++) {
			List<MissionTemplate> chosen = MissionTracker.choose(mvp, Set.of());
			assertThat(chosen).extracting(MissionTemplate::getCode).doesNotHaveDuplicates()
					.satisfies(codes -> {
						assertThat(codes.get(0)).isIn("SCAN_SAVE_3_WORDS", "SCAN_SAVE_5_WORDS");
						assertThat(codes.get(1)).isIn("ADD_5_WORDS", "ADD_3_WORDS");
						assertThat(codes.get(2)).isEqualTo("STUDY_5_CARDS");
						assertThat(codes.get(4)).isEqualTo("KEEP_DAILY_STREAK");
					});
			// Every learning slot has its own template now: one quiz a day instead of three.
			assertThat(chosen).filteredOn(t -> t.getCategory() == MissionCategory.QUIZ).hasSize(1);
		}
	}

	@Test
	void variantsRotateAwayFromYesterdaysTemplateInEverySlot() { // PD-DM-12
		List<MissionTemplate> catalog = MissionCatalogSeeder.mvpCatalog();
		catalog.forEach(t -> t.setActive(true));
		List<MissionTemplate> eligible = without(catalog, "USE_XP_BOOSTER", "BUY_SHOP_ITEM");
		List<MissionTemplate> chosen = MissionTracker.choose(eligible,
				Set.of("SCAN_SAVE_3_WORDS", "ADD_5_WORDS", "STUDY_5_CARDS", "QUIZ_SCORE_80", "FINISH_2_SHORT_QUIZZES"));
		assertThat(chosen).extracting(MissionTemplate::getCode).containsExactly("SCAN_SAVE_5_WORDS", "ADD_3_WORDS",
				"STUDY_10_CARDS", "PERFECT_MINI_QUIZ", "KEEP_DAILY_STREAK");
	}

	@Test
	void softPreferenceFallsBackToYesterdaysTemplateWhenNothingElseFits() {
		List<MissionTemplate> mvp = without(MissionCatalogSeeder.mvpCatalog(), "USE_XP_BOOSTER", "BUY_SHOP_ITEM",
				"ADD_3_WORDS");
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

	/** The active part of {@code catalog} minus {@code codes}: what choose and the invariant are handed. */
	private static List<MissionTemplate> without(List<MissionTemplate> catalog, String... codes) {
		Set<String> drop = Set.of(codes);
		return catalog.stream().filter(MissionTemplate::getActive).filter(t -> !drop.contains(t.getCode())).toList();
	}

	private static MissionTemplate template(String code, MissionCategory category, MissionEligibilityRule rule) {
		return MissionTemplate.builder().code(code).category(category).eligibilityRule(rule)
				.triggerEvent(MissionTriggerEvent.TOPIC_ITEM_CREATED).active(true).build();
	}

	/** Only the templates an app without X-Mission-Triggers renders (PD-DM-13). */
	private static List<MissionTemplate> legacy(List<MissionTemplate> catalog) {
		return catalog.stream().filter(t -> MissionTriggerEvent.LEGACY.contains(t.getTriggerEvent())).toList();
	}
}
