package vn.ptit.snapvocab.study.domain.dto;

/**
 * The Learn tab's "Hôm nay" block and Home's shortcut read this one payload (learn_ui.md §4.2).
 *
 * @param dueCount     due cards available today after each collection's review limit, plus intraday learning
 * @param newCount     new cards available today, summed once per collection
 * @param sessionCount cards in the next session, capped by the internal queue batch size
 * @param studiedToday distinct cards reviewed today
 * @param newPerDay    sum of the active collections' new-card limits; no global daily cap
 * @param backlog      compatibility field, false with independent collection quotas
 * @param returning    compatibility field, false with collection quotas
 * @param dueTomorrow  cards due by the end of tomorrow (study zone)
 * @param totalWords   words in the learner's library; 0 means a new learner
 */
public record StudyTodayDTO(
		long dueCount,
		int newCount,
		int sessionCount,
		int studiedToday,
		int newPerDay,
		boolean backlog,
		boolean returning,
		long dueTomorrow,
		long totalWords
) {
}
