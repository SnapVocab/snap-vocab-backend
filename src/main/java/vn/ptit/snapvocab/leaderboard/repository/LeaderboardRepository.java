package vn.ptit.snapvocab.leaderboard.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.ptit.snapvocab.xp.domain.ExperienceLog;

/** Read-only leaderboard queries Q1–Q4 (leaderboard_backend.md §5.3). */
public interface LeaderboardRepository extends Repository<ExperienceLog, String> {

	/** Q1: Weekly XP and admin signals per eligible learner (LB-01, LB-03). */
	@Query(nativeQuery = true, value = """
			SELECT x.user_id                                                        AS userId,
			       x.score                                                          AS score,
			       TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', x.reached_at)  AS reachedAtMicros,
			       x.review_xp                                                      AS reviewXp,
			       x.quiz_xp                                                        AS quizXp,
			       x.bonus_xp                                                       AS bonusXp,
			       x.events                                                         AS events,
			       x.active_days                                                    AS activeDays
			FROM (
			    SELECT el.user_id,
			           CAST(SUM(el.amount) AS SIGNED)                                                               AS score,
			           MAX(el.created_at)                                                                           AS reached_at,
			           CAST(SUM(CASE WHEN el.source_type = 'FLASHCARD_REVIEW' THEN el.amount ELSE 0 END) AS SIGNED) AS review_xp,
			           CAST(SUM(CASE WHEN el.source_type = 'QUIZ' THEN el.amount ELSE 0 END) AS SIGNED)             AS quiz_xp,
			           CAST(SUM(el.bonus_amount) AS SIGNED)                                                         AS bonus_xp,
			           COUNT(*)                                                                                     AS events,
			           COUNT(DISTINCT DATE_FORMAT(el.created_at + INTERVAL 7 HOUR, '%Y-%m-%d'))                     AS active_days
			    FROM experience_logs el
			    WHERE el.created_at >= :start AND el.created_at < :end
			      AND el.source_type IN (:sources)
			    GROUP BY el.user_id
			) x
			JOIN users u ON u.id = x.user_id
			WHERE u.activated = TRUE
			  AND u.banned_at IS NULL
			  AND u.id NOT IN (SELECT ua.user_id FROM user_authorities ua WHERE ua.authority_name = 'ROLE_ADMIN')
			""")
	List<ScoreRow> weeklyScores(@Param("start") Instant start, @Param("end") Instant end,
			@Param("sources") Collection<String> sources);

	/**
	 * Q2: largest XP in one clock hour. Buckets are whole hours since {@code :start}, which is 00:00 Vietnam and so a
	 * whole hour in UTC too: the same buckets as {@code DATE_FORMAT(created_at, '%Y-%m-%d %H')}, without building a
	 * string per row (MariaDB 10.11, Phụ lục C data: 717 → 343 ms median).
	 */
	@Query(nativeQuery = true, value = """
			SELECT h.user_id AS userId, MAX(h.xp) AS peakHourXp
			FROM (
			    SELECT el.user_id, CAST(SUM(el.amount) AS SIGNED) AS xp
			    FROM experience_logs el
			    WHERE el.created_at >= :start AND el.created_at < :end
			      AND el.source_type IN (:sources)
			    GROUP BY el.user_id, TIMESTAMPDIFF(HOUR, :start, el.created_at)
			) h
			GROUP BY h.user_id
			""")
	List<PeakRow> peakHourXp(@Param("start") Instant start, @Param("end") Instant end,
			@Param("sources") Collection<String> sources);

	/** Q3: eligible learners, the admin participation denominator. */
	@Query(nativeQuery = true, value = """
			SELECT COUNT(*) FROM users u
			WHERE u.activated = TRUE
			  AND u.banned_at IS NULL
			  AND u.id NOT IN (SELECT ua.user_id FROM user_authorities ua WHERE ua.authority_name = 'ROLE_ADMIN')
			""")
	long countEligibleLearners();

	/** Q4: display profile; read when building a response, never stored in the snapshot. */
	@Query(nativeQuery = true, value = """
			SELECT u.id AS id, u.first_name AS firstName, u.last_name AS lastName, u.email AS email,
			       u.avatar_url AS avatarKey, si.frame_asset_key AS frameKey
			FROM users u
			LEFT JOIN user_items ui ON ui.user_id = u.id AND ui.equipped_slot = 'AVATAR_FRAME'
			LEFT JOIN shop_items si ON si.id = ui.shop_item_id
			WHERE u.id IN (:ids)
			""")
	List<ProfileRow> profiles(@Param("ids") Collection<Long> ids);

	interface ScoreRow {
		Long getUserId();

		Long getScore();

		Long getReachedAtMicros();

		Long getReviewXp();

		Long getQuizXp();

		Long getBonusXp();

		Long getEvents();

		Long getActiveDays();
	}

	interface PeakRow {
		Long getUserId();

		Long getPeakHourXp();
	}

	interface ProfileRow {
		Long getId();

		String getFirstName();

		String getLastName();

		String getEmail();

		String getAvatarKey();

		String getFrameKey();
	}
}
