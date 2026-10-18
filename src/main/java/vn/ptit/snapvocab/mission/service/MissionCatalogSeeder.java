package vn.ptit.snapvocab.mission.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;

/**
 * Seeds the MVP catalog (daily_mission.md §5.1, §7.7) by code, never overwriting an existing row, then checks the
 * catalog invariant:
 * <ul>
 * <li>no active template at all ⇒ the feature is not activated yet: WARN, startup continues, GET answers 503;</li>
 * <li>any active template ⇒ the catalog is live and must satisfy the invariant, else startup fails, whether the rows
 * came from this seed, SQL or a future Admin.</li>
 * </ul>
 */
@Component
public class MissionCatalogSeeder implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(MissionCatalogSeeder.class);

	private final MissionTemplateRepository templateRepository;

	public MissionCatalogSeeder(MissionTemplateRepository templateRepository) {
		this.templateRepository = templateRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(mvpCatalog());
	}

	void seed(List<MissionTemplate> catalog) {
		for (MissionTemplate template : catalog) {
			if (templateRepository.findByCode(template.getCode()).isEmpty()) {
				templateRepository.save(template);
			}
		}
		List<MissionTemplate> active = templateRepository.findByActiveTrueOrderById();
		if (active.isEmpty()) {
			LOG.warn("Daily Mission not activated: no active mission template; GET /api/me/daily-missions answers 503");
		} else if (!MissionTracker.satisfiesInvariant(active)) {
			throw new IllegalStateException("Active mission catalog (" + active.size()
					+ " templates) violates the daily_mission.md §4.2 invariant");
		}
	}

	/** daily_mission.md §5.1; UI names of ADD_5_WORDS, FINISH_2_SHORT_QUIZZES, PERFECT_MINI_QUIZ per COPY-DM-01. */
	static List<MissionTemplate> mvpCatalog() {
		return List.of(
				template("ADD_5_WORDS", "Góp Nhặt Từ Mới", "Thêm 5 từ mới có nghĩa", MissionCategory.VOCABULARY,
						MissionTriggerEvent.TOPIC_ITEM_CREATED, null, null, 5, 15, 25, MissionEligibilityRule.NONE),
				template("QUIZ_SCORE_80", "Trí Nhớ Siêu Phàm", "Hoàn thành 1 quiz với độ chính xác ≥ 80%",
						MissionCategory.QUIZ, MissionTriggerEvent.QUIZ_COMPLETED, 80, null, 1, 20, 40,
						MissionEligibilityRule.NONE),
				template("FINISH_2_SHORT_QUIZZES", "Hai Bài Quiz Ngắn", "Hoàn thành 2 quiz 5 câu", MissionCategory.QUIZ,
						MissionTriggerEvent.QUIZ_COMPLETED, null, 5, 2, 15, 30, MissionEligibilityRule.NONE),
				template("PERFECT_MINI_QUIZ", "Quiz Ngắn Hoàn Hảo", "Đạt 100% một quiz 5 câu", MissionCategory.QUIZ,
						MissionTriggerEvent.QUIZ_COMPLETED, 100, 5, 1, 25, 45, MissionEligibilityRule.NONE),
				template("KEEP_DAILY_STREAK", "Giữ Lửa Mỗi Ngày", "Có ít nhất 1 hoạt động học trong ngày",
						MissionCategory.RETENTION, MissionTriggerEvent.LEARNING_ACTIVITY_RECORDED, null, null, 1, 10, 20,
						MissionEligibilityRule.NONE),
				template("USE_XP_BOOSTER", "Tăng Tốc Học Tập", "Kích hoạt 1 XP Booster", MissionCategory.SHOP,
						MissionTriggerEvent.BOOSTER_USED, null, null, 1, 5, 10, MissionEligibilityRule.HAS_IDLE_XP_BOOSTER),
				template("BUY_SHOP_ITEM", "Ghé Thăm Cửa Hàng", "Mua 1 vật phẩm trong Shop", MissionCategory.SHOP,
						MissionTriggerEvent.SHOP_ITEM_PURCHASED, null, null, 1, 5, 10,
						MissionEligibilityRule.HAS_PURCHASABLE_ITEM));
	}

	private static MissionTemplate template(String code, String name, String description, MissionCategory category,
			MissionTriggerEvent trigger, Integer minAccuracy, Integer requestedCount, int target, int coin, int xp,
			MissionEligibilityRule rule) {
		return MissionTemplate.builder()
				.code(code)
				.name(name)
				.description(description)
				.category(category)
				.triggerEvent(trigger)
				.minQuizAccuracyPercent(minAccuracy)
				.quizRequestedCount(requestedCount)
				.targetValue(target)
				.rewardCoin(coin)
				.rewardXp(xp)
				.eligibilityRule(rule)
				.active(true)
				.build();
	}
}
