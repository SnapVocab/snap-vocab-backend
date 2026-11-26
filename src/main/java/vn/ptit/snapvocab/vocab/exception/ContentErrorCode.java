package vn.ptit.snapvocab.vocab.exception;

import org.springframework.http.HttpStatus;

/** Errors of the admin content APIs (dictionary, topics, topic items, card templates); the name is the API code. */
public enum ContentErrorCode {
	/** Someone saved the object after the editor read it; {@code details.currentVersion}. */
	VERSION_CONFLICT(HttpStatus.CONFLICT),
	/** A soft-deleted word is restored before it is edited again. */
	WORD_DELETED(HttpStatus.CONFLICT),
	WORD_NOT_DELETED(HttpStatus.CONFLICT),
	/** Learners studied it (SRS card, daily card, quiz or story); {@code details.learners}. Nothing was deleted. */
	TOPIC_HAS_LEARNING_DATA(HttpStatus.CONFLICT),
	TOPIC_ALREADY_HIDDEN(HttpStatus.CONFLICT),
	TOPIC_NOT_HIDDEN(HttpStatus.CONFLICT),
	/** A subtopic comes back with its hidden parent (restore the parent). */
	TOPIC_PARENT_HIDDEN(HttpStatus.CONFLICT),
	/** Subtopics are deleted first; {@code details.children}. */
	TOPIC_HAS_CHILDREN(HttpStatus.CONFLICT),
	/** WI-04: a SYSTEM item some learners studied keeps its history; {@code details.learners}. */
	TOPIC_ITEM_HAS_LEARNING_DATA(HttpStatus.CONFLICT),
	/** Quiz could not read the template; {@code details} = {mode, role, kind: MISSING | AMBIGUOUS}. */
	TEMPLATE_BINDING_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
	/** Body fails a rule Bean Validation cannot express; {@code details.field} names the offending path. */
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

	ContentErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
