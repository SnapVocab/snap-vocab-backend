package vn.ptit.snapvocab.adminusers.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.ptit.snapvocab.adminstats.repository.AdminStatsRepository;
import vn.ptit.snapvocab.user.domain.User;

/**
 * Read side of Admin › Người học (snapAdmin domains/admin-learners/types.ts). Learners only: ROLE_ADMIN accounts are
 * never listed, shown or banned here. Times are UTC, returned as ISO strings like AdminStatsRepository.
 */
public interface AdminUserRepository extends Repository<User, Long> {

	String LEARNER = AdminStatsRepository.LEARNER;
	String ISO = "'%Y-%m-%dT%H:%i:%sZ'";

	/**
	 * One row of the directory. The streak is the stored chain while it reaches yesterday, else 0.
	 * ponytail: ignores Streak Freeze cover, so a frozen chain shows 0 here until the learner studies again; the
	 * profile uses ProgressService.getStreak, which applies it.
	 */
	String ROW = """
			SELECT u.id AS id, TRIM(CONCAT_WS(' ', u.first_name, u.last_name)) AS displayName, u.email AS email,
			       u.avatar_url AS avatarKey,
			       CASE WHEN u.banned_at IS NOT NULL THEN 'BANNED' WHEN u.activated THEN 'ACTIVE' ELSE 'PENDING' END AS status,
			       DATE_FORMAT(u.created_at, """ + ISO + """
			) AS createdAt,
			       DATE_FORMAT(u.last_studied_at, """ + ISO + """
			) AS lastStudiedAt,
			       CAST(CASE WHEN u.last_studied_at >= :yesterdayStart AND u.streak_days > 0 THEN u.streak_days ELSE 0 END AS SIGNED) AS streakDays,
			       CAST(u.longest_streak_days AS SIGNED) AS longestStreakDays,
			       (SELECT COUNT(*) FROM topic_items ti JOIN topics t ON t.id = ti.topic_id
			          JOIN collections c ON c.id = t.collection_id WHERE c.owner_id = u.id) AS wordsSaved,
			       CAST(COALESCE(u.coin, 0) AS SIGNED) AS coin, CAST(COALESCE(u.exp, 0) AS SIGNED) AS exp,
			       u.last_studied_at AS lastStudiedSort, u.created_at AS createdSort
			FROM users u
			""";

	/** Search, status and activity filters (activity from users.last_studied_at, like the Admin's activityOf). */
	String FILTER = """
			WHERE u.id""" + LEARNER + """

			  AND (:q IS NULL OR u.email LIKE CONCAT('%', :q, '%')
			       OR CONCAT_WS(' ', u.first_name, u.last_name) LIKE CONCAT('%', :q, '%'))
			  AND (:status IS NULL
			       OR (:status = 'BANNED' AND u.banned_at IS NOT NULL)
			       OR (:status = 'ACTIVE' AND u.banned_at IS NULL AND u.activated)
			       OR (:status = 'PENDING' AND u.banned_at IS NULL AND NOT u.activated))
			  AND (:activity IS NULL
			       OR (:activity = 'ACTIVE_7D' AND u.last_studied_at >= :now7)
			       OR (:activity = 'DORMANT_30D' AND u.last_studied_at < :now30)
			       OR (:activity = 'NEVER' AND u.last_studied_at IS NULL))
			""";

	@Query(value = "SELECT * FROM (" + ROW + FILTER + """
			) x
			ORDER BY CASE WHEN :sort = 'streak' THEN x.streakDays END DESC,
			         CASE WHEN :sort = 'words' THEN x.wordsSaved END DESC,
			         CASE WHEN :sort = 'createdAt' THEN x.createdSort END DESC,
			         x.lastStudiedSort IS NULL, x.lastStudiedSort DESC, x.id DESC
			LIMIT :size OFFSET :offset""", nativeQuery = true)
	List<Row> page(@Param("q") String q, @Param("status") String status, @Param("activity") String activity,
			@Param("sort") String sort, @Param("now7") Instant now7, @Param("now30") Instant now30,
			@Param("yesterdayStart") Instant yesterdayStart, @Param("size") int size, @Param("offset") int offset);

	@Query(value = "SELECT COUNT(*) FROM users u " + FILTER, nativeQuery = true)
	long count(@Param("q") String q, @Param("status") String status, @Param("activity") String activity,
			@Param("now7") Instant now7, @Param("now30") Instant now30);

	@Query(value = ROW + "WHERE u.id = :id AND u.id" + LEARNER, nativeQuery = true)
	Optional<Row> row(@Param("id") Long id, @Param("yesterdayStart") Instant yesterdayStart);

	/** Whole learner population, filters ignored (the summary strip). */
	@Query(value = """
			SELECT COUNT(*) AS total,
			       CAST(COALESCE(SUM(u.last_studied_at >= :now7), 0) AS SIGNED) AS active7d,
			       CAST(COALESCE(SUM(u.last_studied_at < :now30), 0) AS SIGNED) AS dormant30d,
			       CAST(COALESCE(SUM(u.last_studied_at IS NULL), 0) AS SIGNED) AS never,
			       CAST(COALESCE(SUM(u.banned_at IS NULL AND NOT u.activated), 0) AS SIGNED) AS pending,
			       CAST(COALESCE(SUM(u.banned_at IS NOT NULL), 0) AS SIGNED) AS banned
			FROM users u
			WHERE u.id""" + LEARNER, nativeQuery = true)
	Summary summary(@Param("now7") Instant now7, @Param("now30") Instant now30);

