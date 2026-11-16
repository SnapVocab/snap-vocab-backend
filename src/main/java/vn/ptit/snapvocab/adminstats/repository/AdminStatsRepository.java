package vn.ptit.snapvocab.adminstats.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.ptit.snapvocab.user.domain.User;

/**
 * Read-only native aggregates for the Admin Dashboard (admin_overview_backend.md §3.3) and Analytics
 * (admin_analytics_backend.md §5). Every query
 * counts learners only: accounts with ROLE_ADMIN are excluded (§2.3). Instants are UTC; a Vietnam day is
 * {@code col + INTERVAL 7 HOUR} (UTC+7, no DST).
 */
public interface AdminStatsRepository extends Repository<User, Long> {

	/** Appended after a user id column (§2.3, KT-S2). */
	String LEARNER = " NOT IN (SELECT ua.user_id FROM user_authorities ua WHERE ua.authority_name = 'ROLE_ADMIN')";

	// Q-ACT
	@Query(value = "SELECT COUNT(*) FROM users u WHERE u.activated = TRUE AND u.id" + LEARNER, nativeQuery = true)
	Long countActivated();

	// Q-ACT7
	@Query(value = """
			SELECT COUNT(DISTINCT e.user_id) FROM learning_events e
			WHERE e.learning_date BETWEEN :from AND :to AND e.user_id""" + LEARNER, nativeQuery = true)
	Long countActiveLearners(@Param("from") LocalDate from, @Param("to") LocalDate to);

	// Q-DAILY
	@Query(value = """
			SELECT DATE_FORMAT(e.learning_date, '%Y-%m-%d') AS day, COUNT(DISTINCT e.user_id) AS count
			FROM learning_events e
			WHERE e.learning_date BETWEEN :from AND :to AND e.user_id""" + LEARNER + """

			GROUP BY e.learning_date""", nativeQuery = true)
	List<DayCount> activePerDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

	// Q-TODAY
	@Query(value = """
			SELECT
			  (SELECT COUNT(*) FROM topic_items ti JOIN topics t ON t.id = ti.topic_id
			     JOIN collections c ON c.id = t.collection_id
			     WHERE c.type = 'USER' AND ti.created_at >= :start AND ti.created_at < :end AND c.owner_id""" + LEARNER + """
			) AS newWords,
			  (SELECT COUNT(*) FROM learning_events e
			     WHERE e.learning_date = :day AND e.event_type = 'SRS_REVIEW_COMPLETED' AND e.user_id""" + LEARNER + """
			) AS reviews,
			  (SELECT COUNT(*) FROM quizzes q
			     WHERE q.status = 'COMPLETED' AND q.completed_at >= :start AND q.completed_at < :end
			       AND q.user_id""" + LEARNER + """
			) AS quizzes,
			  (SELECT COUNT(*) FROM user_daily_missions m
			     WHERE m.mission_date = :day AND m.status IN ('COMPLETED', 'CLAIMED') AND m.user_id""" + LEARNER + """
			) AS missionsCompleted,
			  (SELECT COUNT(*) FROM (SELECT m.user_id FROM user_daily_missions m
			       WHERE m.mission_date = :day AND m.user_id""" + LEARNER + """

			       GROUP BY m.user_id HAVING SUM(m.status IN ('COMPLETED', 'CLAIMED')) = 5) full_sets) AS fullSets
			""", nativeQuery = true)
	TodayRow today(@Param("day") LocalDate day, @Param("start") Instant start, @Param("end") Instant end);

	// Q-SCANDAY
	@Query(value = """
			SELECT DATE_FORMAT(s.created_at + INTERVAL 7 HOUR, '%Y-%m-%d') AS day,
			       CAST(SUM(s.status = 'DONE') AS SIGNED) AS done,
			       CAST(SUM(s.status = 'FAILED' AND COALESCE(s.error_code, '') <> 'QUOTA_EXCEEDED') AS SIGNED) AS failed
			FROM scan_requests s
			WHERE s.created_at >= :start AND s.created_at < :end AND s.user_id""" + LEARNER + """

			GROUP BY day""", nativeQuery = true)
	List<ScanDayRow> scanPerDay(@Param("start") Instant start, @Param("end") Instant end);

