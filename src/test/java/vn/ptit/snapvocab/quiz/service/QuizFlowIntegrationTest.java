package vn.ptit.snapvocab.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingAttempt;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.quiz.domain.dto.CheckQuestionRequest;
import vn.ptit.snapvocab.quiz.domain.dto.CreateQuizRequest;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptDTO;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptRequest;
import vn.ptit.snapvocab.quiz.domain.dto.QuestionCheckDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizCompletedEvent;
import vn.ptit.snapvocab.quiz.domain.dto.QuizPlayDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizResultDTO;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;
import vn.ptit.snapvocab.quiz.repository.QuizMatchingAttemptRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.quiz.support.QuizIntegrationTestBase;
import vn.ptit.snapvocab.quiz.exception.QuizException;

@RecordApplicationEvents
class QuizFlowIntegrationTest extends QuizIntegrationTestBase {

	@Autowired
	private QuizService quizService;
	@Autowired
	private QuizRepository quizRepository;
	@Autowired
	private QuizMatchingAttemptRepository attemptRepository;
	@Autowired
	private ApplicationEvents events;

	private User user;

	@BeforeEach
	void signIn() {
		user = newUser();
		authenticate(user);
	}

	private static QuizErrorCode codeOf(Runnable action) {
		try {
			action.run();
		} catch (QuizException e) {
			return e.getCode();
		}
		return null;
	}

	private QuizPlayDTO create(Topic topic, QuizMode mode, QuizDirection direction, int count, String key) {
		return quizService.create(new CreateQuizRequest(topic.getId(), mode, direction, count), key);
	}

	private List<QuizQuestion> answerKey(Long quizId) {
		return tx().execute(s -> {
			Quiz quiz = quizRepository.findById(quizId).orElseThrow();
			quiz.getQuestions().forEach(q -> q.getOptions().size());
			return quiz.getQuestions();
		});
	}

	private String wrongOptionKey(QuizQuestion q) {
		return q.getOptions().stream().map(o -> o.getOptionKey())
				.filter(k -> !k.equals(q.getCorrectOptionKey())).findFirst().orElseThrow();
	}

	// --- Create & idempotency
	// -------------------------------------------------------------------

	@Test
	void createReturnsPlayableSessionWithoutAnyAnswerKey() throws Exception {
		Topic topic = topicWithVocabulary("STANDARD", 8);

		QuizPlayDTO play = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "create-1");

