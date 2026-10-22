package vn.ptit.snapvocab.study.exception;

/** The learner already started today's allowance of new words ({@code dailyGoal}); reviews are never blocked. */
public class StudyQuotaExceededException extends RuntimeException {
	public StudyQuotaExceededException() {
		super("DAILY_QUOTA_REACHED");
	}
}