	// Q-SCANTODAY
	@Query(value = """
			SELECT CAST(COALESCE(SUM(s.status = 'FAILED' AND s.error_code = 'AI_QUEUE_FULL'), 0) AS SIGNED) AS queueFull,
			       COUNT(DISTINCT CASE WHEN s.error_code = 'QUOTA_EXCEEDED' THEN s.user_id END) AS quotaHit
			FROM scan_requests s
			WHERE s.created_at >= :start AND s.created_at < :end AND s.user_id""" + LEARNER, nativeQuery = true)
	ScanRefusals scanRefusals(@Param("start") Instant start, @Param("end") Instant end);

	// Q-ERR
	@Query(value = """
			SELECT s.error_code AS code, COUNT(*) AS count
			FROM scan_requests s
			WHERE s.status = 'FAILED' AND s.error_code <> 'QUOTA_EXCEEDED'
			  AND s.created_at >= :start AND s.created_at < :end AND s.user_id""" + LEARNER + """

			GROUP BY s.error_code
			ORDER BY count DESC""", nativeQuery = true)
	List<CodeCount> scanErrors(@Param("start") Instant start, @Param("end") Instant end);

	// Q-LAT: nearest-rank percentiles of finished_at − started_at (KT-S6, KT-S7); all null without samples.
	@Query(value = """
			SELECT MAX(CASE WHEN x.rn = CEIL(0.50 * x.cnt) THEN x.ms END) AS p50,
			       MAX(CASE WHEN x.rn = CEIL(0.95 * x.cnt) THEN x.ms END) AS p95,
			       MAX(CASE WHEN x.rn = CEIL(0.99 * x.cnt) THEN x.ms END) AS p99
			FROM (
			  SELECT TIMESTAMPDIFF(MICROSECOND, s.started_at, s.finished_at) DIV 1000 AS ms,
			         ROW_NUMBER() OVER (ORDER BY TIMESTAMPDIFF(MICROSECOND, s.started_at, s.finished_at)) AS rn,
			         COUNT(*) OVER () AS cnt
			  FROM scan_requests s
			  WHERE s.status = 'DONE' AND s.started_at IS NOT NULL AND s.finished_at IS NOT NULL
			    AND s.created_at >= :start AND s.created_at < :end AND s.user_id""" + LEARNER + """

			) x""", nativeQuery = true)
	Percentiles scanLatency(@Param("start") Instant start, @Param("end") Instant end);

	// Q-UNMAPPED: 50 rows, the caller re-checks them against today's dictionary (KT-S5).
	@Query(value = """
			SELECT d.label AS label, MAX(d.headword) AS headword,
			       COUNT(DISTINCT d.scan_request_id) AS scans, COUNT(DISTINCT d.user_id) AS learners,
			       DATE_FORMAT(MAX(d.created_at), '%Y-%m-%dT%H:%i:%sZ') AS lastSeen
			FROM scan_detections d
			WHERE d.has_word = FALSE AND d.created_at >= :start AND d.created_at < :end AND d.user_id""" + LEARNER + """

			GROUP BY d.label
			ORDER BY scans DESC, d.label
			LIMIT 50""", nativeQuery = true)
	List<UnmappedRow> unmappedLabels(@Param("start") Instant start, @Param("end") Instant end);

	// Q-COIN
	@Query(value = """
			SELECT DATE_FORMAT(ct.created_at + INTERVAL 7 HOUR, '%Y-%m-%d') AS day,
			       CAST(COALESCE(SUM(CASE WHEN ct.source_type = 'MISSION_REWARD' THEN ct.amount END), 0) AS SIGNED) AS coinIn,
			       CAST(COALESCE(SUM(CASE WHEN ct.source_type = 'SHOP_PURCHASE' THEN -ct.amount END), 0) AS SIGNED) AS coinOut
			FROM coin_transactions ct
			WHERE ct.created_at >= :start AND ct.created_at < :end AND ct.user_id""" + LEARNER + """

			GROUP BY day""", nativeQuery = true)
	List<CoinDayRow> coinPerDay(@Param("start") Instant start, @Param("end") Instant end);

	// Q-BAL
	@Query(value = """
			SELECT CAST(COALESCE(ROUND(AVG(COALESCE(u.coin, 0))), 0) AS SIGNED)
			FROM users u WHERE u.activated = TRUE AND u.id""" + LEARNER, nativeQuery = true)
	Long avgBalance();

	// Q-MIS
	@Query(value = """
			SELECT m.code AS code, COUNT(*) AS assigned,
			       CAST(SUM(m.status IN ('COMPLETED', 'CLAIMED')) AS SIGNED) AS completed
			FROM user_daily_missions m
			WHERE m.mission_date BETWEEN :from AND :to AND m.user_id""" + LEARNER + """

			GROUP BY m.code""", nativeQuery = true)
	List<MissionRow> missions(@Param("from") LocalDate from, @Param("to") LocalDate to);

