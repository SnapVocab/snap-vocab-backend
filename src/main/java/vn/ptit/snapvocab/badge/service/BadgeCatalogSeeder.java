package vn.ptit.snapvocab.badge.service;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;

/**
 * Inserts each MVP badge (badges.md §4.1) whose code is missing; never updates an existing row, so names, images,
 * order and activation changed by migration SQL survive restarts (§7.4).
 */
@Component
public class BadgeCatalogSeeder implements ApplicationRunner {

	private final BadgeRepository badgeRepository;

	public BadgeCatalogSeeder(BadgeRepository badgeRepository) {
		this.badgeRepository = badgeRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(mvpCatalog());
	}

	void seed(List<Badge> catalog) {
		for (Badge badge : catalog) {
			if (badgeRepository.findByCode(badge.getCode()).isEmpty()) {
				badgeRepository.save(badge);
			}
		}
	}

	/** badges.md §4.1, names and descriptions per COPY-BDG-01; rewards per PD-BDG-14 (last-tier frames are set by Admin). */
	static List<Badge> mvpCatalog() {
		return List.of(
				badge(10, "STREAK_7", BadgeMetric.LONGEST_STREAK_DAYS, 7, "Một Tuần Bền Bỉ",
						"Học (quiz, ôn thẻ hoặc lưu từ đã quét) 7 ngày trong một chuỗi không đứt"),
				badge(11, "STREAK_30", BadgeMetric.LONGEST_STREAK_DAYS, 30, "Tháng Không Nghỉ",
						"Học (quiz, ôn thẻ hoặc lưu từ đã quét) 30 ngày trong một chuỗi không đứt"),
				badge(12, "STREAK_100", BadgeMetric.LONGEST_STREAK_DAYS, 100, "Trăm Ngày Kiên Trì",
						"Học (quiz, ôn thẻ hoặc lưu từ đã quét) 100 ngày trong một chuỗi không đứt"),
				badge(20, "QUIZ_1", BadgeMetric.QUIZ_COMPLETED, 1, "Bài Quiz Đầu Tiên", "Hoàn thành 1 quiz"),
				badge(21, "QUIZ_10", BadgeMetric.QUIZ_COMPLETED, 10, "Quen Tay Quiz", "Hoàn thành 10 quiz"),
				badge(22, "QUIZ_50", BadgeMetric.QUIZ_COMPLETED, 50, "Chiến Binh Quiz", "Hoàn thành 50 quiz"),
				badge(30, "PERFECT_1", BadgeMetric.PERFECT_QUIZ, 1, "Điểm Tuyệt Đối",
						"Đúng tất cả câu (hoặc tất cả cặp) trong 1 quiz"),
				badge(31, "PERFECT_10", BadgeMetric.PERFECT_QUIZ, 10, "Mười Lần Tuyệt Đối",
						"Đạt điểm tuyệt đối 10 lần (mỗi quiz đúng tất cả câu hoặc cặp)"),
				badge(32, "PERFECT_50", BadgeMetric.PERFECT_QUIZ, 50, "Năm Mươi Lần Tuyệt Đối",
						"Đạt điểm tuyệt đối 50 lần (mỗi quiz đúng tất cả câu hoặc cặp)"),
				badge(40, "WORDS_10", BadgeMetric.DISTINCT_WORDS_ADDED, 10, "Mười Từ Đầu Tiên",
						"Tự thêm 10 từ khác nhau vào bộ sưu tập của bạn"),
				badge(41, "WORDS_100", BadgeMetric.DISTINCT_WORDS_ADDED, 100, "Kho Từ Trăm Chữ",
						"Tự thêm 100 từ khác nhau vào bộ sưu tập của bạn"),
				badge(42, "WORDS_500", BadgeMetric.DISTINCT_WORDS_ADDED, 500, "Thư Viện Sống",
						"Tự thêm 500 từ khác nhau vào bộ sưu tập của bạn"));
	}

	/** PD-BDG-14 by tier (sort order ends in 0, 1, 2): Coin + XP for the first two, XP (plus a frame) for the last. */
	private static final int[][] TIER_REWARD = {{20, 50}, {60, 150}, {0, 300}};

	private static Badge badge(int sortOrder, String code, BadgeMetric metric, int threshold, String name,
			String description) {
		int[] reward = TIER_REWARD[sortOrder % 10];
		return Badge.builder()
				.code(code)
				.name(name)
				.description(description)
				.metric(metric)
				.threshold(threshold)
				.imageKey(AdminBadgeService.IMAGE_PREFIX + code.toLowerCase(Locale.ROOT) + ".png")
				.sortOrder(sortOrder)
				.rewardCoin(reward[0])
				.rewardXp(reward[1])
				.build();
	}
}
