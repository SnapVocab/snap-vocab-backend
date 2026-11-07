package vn.ptit.snapvocab.study.exception;

/** This collection's allowance of new introductions or reviews has been used up for the study day. */
public class StudyQuotaExceededException extends RuntimeException {
	public StudyQuotaExceededException() {
		super("DAILY_QUOTA_REACHED");
	}
}