	// Q-SALES
	@Query(value = """
			SELECT si.code AS code, COUNT(*) AS count
			FROM coin_transactions ct JOIN shop_items si ON si.id = CAST(ct.reference_id AS UNSIGNED)
			WHERE ct.source_type = 'SHOP_PURCHASE' AND ct.created_at >= :start AND ct.created_at < :end
			  AND ct.user_id""" + LEARNER + """

			GROUP BY si.code
			ORDER BY count DESC, si.code""", nativeQuery = true)
	List<CodeCount> purchasesPerItem(@Param("start") Instant start, @Param("end") Instant end);

	// ---- Analytics (admin_analytics_backend.md §5) ----

	// A-CARDS: states are FsrsRecord ordinals (NEW 0, LEARNING 1, REVIEW 2, RELEARNING 3, SUSPENDED 4); due is UTC.
	@Query(value = """
			SELECT f.state AS state, COUNT(*) AS total,
			       CAST(SUM(f.state = 2 AND f.stability >= 21) AS SIGNED) AS mastered,
			       CAST(SUM(f.state <> 4 AND f.due <= :nowUtc) AS SIGNED) AS due
			FROM fsrs_records f
			WHERE f.user_id""" + LEARNER + """

			GROUP BY f.state""", nativeQuery = true)
	List<StateCount> cardStates(@Param("nowUtc") LocalDateTime nowUtc);

	// A-NEVER
	@Query(value = """
			SELECT COUNT(*)
			FROM topic_items ti
			JOIN topics t ON t.id = ti.topic_id
			JOIN collections c ON c.id = t.collection_id
			WHERE c.type = 'USER'
			  AND NOT EXISTS (SELECT 1 FROM fsrs_records f WHERE f.user_id = c.owner_id AND f.topic_item_id = ti.id)
			  AND c.owner_id""" + LEARNER, nativeQuery = true)
	Long countOwnWordsNeverReviewed();

	// A-REVIEWS
	@Query(value = """
			SELECT DATE_FORMAT(e.learning_date, '%Y-%m-%d') AS day, COUNT(*) AS count
			FROM learning_events e
			WHERE e.event_type = 'SRS_REVIEW_COMPLETED' AND e.learning_date BETWEEN :from AND :to
			  AND e.user_id""" + LEARNER + """

			GROUP BY e.learning_date""", nativeQuery = true)
	List<DayCount> reviewsPerDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

	// A-NEWWORDS
	@Query(value = """
			SELECT DATE_FORMAT(ti.created_at + INTERVAL 7 HOUR, '%Y-%m-%d') AS day, COUNT(*) AS count
			FROM topic_items ti
			JOIN topics t ON t.id = ti.topic_id
			JOIN collections c ON c.id = t.collection_id
			WHERE c.type = 'USER' AND ti.created_at >= :start AND ti.created_at < :end AND c.owner_id""" + LEARNER + """

			GROUP BY day""", nativeQuery = true)
	List<DayCount> newWordsPerDay(@Param("start") Instant start, @Param("end") Instant end);

	// A-QUIZDAY
	@Query(value = """
			SELECT DATE_FORMAT(q.completed_at + INTERVAL 7 HOUR, '%Y-%m-%d') AS day,
			       CAST(SUM(COALESCE(q.correct_count, 0) + COALESCE(q.incorrect_count, 0)) AS SIGNED) AS answers,
			       CAST(SUM(COALESCE(q.correct_count, 0)) AS SIGNED) AS correct
			FROM quizzes q
			WHERE q.status = 'COMPLETED' AND q.completed_at >= :start AND q.completed_at < :end
			  AND q.user_id""" + LEARNER + """

			GROUP BY day""", nativeQuery = true)
	List<QuizDayRow> quizPerDay(@Param("start") Instant start, @Param("end") Instant end);