		assertThat(play.status()).isEqualTo(QuizStatus.IN_PROGRESS);
		assertThat(play.questions()).hasSize(5).allSatisfy(q -> {
			assertThat(q.prompt()).startsWith("word");
			assertThat(q.options()).hasSize(4);
			assertThat(q.outcome()).isEqualTo(QuizOutcome.UNCOMMITTED);
		});
		String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(play);
		assertThat(json).doesNotContainIgnoringCase("correct");
		for (QuizQuestion q : answerKey(play.quizId())) {
			assertThat(q.getCorrectAnswer()).startsWith("nghĩa");
		}
	}

	@Test
	void sameCreateKeyReturnsTheOriginalQuiz() {
		Topic topic = topicWithVocabulary("STANDARD", 8);

		QuizPlayDTO first = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "same-key");
		QuizPlayDTO second = create(topic, QuizMode.MATCHING, QuizDirection.VI_EN, 10, "same-key");

		assertThat(second.quizId()).isEqualTo(first.quizId());
		assertThat(second.mode()).isEqualTo(QuizMode.MCQ);
		assertThat(quizRepository.count()).isEqualTo(1);
	}

	@Test
	void concurrentCreatesWithSameKeyYieldOneQuiz() throws Exception {
		Topic topic = topicWithVocabulary("STANDARD", 8);
		List<Long> ids = runConcurrently(2, () -> create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "race").quizId());

		assertThat(ids).hasSize(2).containsOnly(ids.get(0));
		assertThat(quizRepository.count()).isEqualTo(1);
	}

	@Test
	void databaseRejectsDuplicateCreateKeyPerUser() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "db-unique").quizId();

		assertThatThrownBy(() -> jdbc().update(
				"INSERT INTO quizzes (user_id, topic_id, topic_name, mode, status, requested_count, "
						+ "create_idempotency_key, created_at, updated_at, version) "
						+ "SELECT user_id, topic_id, topic_name, mode, status, requested_count, create_idempotency_key, "
						+ "created_at, updated_at, 0 FROM quizzes WHERE id = ?",
				quizId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void topicWithoutNativeTranslationsCannotBuildMcq() {
		Topic topic = newTopic("STANDARD");
		for (int i = 1; i <= 6; i++) {
			addItem(topic, "word" + i, null, "word" + i + " here.");
		}
		assertThat(codeOf(() -> create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "k")))
				.isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
		assertThat(quizRepository.count()).isZero();
	}

	@Test
	void eligibilityDoesNotDependOnTheFlashcardMode() {
		Topic topic = topicWithVocabulary("REVERSE", 6);
		assertThat(create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "a").matchingRounds()).hasSize(1);
		assertThat(create(topic, QuizMode.FILL_BLANK, null, 5, "b").questions()).hasSize(5);
	}

	@ParameterizedTest
	@ValueSource(ints = { 5, 10, 20 })
	void everyAllowedCountBuildsAQuizOfExactlyThatSize(int count) {
		Topic topic = topicWithVocabulary("STANDARD", 20);
		assertThat(create(topic, QuizMode.MCQ, QuizDirection.EN_VI, count, "mcq").questions()).hasSize(count);
		assertThat(create(topic, QuizMode.FILL_BLANK, null, count, "fill").questions()).hasSize(count);
		QuizPlayDTO matching = create(topic, QuizMode.MATCHING, QuizDirection.VI_EN, count, "match");
		assertThat(matching.matchingRounds()).hasSize(count / 5)
				.allSatisfy(round -> {
					assertThat(round.totalPairs()).isEqualTo(5);
					assertThat(round.leftTiles()).hasSize(5);
					assertThat(round.rightTiles()).hasSize(5);
				});
	}

	@Test
	void insufficientEligiblePoolFailsAndCreatesNothing() {
		Topic topic = topicWithVocabulary("STANDARD", 9);
		addItem(topic, "orphan", null, "orphan here.");

		QuizException e = null;
		try {
			create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 10, "short");
		} catch (QuizException ex) {
			e = ex;
		}

		assertThat(e).isNotNull();
		assertThat(e.getCode()).isEqualTo(QuizErrorCode.QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS);
		assertThat(e.getDetails()).isEqualTo(Map.of("requestedCount", 10, "eligibleCount", 9));
		assertThat(quizRepository.count()).isZero();
		assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM quiz_questions", Integer.class)).isZero();
	}

	@Test
	void invalidRequestsAreRejectedBeforeTouchingData() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		assertThat(codeOf(() -> create(topic, QuizMode.MCQ, null, 5, "k"))).isEqualTo(QuizErrorCode.INVALID_REQUEST);
		for (int count : new int[] { 0, 4, 6, 9, 11, 15, 19, 21, 50, -5 }) {
			assertThat(codeOf(() -> create(topic, QuizMode.MCQ, QuizDirection.EN_VI, count, "k" + count)))
					.as("requestedCount %s", count)
					.isEqualTo(QuizErrorCode.INVALID_REQUEST);
		}
		assertThat(quizRepository.count()).isZero();
		assertThat(codeOf(() -> create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, " ")))
				.isEqualTo(QuizErrorCode.IDEMPOTENCY_KEY_REQUIRED);
	}

	// --- MCQ
	// ------------------------------------------------------------------------------------

	@Test
	void mcqIsGradedPerQuestionByOptionIdentity() {
		Topic topic = topicWithVocabulary("STANDARD", 8);
		QuizPlayDTO play = create(topic, QuizMode.MCQ, QuizDirection.VI_EN, 5, "mcq");
		List<QuizQuestion> key = answerKey(play.quizId());
		QuizQuestion first = key.get(0);
		QuizQuestion second = key.get(1);

		QuestionCheckDTO right = quizService.checkQuestion(first.getId(),
				new CheckQuestionRequest(first.getCorrectOptionKey(), null), "c1");
		QuestionCheckDTO wrong = quizService.checkQuestion(second.getId(),
				new CheckQuestionRequest(wrongOptionKey(second), null), "c2");

		assertThat(right.outcome()).isEqualTo(QuizOutcome.CORRECT);
		assertThat(right.correctAnswer()).startsWith("word");
		assertThat(wrong.outcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(wrong.correctOptionKey()).isEqualTo(second.getCorrectOptionKey());

		QuizQuestion third = key.get(2);
		assertThat(codeOf(() -> quizService.checkQuestion(third.getId(),
				new CheckQuestionRequest(third.getCorrectAnswer(), null), "c3")))
				.isEqualTo(QuizErrorCode.INVALID_OPTION);
		assertThat(codeOf(() -> quizService.checkQuestion(third.getId(),
				new CheckQuestionRequest(first.getCorrectOptionKey(), null), "c3")))
				.isEqualTo(QuizErrorCode.INVALID_OPTION);
	}

	@Test
	void questionCheckReplaysSameKeyAndRejectsNewKey() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		QuizQuestion q = answerKey(create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "k").quizId()).get(0);

		QuestionCheckDTO original = quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(wrongOptionKey(q), null), "check-key");
		QuestionCheckDTO replay = quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(q.getCorrectOptionKey(), null), "check-key");

		assertThat(replay).isEqualTo(original);
		assertThat(replay.outcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(codeOf(() -> quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(q.getCorrectOptionKey(), null), "other-key")))
				.isEqualTo(QuizErrorCode.QUESTION_ALREADY_CHECKED);
	}

	@Test
	void concurrentChecksOfOneQuestionGradeItOnce() throws Exception {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		QuizQuestion q = answerKey(create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "k").quizId()).get(0);

		List<String> results = runConcurrently(4, () -> {
			try {
				return quizService.checkQuestion(q.getId(), new CheckQuestionRequest(q.getCorrectOptionKey(), null),
						"key-" + Thread.currentThread().getId()).outcome().name();
			} catch (QuizException e) {
				return e.getCode().name();
			}
		});

		assertThat(results).containsOnlyOnce("CORRECT");
		assertThat(results).filteredOn(r -> !r.equals("CORRECT"))
				.containsOnly(QuizErrorCode.QUESTION_ALREADY_CHECKED.name());
	}

	// --- FILL_BLANK
	// -----------------------------------------------------------------------------

	@Test
	void fillBlankMasksTargetAndGradesNormalizedText() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		QuizPlayDTO play = create(topic, QuizMode.FILL_BLANK, QuizDirection.EN_VI, 5, "fill");
		List<QuizQuestion> key = answerKey(play.quizId());

		assertThat(play.questions()).allSatisfy(q -> {
			assertThat(q.prompt()).contains("_____").doesNotContainPattern("word\\d");
			assertThat(q.options()).isEmpty();
		});
		QuestionCheckDTO right = quizService.checkQuestion(key.get(0).getId(),
				new CheckQuestionRequest(null, "  " + key.get(0).getCorrectAnswer().toUpperCase() + " "), "f1");
		QuestionCheckDTO wrong = quizService.checkQuestion(key.get(1).getId(),
				new CheckQuestionRequest(null, "nghĩa"), "f2");

		assertThat(right.outcome()).isEqualTo(QuizOutcome.CORRECT);
		assertThat(wrong.outcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(wrong.correctAnswer()).isEqualTo(key.get(1).getCorrectAnswer());
	}

	// --- MATCHING
	// -------------------------------------------------------------------------------

	@Test
	void matchingSeparatesGradingOutcomeFromResolution() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		QuizPlayDTO play = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "m");
		List<QuizQuestion> key = answerKey(play.quizId());
		QuizQuestion a = key.get(0);
		QuizQuestion b = key.get(1);
		Long quizId = play.quizId();

		MatchingAttemptDTO wrong = quizService.attemptPair(quizId, 1,
				new MatchingAttemptRequest(a.getLeftKey(), b.getCorrectRightKey()), "a1");
		assertThat(wrong.correct()).isFalse();
		assertThat(wrong.resolved()).isFalse();
		assertThat(wrong.outcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(wrong.round().matchedCount()).isZero();

		MatchingAttemptDTO right = quizService.attemptPair(quizId, 1,
				new MatchingAttemptRequest(a.getLeftKey(), a.getCorrectRightKey()), "a2");
		assertThat(right.correct()).isTrue();
		assertThat(right.resolved()).isTrue();
		assertThat(right.attemptNo()).isEqualTo(2);
		assertThat(right.outcome()).isEqualTo(QuizOutcome.INCORRECT); // first attempt decides the score

		QuizQuestion stored = answerKey(quizId).get(0);
		assertThat(stored.isResolved()).isTrue();
		assertThat(stored.getOutcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(stored.getAttemptCount()).isEqualTo(2);
		assertThat(stored.getResolvedAt()).isNotNull();

		assertThat(codeOf(() -> quizService.attemptPair(quizId, 1,
				new MatchingAttemptRequest(b.getLeftKey(), a.getCorrectRightKey()), "a3")))
				.isEqualTo(QuizErrorCode.MATCHING_TILE_ALREADY_MATCHED);
		assertThat(codeOf(() -> quizService.attemptPair(quizId, 1,
				new MatchingAttemptRequest(a.getLeftKey(), b.getCorrectRightKey()), "a4")))
				.isEqualTo(QuizErrorCode.MATCHING_LEFT_ALREADY_RESOLVED);
		assertThat(codeOf(() -> quizService.attemptPair(quizId, 2,
				new MatchingAttemptRequest(b.getLeftKey(), b.getCorrectRightKey()), "a5")))
				.isEqualTo(QuizErrorCode.INVALID_MATCHING_PAIR);

		// Unresolved pairs block completion; then all-resolved completes even with an
		// INCORRECT outcome.
		assertThat(codeOf(() -> quizService.complete(quizId))).isEqualTo(QuizErrorCode.QUIZ_HAS_UNRESOLVED_UNITS);
		for (QuizQuestion q : key.subList(1, key.size())) {
			quizService.attemptPair(quizId, q.getRoundNo(), new MatchingAttemptRequest(q.getLeftKey(),
					q.getCorrectRightKey()), "ok-" + q.getId());
		}
		QuizResultDTO result = quizService.complete(quizId);
		assertThat(result.summary().totalUnits()).isEqualTo(5);
		assertThat(result.summary().correctCount()).isEqualTo(4);
		assertThat(result.summary().incorrectCount()).isEqualTo(1);
		assertThat(result.summary().accuracy()).isEqualTo(0.8);
	}

	@Test
	void pairAttemptReplayIsExactAndCountsOnce() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "m").quizId();
		List<QuizQuestion> key = answerKey(quizId);
		MatchingAttemptRequest wrongPair = new MatchingAttemptRequest(key.get(0).getLeftKey(),
				key.get(1).getCorrectRightKey());

		MatchingAttemptDTO original = quizService.attemptPair(quizId, 1, wrongPair, "retry-key");
		MatchingAttemptDTO replay = quizService.attemptPair(quizId, 1, wrongPair, "retry-key");

		assertThat(replay).isEqualTo(original);
		assertThat(answerKey(quizId).get(0).getAttemptCount()).isEqualTo(1);
		assertThat(attemptRepository.count()).isEqualTo(1);
	}

	@Test
	void concurrentPairAttemptsWithSameKeyCountOnce() throws Exception {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "m").quizId();
		QuizQuestion q = answerKey(quizId).get(0);

		List<MatchingAttemptDTO> results = runConcurrently(4, () -> quizService.attemptPair(quizId, 1,
				new MatchingAttemptRequest(q.getLeftKey(), q.getCorrectRightKey()), "same-attempt"));

		assertThat(results).containsOnly(results.get(0));
		assertThat(answerKey(quizId).get(0).getAttemptCount()).isEqualTo(1);
		assertThat(attemptRepository.count()).isEqualTo(1);
	}

	@Test
	void databaseRejectsDuplicateAttemptKeyPerQuiz() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "m").quizId();
		QuizQuestion q = answerKey(quizId).get(0);
		quizService.attemptPair(quizId, 1, new MatchingAttemptRequest(q.getLeftKey(), q.getCorrectRightKey()), "dup");

		assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
			QuizMatchingAttempt existing = attemptRepository.findByQuizIdAndIdempotencyKey(quizId, "dup").orElseThrow();
			attemptRepository.saveAndFlush(QuizMatchingAttempt.builder().quiz(existing.getQuiz())
					.question(existing.getQuestion()).roundNo(1).leftKey("x").rightKey("y").correct(false)
					.attemptNo(9).roundMatchedCount(0).roundTotalPairs(5).idempotencyKey("dup").build());
		})).isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Complete / cancel state machine
	// --------------------------------------------------------

	@Test
	void completeFinalizesOnceAndRepeatsWithoutSideEffects() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "c").quizId();
		for (QuizQuestion q : answerKey(quizId)) {
			quizService.checkQuestion(q.getId(), new CheckQuestionRequest(q.getCorrectOptionKey(), null),
					"k" + q.getId());
		}

		QuizResultDTO first = quizService.complete(quizId);
		QuizResultDTO again = quizService.complete(quizId);

		assertThat(first.status()).isEqualTo(QuizStatus.COMPLETED);
		assertThat(first.summary().correctCount()).isEqualTo(5);
		assertThat(again).isEqualTo(first);
		assertThat(first.xp()).isEqualTo(new QuizResultDTO.Xp(5 * QuizService.XP_PER_CORRECT, 0,
				5 * QuizService.XP_PER_CORRECT));
		assertThat(userRepository.findById(user.getId()).orElseThrow().getExp())
				.isEqualTo(user.getExp() + 5 * QuizService.XP_PER_CORRECT);
		assertThat(events.stream(QuizCompletedEvent.class)).hasSize(1);
		assertThat(quizService.getResult(quizId)).isEqualTo(first);
		assertThat(codeOf(() -> quizService.cancel(quizId))).isEqualTo(QuizErrorCode.QUIZ_NOT_IN_PROGRESS);
		assertThat(codeOf(() -> quizService.checkQuestion(answerKey(quizId).get(0).getId(),
				new CheckQuestionRequest("x", null), "new"))).isEqualTo(QuizErrorCode.QUESTION_ALREADY_CHECKED);
	}

	@Test
	void cancelIsIdempotentAndBlocksCompletion() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "c").quizId();

		assertThat(codeOf(() -> quizService.getResult(quizId))).isEqualTo(QuizErrorCode.QUIZ_NOT_FINISHED);
		assertThat(quizService.cancel(quizId).status()).isEqualTo(QuizStatus.CANCELLED);
		assertThat(quizService.cancel(quizId).status()).isEqualTo(QuizStatus.CANCELLED);
		assertThat(codeOf(() -> quizService.complete(quizId))).isEqualTo(QuizErrorCode.QUIZ_NOT_IN_PROGRESS);
		QuizQuestion q = answerKey(quizId).get(0);
		assertThat(codeOf(() -> quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(q.getCorrectOptionKey(), null), "late")))
				.isEqualTo(QuizErrorCode.QUIZ_NOT_IN_PROGRESS);
		assertThat(quizService.getResult(quizId).summary()).isNull();
		assertThat(events.stream(QuizCompletedEvent.class)).isEmpty();
	}

	@Test
	void otherUsersCannotSeeOrMutateTheQuiz() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "c").quizId();
		QuizQuestion q = answerKey(quizId).get(0);

		authenticate(newUser());
		assertThat(codeOf(() -> quizService.getQuiz(quizId))).isEqualTo(QuizErrorCode.QUIZ_NOT_FOUND);
		assertThat(codeOf(() -> quizService.complete(quizId))).isEqualTo(QuizErrorCode.QUIZ_NOT_FOUND);
		assertThat(codeOf(() -> quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(q.getCorrectOptionKey(), null), "x"))).isEqualTo(QuizErrorCode.QUIZ_NOT_FOUND);
		assertThat(quizService.getHistory(0, 20, null, null).getData()).isEmpty();
	}

	@Test
	void historyIsPagedNewestFirst() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long older = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "h1").quizId();
		Long newer = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "h2").quizId();

		var page = quizService.getHistory(0, 1, null, null);

		assertThat(page.getData()).extracting(h -> h.quizId()).containsExactly(newer);
		assertThat(page.getMeta().getTotal()).isEqualTo(2);
		assertThat(page.getMeta().getPages()).isEqualTo(2);
		assertThat(quizService.getHistory(1, 1, null, null).getData()).extracting(h -> h.quizId()).containsExactly(older);
	}

	@Test
	void historyFiltersByModeAndStatusIncludingTotals() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long mcq = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "f1").quizId();
		Long matching = create(topic, QuizMode.MATCHING, QuizDirection.EN_VI, 5, "f2").quizId();
		Long stale = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "f3").quizId();
		backdateCreation(stale, 30);

		var mcqPage = quizService.getHistory(0, 20, QuizMode.MCQ, null);
		assertThat(mcqPage.getData()).extracting(h -> h.quizId()).containsExactly(mcq, stale);
		assertThat(mcqPage.getMeta().getTotal()).isEqualTo(2);
		// Stale sessions are expired before filtering, so IN_PROGRESS means still resumable (D17).
		assertThat(quizService.getHistory(0, 20, QuizMode.MCQ, QuizStatus.IN_PROGRESS).getData())
				.extracting(h -> h.quizId()).containsExactly(mcq);
		assertThat(quizService.getHistory(0, 20, null, QuizStatus.IN_PROGRESS).getData())
				.extracting(h -> h.quizId()).containsExactly(matching, mcq);
	}

	@Test
	void quizRowLockSerializesMutations() throws Exception {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "lock").quizId();
		Long userId = user.getId();
		CountDownLatch locked = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> holder = pool.submit(() -> tx().executeWithoutResult(s -> {
				quizRepository.findForUpdate(quizId, userId).orElseThrow();
				locked.countDown();
				sleep(1500);
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			long start = System.nanoTime();
			quizService.cancel(quizId);
			long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			holder.get(10, TimeUnit.SECONDS);
			assertThat(waitedMs).isGreaterThanOrEqualTo(1000);
		} finally {
			pool.shutdownNow();
		}
	}

	// --- Duration (D14)
	// -------------------------------------------------------------------------

	@Test
	void completedResultCarriesServerCalculatedDuration() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "dur").quizId();
		for (QuizQuestion q : answerKey(quizId)) {
			quizService.checkQuestion(q.getId(), new CheckQuestionRequest(q.getCorrectOptionKey(), null),
					"k" + q.getId());
		}
		assertThat(quizService.complete(quizId).durationSeconds()).isNotNull();

		// createdAt exactly 105s before completedAt, so the value is not a timing
		// approximation.
		jdbc().update("UPDATE quizzes SET created_at = TIMESTAMPADD(SECOND, -105, completed_at) WHERE id = ?", quizId);

		assertThat(quizService.getResult(quizId).durationSeconds()).isEqualTo(105L);
	}

	@Test
	void unfinishedSessionHasNoDuration() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "dur-none").quizId();
		quizService.cancel(quizId);

		assertThat(quizService.getResult(quizId).durationSeconds()).isNull();
	}

	// --- Session expiration (D16)
	// ---------------------------------------------------------------

	@Test
	void sessionStaysResumableInsideTheTtl() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "ttl-ok").quizId();
		backdateCreation(quizId, 23);

		assertThat(quizService.getQuiz(quizId).status()).isEqualTo(QuizStatus.IN_PROGRESS);
		QuizQuestion q = answerKey(quizId).get(0);
		assertThat(quizService.checkQuestion(q.getId(), new CheckQuestionRequest(q.getCorrectOptionKey(), null), "late")
				.correct()).isTrue();
	}

	@Test
	void staleSessionExpiresLazilyAndRejectsEveryInteraction() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long quizId = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "ttl-gone").quizId();
		QuizQuestion q = answerKey(quizId).get(0);
		backdateCreation(quizId, 25);

		// First touch both persists EXPIRED and rejects the resume; no scheduler
		// involved.
		assertThat(codeOf(() -> quizService.getQuiz(quizId))).isEqualTo(QuizErrorCode.QUIZ_SESSION_EXPIRED);
		assertThat(statusOf(quizId)).isEqualTo(QuizStatus.EXPIRED);
		assertThat(codeOf(() -> quizService.checkQuestion(q.getId(),
				new CheckQuestionRequest(q.getCorrectOptionKey(), null), "gone")))
				.isEqualTo(QuizErrorCode.QUIZ_SESSION_EXPIRED);
		assertThat(codeOf(() -> quizService.complete(quizId))).isEqualTo(QuizErrorCode.QUIZ_SESSION_EXPIRED);
		assertThat(codeOf(() -> quizService.cancel(quizId))).isEqualTo(QuizErrorCode.QUIZ_SESSION_EXPIRED);

		QuizResultDTO result = quizService.getResult(quizId);
		assertThat(result.status()).isEqualTo(QuizStatus.EXPIRED);
		assertThat(result.summary()).isNull();
		assertThat(result.durationSeconds()).isNull();
		assertThat(events.stream(QuizCompletedEvent.class)).isEmpty();
	}

	@Test
	void expiredAndCancelledAreNotInterchangeable() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long cancelled = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "ttl-cancel").quizId();
		quizService.cancel(cancelled);
		backdateCreation(cancelled, 25);

		assertThat(quizService.getResult(cancelled).status()).isEqualTo(QuizStatus.CANCELLED);
		assertThat(codeOf(() -> quizService.complete(cancelled))).isEqualTo(QuizErrorCode.QUIZ_NOT_IN_PROGRESS);
		assertThat(statusOf(cancelled)).isEqualTo(QuizStatus.CANCELLED);
	}

	@Test
	void historyReportsStaleSessionsAsExpiredNotResumable() {
		Topic topic = topicWithVocabulary("STANDARD", 6);
		Long stale = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "h-stale").quizId();
		Long fresh = create(topic, QuizMode.MCQ, QuizDirection.EN_VI, 5, "h-fresh").quizId();
		backdateCreation(stale, 30);

		Map<Long, QuizStatus> statuses = quizService.getHistory(0, 20, null, null).getData().stream()
				.collect(java.util.stream.Collectors.toMap(h -> h.quizId(), h -> h.status()));

		assertThat(statuses).containsEntry(stale, QuizStatus.EXPIRED).containsEntry(fresh, QuizStatus.IN_PROGRESS);
		assertThat(statusOf(stale)).isEqualTo(QuizStatus.EXPIRED);
	}

	// --- helpers
	// --------------------------------------------------------------------------------

	/**
	 * Ages a session by rewriting {@code created_at}, which JPA never updates.
	 * Shifting the stored
	 * value keeps this independent of the JDBC/server time zone, unlike anchoring
	 * on {@code NOW()}.
	 */
	private void backdateCreation(Long quizId, int hours) {
		jdbc().update("UPDATE quizzes SET created_at = TIMESTAMPADD(HOUR, ?, created_at) WHERE id = ?", -hours, quizId);
	}

	private QuizStatus statusOf(Long quizId) {
		return QuizStatus
				.valueOf(jdbc().queryForObject("SELECT status FROM quizzes WHERE id = ?", String.class, quizId));
	}

	private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new java.util.ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					authenticate(user);
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new java.util.ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