	@Query(value = """
			SELECT COUNT(*) AS assigned,
			       CAST(COALESCE(SUM(m.status IN ('COMPLETED', 'CLAIMED')), 0) AS SIGNED) AS completed,
			       CAST(COALESCE(SUM(m.status = 'CLAIMED'), 0) AS SIGNED) AS claimed
			FROM user_daily_missions m
			WHERE m.user_id = :id AND m.mission_date BETWEEN :from AND :to""", nativeQuery = true)
	Missions missions(@Param("id") Long id, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query(value = "SELECT b.code AS code, b.name AS name, DATE_FORMAT(ub.created_at, " + ISO + """
			) AS at
			FROM user_badges ub JOIN badges b ON b.id = ub.badge_id
			WHERE ub.user_id = :id
			ORDER BY ub.created_at""", nativeQuery = true)
	List<BadgeRow> badges(@Param("id") Long id);

	@Query(value = "SELECT DATE_FORMAT(ct.created_at, " + ISO + """
			) AS at, ct.amount AS amount, ct.source_type AS source, ct.description AS note
			FROM coin_transactions ct
			WHERE ct.user_id = :id
			ORDER BY ct.created_at DESC, ct.id DESC
			LIMIT 6""", nativeQuery = true)
	List<CoinRow> recentCoins(@Param("id") Long id);

	@Query(value = """
			SELECT si.code AS code, si.name AS name, ui.item_type AS type, CAST(ui.equipped AS SIGNED) AS equipped,
			       CAST(ui.quantity AS SIGNED) AS quantity
			FROM user_items ui JOIN shop_items si ON si.id = ui.shop_item_id
			WHERE ui.user_id = :id AND ui.quantity > 0
			ORDER BY ui.acquired_at DESC""", nativeQuery = true)
	List<ItemRow> items(@Param("id") Long id);

	/** Scans the learner ran (a QUOTA_EXCEEDED refusal is no scan) since {@code since}. */
	@Query(value = """
			SELECT COUNT(*) AS total, CAST(COALESCE(SUM(s.status = 'FAILED'), 0) AS SIGNED) AS failed
			FROM scan_requests s
			WHERE s.user_id = :id AND s.created_at >= :since AND COALESCE(s.error_code, '') <> 'QUOTA_EXCEEDED'""",
			nativeQuery = true)
	ScanTotals scanTotals(@Param("id") Long id, @Param("since") Instant since);

	@Query(value = "SELECT s.id AS id, DATE_FORMAT(s.created_at, " + ISO + """
			) AS at, s.status AS status, s.error_code AS errorCode, s.result_json AS resultJson,
			       (SELECT COUNT(*) FROM topic_items ti WHERE ti.source_scan_id = s.id) AS savedWords
			FROM scan_requests s
			WHERE s.user_id = :id AND s.status IN ('DONE', 'FAILED')
			  AND COALESCE(s.error_code, '') <> 'QUOTA_EXCEEDED'
			ORDER BY s.created_at DESC
			LIMIT 4""", nativeQuery = true)
	List<ScanRow> recentScans(@Param("id") Long id);

	@Query(value = "SELECT DATE_FORMAT(q.completed_at, " + ISO + """
			) AS at, CAST(q.correct_count AS SIGNED) AS correct, CAST(q.total_units AS SIGNED) AS total
			FROM quizzes q
			WHERE q.user_id = :id AND q.status = 'COMPLETED' AND q.completed_at IS NOT NULL
			ORDER BY q.completed_at DESC
			LIMIT 2""", nativeQuery = true)
	List<QuizRow> recentQuizzes(@Param("id") Long id);

	interface Row {
		Long getId();

		String getDisplayName();

		String getEmail();

		String getAvatarKey();

		String getStatus();

		String getCreatedAt();

		String getLastStudiedAt();

		Long getStreakDays();

		Long getLongestStreakDays();

		Long getWordsSaved();

		Long getCoin();

		Long getExp();
	}

	interface Summary {
		Long getTotal();

		Long getActive7d();

		Long getDormant30d();

		Long getNever();

		Long getPending();

		Long getBanned();
	}

	interface Missions {
		Long getAssigned();

		Long getCompleted();

		Long getClaimed();
	}

	interface BadgeRow {
		String getCode();

		String getName();

		String getAt();
	}

	interface CoinRow {
		String getAt();

		Long getAmount();

		String getSource();

		String getNote();
	}

	interface ItemRow {
		String getCode();

		String getName();

		String getType();

		Long getEquipped();

		Long getQuantity();
	}

	interface ScanTotals {
		Long getTotal();

		Long getFailed();
	}

	interface ScanRow {
		String getId();

		String getAt();

		String getStatus();

		String getErrorCode();

		String getResultJson();

		Long getSavedWords();
	}

	interface QuizRow {
		String getAt();

		Long getCorrect();

		Long getTotal();
	}
}