	// A-TOPICS: SYSTEM topics only; a deeper level folds into its direct parent.
	@Query(value = """
			SELECT c.id AS collectionId, COALESCE(p.id, t.id) AS rootId, COALESCE(p.name, t.name) AS rootName,
			       t.id AS topicId, t.name AS name,
			       COUNT(*) AS answers, CAST(SUM(qq.outcome = 'CORRECT') AS SIGNED) AS correct,
			       COUNT(DISTINCT q.user_id) AS learners
			FROM quiz_questions qq
			JOIN quizzes q ON q.id = qq.quiz_id
			JOIN topic_items ti ON ti.id = qq.topic_item_id
			JOIN topics t ON t.id = ti.topic_id
			LEFT JOIN topics p ON p.id = t.parent_id
			JOIN collections c ON c.id = t.collection_id
			WHERE q.status = 'COMPLETED' AND q.completed_at >= :start AND q.completed_at < :end
			  AND qq.outcome IN ('CORRECT', 'INCORRECT') AND c.type = 'SYSTEM' AND q.user_id""" + LEARNER + """

			GROUP BY c.id, p.id, p.name, t.id, t.name""", nativeQuery = true)
	List<TopicRow> topicAccuracy(@Param("start") Instant start, @Param("end") Instant end);

	// A-HARD: 30 rows; the caller drops items without a target word and keeps 20.
	@Query(value = """
			SELECT qq.topic_item_id AS itemId, c.id AS collectionId, COALESCE(t.parent_id, t.id) AS rootId,
			       t.id AS topicId, t.name AS topicName,
			       COUNT(*) AS answers, CAST(SUM(qq.outcome = 'INCORRECT') AS SIGNED) AS wrong,
			       COUNT(DISTINCT q.user_id) AS learners
			FROM quiz_questions qq
			JOIN quizzes q ON q.id = qq.quiz_id
			JOIN topic_items ti ON ti.id = qq.topic_item_id
			JOIN topics t ON t.id = ti.topic_id
			JOIN collections c ON c.id = t.collection_id
			WHERE q.status = 'COMPLETED' AND q.completed_at >= :start AND q.completed_at < :end
			  AND qq.outcome IN ('CORRECT', 'INCORRECT') AND c.type = 'SYSTEM' AND q.user_id""" + LEARNER + """

			GROUP BY qq.topic_item_id, c.id, t.parent_id, t.id, t.name
			HAVING wrong > 0
			ORDER BY wrong DESC, answers DESC, itemId
			LIMIT 30""", nativeQuery = true)
	List<HardRow> hardItems(@Param("start") Instant start, @Param("end") Instant end);

	// A-LAPSES: never called with an empty list (IN () is a syntax error).
	@Query(value = """
			SELECT f.topic_item_id AS itemId, CAST(SUM(f.lapses) AS SIGNED) AS lapses
			FROM fsrs_records f
			WHERE f.topic_item_id IN (:itemIds) AND f.user_id""" + LEARNER + """

			GROUP BY f.topic_item_id""", nativeQuery = true)
	List<ItemLapses> lapses(@Param("itemIds") Collection<Long> itemIds);

	// A-COHORT-SIZE: cohort 0 = the oldest of the 8 sign-up weeks (Monday, Vietnam time; KT-A4).
	@Query(value = """
			SELECT CAST(FLOOR(DATEDIFF(DATE(u.created_at + INTERVAL 7 HOUR), :firstMonday) / 7) AS SIGNED) AS cohort,
			       COUNT(*) AS count
			FROM users u
			WHERE u.activated = TRUE AND u.created_at >= :firstMondayStart AND u.id""" + LEARNER + """

			GROUP BY cohort""", nativeQuery = true)
	List<CohortSize> cohortSizes(@Param("firstMonday") LocalDate firstMonday,
			@Param("firstMondayStart") Instant firstMondayStart);

	// A-COHORT-ACTIVE
	@Query(value = """
			SELECT CAST(FLOOR(DATEDIFF(DATE(u.created_at + INTERVAL 7 HOUR), :firstMonday) / 7) AS SIGNED) AS cohort,
			       CAST(FLOOR(DATEDIFF(e.learning_date, :firstMonday) / 7) AS SIGNED) AS week,
			       COUNT(DISTINCT u.id) AS learners
			FROM users u
			JOIN learning_events e ON e.user_id = u.id
			WHERE u.activated = TRUE AND u.created_at >= :firstMondayStart AND e.learning_date >= :firstMonday
			  AND u.id""" + LEARNER + """

			GROUP BY cohort, week""", nativeQuery = true)
	List<CohortActive> cohortActivity(@Param("firstMonday") LocalDate firstMonday,
			@Param("firstMondayStart") Instant firstMondayStart);

	// A-STREAK: 30 and above fold into 30.
	@Query(value = """
			SELECT CAST(LEAST(u.longest_streak_days, 30) AS SIGNED) AS days, COUNT(*) AS count
			FROM users u
			WHERE u.activated = TRUE AND u.longest_streak_days >= 1 AND u.id""" + LEARNER + """

			GROUP BY days""", nativeQuery = true)
	List<StreakRow> longestStreaks();

