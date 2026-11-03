package vn.ptit.snapvocab.study.domain.dto;

/**
 * The Learn tab's "Hôm nay" block and Home's shortcut read this one payload (learn_ui.md §4.2).
 *
 * @param dueCount     cards due now across the learner's library; never capped
 * @param newCount     new words the next session will introduce (0 while paused)
 * @param sessionCount cards in the next session = due part + {@code newCount}
 * @param studiedToday distinct cards reviewed today
 * @param newPerDay    the learner's daily new-word limit
 * @param backlog      due exceeds {@link vn.ptit.snapvocab.study.service.StudyService#BACKLOG_SESSIONS} sessions; new words pause
 * @param returning    first session after a break; half-size session
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
