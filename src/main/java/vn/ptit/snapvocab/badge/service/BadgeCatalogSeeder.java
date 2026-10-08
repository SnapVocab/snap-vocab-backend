package vn.ptit.snapvocab.badge.service;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;

/**
 * Inserts each MVP badge (badges.md §4.1) whose code is missing; never updates an existing row, so names, icons,
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

	/** badges.md §4.1, names and descriptions per COPY-BDG-01. */
	static List<Badge> mvpCatalog() {
		return List.of(
				badge(10, "STREAK_7", BadgeMetric.LONGEST_STREAK_DAYS, 7, "Một Tuần Bền Bỉ",
						"Hoàn thành ít nhất 1 quiz mỗi ngày, 7 ngày liên tiếp", "flame"),
				badge(11, "STREAK_30", BadgeMetric.LONGEST_STREAK_DAYS, 30, "Tháng Không Nghỉ",
						"Hoàn thành ít nhất 1 quiz mỗi ngày, 30 ngày liên tiếp", "flame"),
				badge(12, "STREAK_100", BadgeMetric.LONGEST_STREAK_DAYS, 100, "Trăm Ngày Kiên Trì",
						"Hoàn thành ít nhất 1 quiz mỗi ngày, 100 ngày liên tiếp", "flame"),
				badge(20, "QUIZ_1", BadgeMetric.QUIZ_COMPLETED, 1, "Bài Quiz Đầu Tiên", "Hoàn thành 1 quiz",
						"clipboard-check"),
				badge(21, "QUIZ_10", BadgeMetric.QUIZ_COMPLETED, 10, "Quen Tay Quiz", "Hoàn thành 10 quiz",
						"clipboard-check"),
				badge(22, "QUIZ_50", BadgeMetric.QUIZ_COMPLETED, 50, "Chiến Binh Quiz", "Hoàn thành 50 quiz",
						"clipboard-check"),
				badge(30, "PERFECT_1", BadgeMetric.PERFECT_QUIZ, 1, "Điểm Tuyệt Đối",
						"Đúng tất cả câu (hoặc tất cả cặp) trong 1 quiz", "target"),
				badge(31, "PERFECT_10", BadgeMetric.PERFECT_QUIZ, 10, "Mười Lần Tuyệt Đối",
						"Đạt điểm tuyệt đối 10 lần (mỗi quiz đúng tất cả câu hoặc cặp)", "target"),
				badge(32, "PERFECT_50", BadgeMetric.PERFECT_QUIZ, 50, "Năm Mươi Lần Tuyệt Đối",
						"Đạt điểm tuyệt đối 50 lần (mỗi quiz đúng tất cả câu hoặc cặp)", "target"),
				badge(40, "WORDS_10", BadgeMetric.DISTINCT_WORDS_ADDED, 10, "Mười Từ Đầu Tiên",
						"Tự thêm 10 từ khác nhau vào bộ sưu tập của bạn", "book-open"),
				badge(41, "WORDS_100", BadgeMetric.DISTINCT_WORDS_ADDED, 100, "Kho Từ Trăm Chữ",
						"Tự thêm 100 từ khác nhau vào bộ sưu tập của bạn", "book-open"),
				badge(42, "WORDS_500", BadgeMetric.DISTINCT_WORDS_ADDED, 500, "Thư Viện Sống",
						"Tự thêm 500 từ khác nhau vào bộ sưu tập của bạn", "book-open"));
	}

	private static Badge badge(int sortOrder, String code, BadgeMetric metric, int threshold, String name,
			String description, String iconKey) {
		return Badge.builder()
				.code(code)
				.name(name)
				.description(description)
				.metric(metric)
				.threshold(threshold)
				.iconKey(iconKey)
				.sortOrder(sortOrder)
				.build();
	}
}