	// A-SEGMENTS (§3.2.1)
	@Query(value = """
			SELECT CAST(COALESCE(SUM(u.activated = TRUE AND u.streak_days >= 3
			         AND u.last_studied_at >= :yesterdayStart AND u.last_studied_at < :todayStart), 0) AS SIGNED) AS streakAtRisk,
			       CAST(COALESCE(SUM(u.activated = TRUE
			         AND u.last_studied_at >= :now30 AND u.last_studied_at < :now7), 0) AS SIGNED) AS idle,
			       CAST(COALESCE(SUM(u.activated = TRUE AND u.last_studied_at IS NULL), 0) AS SIGNED) AS neverStudied,
			       CAST(COALESCE(SUM(u.activated = FALSE), 0) AS SIGNED) AS pendingEmail
			FROM users u
			WHERE u.id""" + LEARNER, nativeQuery = true)
	SegmentRow segments(@Param("yesterdayStart") Instant yesterdayStart, @Param("todayStart") Instant todayStart,
			@Param("now30") Instant now30, @Param("now7") Instant now7);

	// A-INMISSION: the mission id is the 2nd part of MISSION_REWARD:{id}[:ITEM] (KT-A5).
	@Query(value = """
			SELECT m.code AS code, CAST(SUM(ct.amount) AS SIGNED) AS coin
			FROM coin_transactions ct
			JOIN user_daily_missions m
			  ON m.id = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(ct.event_key, ':', 2), ':', -1) AS UNSIGNED)
			WHERE ct.source_type = 'MISSION_REWARD' AND ct.created_at >= :start AND ct.created_at < :end
			  AND ct.user_id""" + LEARNER + """

			GROUP BY m.code
			HAVING coin > 0
			ORDER BY coin DESC, m.code""", nativeQuery = true)
	List<CodeCoin> coinInByMission(@Param("start") Instant start, @Param("end") Instant end);

	// A-ADJ
	@Query(value = """
			SELECT CAST(COALESCE(SUM(ct.amount), 0) AS SIGNED)
			FROM coin_transactions ct
			WHERE ct.source_type = 'ADMIN_ADJUSTMENT' AND ct.created_at >= :start AND ct.created_at < :end
			  AND ct.user_id""" + LEARNER, nativeQuery = true)
	Long adjustments(@Param("start") Instant start, @Param("end") Instant end);

	// A-SALES: this window [start, end) and the previous one [prevStart, start) in one scan.
	@Query(value = """
			SELECT si.code AS code,
			       CAST(SUM(ct.created_at >= :start) AS SIGNED) AS purchases,
			       CAST(COALESCE(SUM(CASE WHEN ct.created_at >= :start THEN -ct.amount END), 0) AS SIGNED) AS coin,
			       CAST(SUM(ct.created_at < :start) AS SIGNED) AS prevPurchases
			FROM coin_transactions ct
			JOIN shop_items si ON si.id = CAST(ct.reference_id AS UNSIGNED)
			WHERE ct.source_type = 'SHOP_PURCHASE' AND ct.created_at >= :prevStart AND ct.created_at < :end
			  AND ct.user_id""" + LEARNER + """

			GROUP BY si.code""", nativeQuery = true)
	List<SalesRow> sales(@Param("prevStart") Instant prevStart, @Param("start") Instant start,
			@Param("end") Instant end);

	// A-CIRC
	@Query(value = """
			SELECT CAST(COALESCE(SUM(COALESCE(u.coin, 0)), 0) AS SIGNED)
			FROM users u WHERE u.activated = TRUE AND u.id""" + LEARNER, nativeQuery = true)
	Long circulation();

	// A-MODEL: system information, so every account counts.
	@Query(value = """
			SELECT s.model_version FROM scan_requests s
			WHERE s.status = 'DONE' AND s.model_version IS NOT NULL AND s.created_at >= :start AND s.created_at < :end
			ORDER BY s.finished_at DESC
			LIMIT 1""", nativeQuery = true)
	String latestModel(@Param("start") Instant start, @Param("end") Instant end);

