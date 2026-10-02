package vn.ptit.snapvocab.quiz.exception;

import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;

public class QuizException extends RuntimeException {

	private final QuizErrorCode code;
	private final transient Object details;

	public QuizException(QuizErrorCode code, String message) {
		this(code, message, null);
	}

	public QuizException(QuizErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public QuizErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}

