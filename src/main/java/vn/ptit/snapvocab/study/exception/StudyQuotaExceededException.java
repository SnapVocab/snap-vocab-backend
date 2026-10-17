package vn.ptit.snapvocab.study.exception;

public class StudyQuotaExceededException extends RuntimeException {
	public StudyQuotaExceededException() {
		super("DAILY_QUOTA_REACHED");
	}
}