	// A-FUNNEL
	@Query(value = """
			SELECT COUNT(*) AS submitted,
			       CAST(COALESCE(SUM(s.status = 'DONE'), 0) AS SIGNED) AS done,
			       CAST(COALESCE(SUM(s.status = 'DONE' AND s.detection_count > 0), 0) AS SIGNED) AS withLabels,
			       CAST(COALESCE(SUM(s.status = 'DONE' AND EXISTS (SELECT 1 FROM scan_detections d
			            WHERE d.scan_request_id = s.id AND d.has_word = TRUE)), 0) AS SIGNED) AS withWord,
			       CAST(COALESCE(SUM(s.status = 'DONE' AND EXISTS (SELECT 1 FROM topic_items ti
			            WHERE ti.source_scan_id = s.id)), 0) AS SIGNED) AS saved
			FROM scan_requests s
			WHERE COALESCE(s.error_code, '') <> 'QUOTA_EXCEEDED'
			  AND s.created_at >= :start AND s.created_at < :end AND s.user_id""" + LEARNER, nativeQuery = true)
	FunnelRow funnel(@Param("start") Instant start, @Param("end") Instant end);

	// A-SAVED-LIVE: 0 until a saved word carries its source scan (G3).
	@Query(value = "SELECT EXISTS (SELECT 1 FROM topic_items ti WHERE ti.source_scan_id IS NOT NULL)",
			nativeQuery = true)
	Long savedSourceLive();

	// A-RELIABILITY
	@Query(value = """
			SELECT d.reliability AS code, COUNT(*) AS count
			FROM scan_detections d
			WHERE d.created_at >= :start AND d.created_at < :end AND d.user_id""" + LEARNER + """

			GROUP BY d.reliability""", nativeQuery = true)
	List<CodeCount> labelsByReliability(@Param("start") Instant start, @Param("end") Instant end);

	// A-QUOTA
	@Query(value = """
			SELECT COUNT(DISTINCT s.user_id) FROM scan_requests s
			WHERE s.error_code = 'QUOTA_EXCEEDED' AND s.created_at >= :start AND s.created_at < :end
			  AND s.user_id""" + LEARNER, nativeQuery = true)
	Long quotaHitLearners(@Param("start") Instant start, @Param("end") Instant end);

	interface StateCount {
		Integer getState();

		Long getTotal();

		Long getMastered();

		Long getDue();
	}

	interface QuizDayRow {
		String getDay();

		Long getAnswers();

		Long getCorrect();
	}

	interface TopicRow {
		Long getCollectionId();

		Long getRootId();

		String getRootName();

		Long getTopicId();

		String getName();

		Long getAnswers();

		Long getCorrect();

		Long getLearners();
	}

	interface HardRow {
		Long getItemId();

		Long getCollectionId();

		Long getRootId();

		Long getTopicId();

		String getTopicName();

		Long getAnswers();

		Long getWrong();

		Long getLearners();
	}

	interface ItemLapses {
		Long getItemId();

		Long getLapses();
	}

	interface CohortSize {
		Integer getCohort();

		Long getCount();
	}

	interface CohortActive {
		Integer getCohort();

		Integer getWeek();

		Long getLearners();
	}

	interface StreakRow {
		Integer getDays();

		Long getCount();
	}

	interface SegmentRow {
		Long getStreakAtRisk();

		Long getIdle();

		Long getNeverStudied();

		Long getPendingEmail();
	}

	interface CodeCoin {
		String getCode();

		Long getCoin();
	}

	interface SalesRow {
		String getCode();

		Long getPurchases();

		Long getCoin();

		Long getPrevPurchases();
	}

	interface FunnelRow {
		Long getSubmitted();

		Long getDone();

		Long getWithLabels();

		Long getWithWord();

		Long getSaved();
	}

	interface DayCount {
		String getDay();

		Long getCount();
	}

	interface CodeCount {
		String getCode();

		Long getCount();
	}

	interface TodayRow {
		Long getNewWords();

		Long getReviews();

		Long getQuizzes();

		Long getMissionsCompleted();

		Long getFullSets();
	}

	interface ScanDayRow {
		String getDay();

		Long getDone();

		Long getFailed();
	}

	interface ScanRefusals {
		Long getQueueFull();

		Long getQuotaHit();
	}

	interface Percentiles {
		Long getP50();

		Long getP95();

		Long getP99();
	}

	interface UnmappedRow {
		String getLabel();

		String getHeadword();

		Long getScans();

		Long getLearners();

		String getLastSeen();
	}

	interface CoinDayRow {
		String getDay();

		Long getCoinIn();

		Long getCoinOut();
	}

	interface MissionRow {
		String getCode();

		Long getAssigned();

		Long getCompleted();
	}
}
