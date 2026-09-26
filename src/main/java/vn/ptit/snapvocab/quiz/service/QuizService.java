package vn.ptit.snapvocab.quiz.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingAttempt;
import vn.ptit.snapvocab.quiz.domain.QuizMatchingTile;
import vn.ptit.snapvocab.quiz.domain.QuizOption;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.quiz.domain.dto.CheckQuestionRequest;
import vn.ptit.snapvocab.quiz.domain.dto.CreateQuizRequest;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptDTO;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptRequest;
import vn.ptit.snapvocab.quiz.domain.dto.QuestionCheckDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizCompletedEvent;
import vn.ptit.snapvocab.quiz.domain.dto.QuizHistoryItemDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizLifecycleDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizPlayDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizResultDTO;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizErrorCode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;
import vn.ptit.snapvocab.quiz.repository.QuizMatchingAttemptRepository;
import vn.ptit.snapvocab.quiz.repository.QuizQuestionRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.service.TopicService;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.quiz.exception.QuizException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
public class QuizService {

	private static final Logger LOG = LoggerFactory.getLogger(QuizService.class);
	private static final int MAX_KEY_LENGTH = 128;
	private static final int MAX_PAGE_SIZE = 100;
	private static final Duration SESSION_TTL = Duration.ofHours(24);

	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizMatchingAttemptRepository attemptRepository;
	private final TopicRepository topicRepository;
	private final TopicService topicService;
	private final UserRepository userRepository;
	private final QuizContentResolver contentResolver;
	private final QuizGenerator generator;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final TransactionTemplate expiryTransaction;

	public QuizService(QuizRepository quizRepository, QuizQuestionRepository questionRepository,
			QuizMatchingAttemptRepository attemptRepository, TopicRepository topicRepository,
			TopicService topicService, UserRepository userRepository, QuizContentResolver contentResolver,
			QuizGenerator generator, ApplicationEventPublisher eventPublisher,
			PlatformTransactionManager transactionManager) {
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.attemptRepository = attemptRepository;
		this.topicRepository = topicRepository;
		this.topicService = topicService;
		this.userRepository = userRepository;
		this.contentResolver = contentResolver;
		this.generator = generator;
		this.eventPublisher = eventPublisher;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.expiryTransaction = new TransactionTemplate(transactionManager);
		this.expiryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public QuizPlayDTO create(CreateQuizRequest request, String idempotencyKey) {
		Long userId = currentUserId();
		String key = requireKey(idempotencyKey);
		validate(request);

		Optional<QuizPlayDTO> existing = findCreated(userId, key);
		if (existing.isPresent()) {
			return existing.get();
		}
		try {
			return transactionTemplate.execute(status -> doCreate(userId, request, key));
		} catch (DataIntegrityViolationException e) {
			return findCreated(userId, key).orElseThrow(() -> e);
		}
	}

	private Optional<QuizPlayDTO> findCreated(Long userId, String key) {
		return transactionTemplate
				.execute(status -> quizRepository.findByUserIdAndCreateIdempotencyKey(userId, key).map(this::toPlay));
	}

	private QuizPlayDTO doCreate(Long userId, CreateQuizRequest request, String key) {
		topicService.validateTopicAccess(request.topicId());
		Topic topic = topicRepository.findById(request.topicId())
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + request.topicId()));

		List<QuizContentResolver.ItemContent> items = contentResolver.resolve(topic, request.mode());
		Quiz quiz = Quiz.builder()
				.user(userRepository.getReferenceById(userId))
				.topicId(topic.getId())
				.topicName(topic.getName())
				.mode(request.mode())
				.direction(request.direction())
				.status(QuizStatus.IN_PROGRESS)
				.requestedCount(request.requestedCount())
				.createIdempotencyKey(key)
				.build();
		switch (request.mode()) {
			case MCQ -> generator.populateMcq(quiz, items, request.direction(), request.requestedCount());
			case MATCHING -> generator.populateMatching(quiz, items, request.direction(), request.requestedCount());
			case FILL_BLANK -> generator.populateFillBlank(quiz, items, request.requestedCount());
		}
		Quiz saved = quizRepository.saveAndFlush(quiz);
		LOG.info("Created quiz {} for user {} (topic {}, {}, {} units)", saved.getId(), userId, topic.getId(),
				request.mode(), saved.getQuestions().size());
		return toPlay(saved);
	}

	private static void validate(CreateQuizRequest request) {
		if (request == null || request.topicId() == null || request.mode() == null
				|| request.requestedCount() == null) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST, "topicId, mode and requestedCount are required");
		}
		if (!QuizGenerator.ALLOWED_COUNTS.contains(request.requestedCount())) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST, "requestedCount must be 5, 10 or 20",
					Map.of("allowedCounts", List.of(5, 10, 20)));
		}
		if (request.mode() != QuizMode.FILL_BLANK && request.direction() == null) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST, "direction is required for " + request.mode());
		}
	}

	@Transactional(readOnly = true)
	public QuizPlayDTO getQuiz(Long quizId) {
		expireIfStale(quizId, currentUserId());
		Quiz quiz = ownedQuiz(quizId);
		if (quiz.getStatus() == QuizStatus.EXPIRED) {
			throw expired(quizId);
		}
		return toPlay(quiz);
	}

	@Transactional(readOnly = true)
	public QuizResultDTO getResult(Long quizId) {
		expireIfStale(quizId, currentUserId());
		Quiz quiz = ownedQuiz(quizId);
		if (quiz.getStatus() == QuizStatus.IN_PROGRESS) {
			throw new QuizException(QuizErrorCode.QUIZ_NOT_FINISHED, "Quiz " + quizId + " is still in progress");
		}
		return toResult(quiz);
	}

	@Transactional
	public PaginationObject<List<QuizHistoryItemDTO>> getHistory(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST, "page >= 0 and 1 <= size <= " + MAX_PAGE_SIZE);
		}
		Long userId = currentUserId();
		quizRepository.expireStale(userId, now().minus(SESSION_TTL));
		Page<Quiz> result = quizRepository.findHistory(userId, PageRequest.of(page, size));
		return PaginationObject.<List<QuizHistoryItemDTO>>builder()
				.data(result.getContent().stream().map(QuizService::toHistoryItem).toList())
				.meta(Meta.builder()
						.page((long) page)
						.size((long) size)
						.total(result.getTotalElements())
						.pages((long) result.getTotalPages())
						.build())
				.build();
	}

	private Quiz ownedQuiz(Long quizId) {
		return quizRepository.findByIdAndUserId(quizId, currentUserId())
				.orElseThrow(() -> notFound(quizId));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public QuestionCheckDTO checkQuestion(Long questionId, CheckQuestionRequest request, String idempotencyKey) {
		Long userId = currentUserId();
		String key = requireKey(idempotencyKey);
		Long quizId = questionRepository.findQuizIdById(questionId).orElseThrow(() -> notFound(questionId));
		expireIfStale(quizId, userId);
		Quiz quiz = quizRepository.findForUpdate(quizId, userId).orElseThrow(() -> notFound(questionId));
		QuizQuestion question = quiz.getQuestions().stream()
				.filter(q -> q.getId().equals(questionId))
				.findFirst()
				.orElseThrow(() -> notFound(questionId));

		if (quiz.getMode() == QuizMode.MATCHING) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST,
					"Matching pairs are checked through the round attempts endpoint");
		}
		if (question.getCheckIdempotencyKey() != null) {
			if (question.getCheckIdempotencyKey().equals(key)) {
				return toCheck(question);
			}
			throw new QuizException(QuizErrorCode.QUESTION_ALREADY_CHECKED,
					"Question " + questionId + " is already graded");
		}
		requireActive(quiz);

		boolean correct;
		String learnerAnswer;
		if (quiz.getMode() == QuizMode.MCQ) {
			String optionKey = request == null ? null : request.optionKey();
			if (optionKey == null || optionKey.isBlank()) {
				throw new QuizException(QuizErrorCode.INVALID_REQUEST, "optionKey is required for MCQ");
			}
			boolean presented = question.getOptions().stream().anyMatch(o -> o.getOptionKey().equals(optionKey));
			if (!presented) {
				throw new QuizException(QuizErrorCode.INVALID_OPTION,
						"Option was not presented for question " + questionId);
			}
			correct = optionKey.equals(question.getCorrectOptionKey());
			learnerAnswer = optionKey;
		} else {
			String answer = request == null ? null : request.answer();
			if (answer == null) {
				throw new QuizException(QuizErrorCode.INVALID_REQUEST, "answer is required for FILL_BLANK");
			}
			correct = QuizGenerator.normalize(answer).equals(QuizGenerator.normalize(question.getCorrectAnswer()));
			learnerAnswer = answer;
		}

		Instant now = now();
		question.setOutcome(correct ? QuizOutcome.CORRECT : QuizOutcome.INCORRECT);
		question.setResolved(true);
		question.setResolvedAt(now);
		question.setAttemptCount(1);
		question.setLearnerAnswer(learnerAnswer);
		question.setCheckIdempotencyKey(key);
		question.setCheckedAt(now);
		return toCheck(question);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MatchingAttemptDTO attemptPair(Long quizId, int roundNo, MatchingAttemptRequest request,
			String idempotencyKey) {
		Long userId = currentUserId();
		String key = requireKey(idempotencyKey);
		expireIfStale(quizId, userId);
		Quiz quiz = quizRepository.findForUpdate(quizId, userId).orElseThrow(() -> notFound(quizId));

		Optional<QuizMatchingAttempt> replay = attemptRepository.findByQuizIdAndIdempotencyKey(quizId, key);
		if (replay.isPresent()) {
			return toAttempt(replay.get());
		}
		requireActive(quiz);
		if (quiz.getMode() != QuizMode.MATCHING || request == null) {
			throw new QuizException(QuizErrorCode.INVALID_REQUEST, "Quiz " + quizId + " is not a matching quiz");
		}

		QuizQuestion left = quiz.getQuestions().stream()
				.filter(q -> Integer.valueOf(roundNo).equals(q.getRoundNo()))
				.filter(q -> request.leftTileKey().equals(q.getLeftKey()))
				.findFirst()
				.orElseThrow(() -> invalidPair(quizId, roundNo));
		QuizMatchingTile tile = quiz.getTiles().stream()
				.filter(t -> t.getRoundNo() == roundNo && t.getTileKey().equals(request.rightTileKey()))
				.findFirst()
				.orElseThrow(() -> invalidPair(quizId, roundNo));
		if (left.isResolved()) {
			throw new QuizException(QuizErrorCode.MATCHING_LEFT_ALREADY_RESOLVED, "Left item already matched");
		}
		if (tile.isMatched()) {
			throw new QuizException(QuizErrorCode.MATCHING_TILE_ALREADY_MATCHED, "Right tile already matched");
		}

		boolean correct = tile.getTileKey().equals(left.getCorrectRightKey());
		left.setAttemptCount(left.getAttemptCount() + 1);
		if (left.getAttemptCount() == 1) {
			left.setOutcome(correct ? QuizOutcome.CORRECT : QuizOutcome.INCORRECT);
		}
		if (correct) {
			Instant now = now();
			left.setResolved(true);
			left.setResolvedAt(now);
			tile.setMatched(true);
		}

		List<QuizQuestion> round = quiz.getQuestions().stream()
				.filter(q -> Integer.valueOf(roundNo).equals(q.getRoundNo()))
				.toList();
		QuizMatchingAttempt attempt = attemptRepository.save(QuizMatchingAttempt.builder()
				.quiz(quiz)
				.question(left)
				.roundNo(roundNo)
				.leftKey(left.getLeftKey())
				.rightKey(tile.getTileKey())
				.correct(correct)
				.attemptNo(left.getAttemptCount())
				.roundMatchedCount((int) round.stream().filter(QuizQuestion::isResolved).count())
				.roundTotalPairs(round.size())
				.idempotencyKey(key)
				.build());
		return toAttempt(attempt);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public QuizResultDTO complete(Long quizId) {
		Long userId = currentUserId();
		expireIfStale(quizId, userId);
		Quiz quiz = quizRepository.findForUpdate(quizId, userId).orElseThrow(() -> notFound(quizId));
		if (quiz.getStatus() == QuizStatus.COMPLETED) {
			return toResult(quiz);
		}
		requireActive(quiz);

		long unresolved = quiz.getQuestions().stream().filter(q -> !q.isResolved()).count();
		if (unresolved > 0) {
			throw new QuizException(QuizErrorCode.QUIZ_HAS_UNRESOLVED_UNITS,
					unresolved + " unit(s) not resolved yet", Map.of("unresolvedCount", unresolved));
		}
		int correct = (int) quiz.getQuestions().stream().filter(q -> q.getOutcome() == QuizOutcome.CORRECT).count();
		quiz.setTotalUnits(quiz.getQuestions().size());
		quiz.setCorrectCount(correct);
		quiz.setIncorrectCount(quiz.getQuestions().size() - correct);
		quiz.setStatus(QuizStatus.COMPLETED);
		quiz.setCompletedAt(now());
		quizRepository.saveAndFlush(quiz);

		eventPublisher.publishEvent(new QuizCompletedEvent(quiz.getId(), userId, quiz.getTopicId(), quiz.getMode(),
				quiz.getTotalUnits(), correct));
		return toResult(quiz);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public QuizLifecycleDTO cancel(Long quizId) {
		Long userId = currentUserId();
		expireIfStale(quizId, userId);
		Quiz quiz = quizRepository.findForUpdate(quizId, userId).orElseThrow(() -> notFound(quizId));
		if (quiz.getStatus() != QuizStatus.CANCELLED) {
			requireActive(quiz);
			quiz.setStatus(QuizStatus.CANCELLED);
			quiz.setCancelledAt(now());
			quizRepository.saveAndFlush(quiz);
		}
		return new QuizLifecycleDTO(quiz.getId(), quiz.getStatus(), quiz.getCancelledAt());
	}

	private static void requireActive(Quiz quiz) {
		if (quiz.getStatus() == QuizStatus.EXPIRED) {
			throw expired(quiz.getId());
		}
		if (quiz.getStatus() != QuizStatus.IN_PROGRESS) {
			throw new QuizException(QuizErrorCode.QUIZ_NOT_IN_PROGRESS,
					"Quiz " + quiz.getId() + " is " + quiz.getStatus(), Map.of("status", quiz.getStatus()));
		}
	}

	private void expireIfStale(Long quizId, Long userId) {
		expiryTransaction.execute(status -> quizRepository.expireStaleById(quizId, userId, now().minus(SESSION_TTL)));
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	private static String requireKey(String key) {
		if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
			throw new QuizException(QuizErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"Idempotency-Key header (1.." + MAX_KEY_LENGTH + " chars) is required");
		}
		return key;
	}

	private static QuizException expired(Long quizId) {
		return new QuizException(QuizErrorCode.QUIZ_SESSION_EXPIRED,
				"Quiz " + quizId + " expired after " + SESSION_TTL.toHours() + "h",
				Map.of("status", QuizStatus.EXPIRED, "ttlHours", SESSION_TTL.toHours()));
	}

	private static QuizException notFound(Long id) {
		return new QuizException(QuizErrorCode.QUIZ_NOT_FOUND, "Quiz resource " + id + " not found");
	}

	private static QuizException invalidPair(Long quizId, int roundNo) {
		return new QuizException(QuizErrorCode.INVALID_MATCHING_PAIR,
				"Tile keys do not belong to quiz " + quizId + " round " + roundNo);
	}

	private Long currentUserId() {
		return SecurityUtils.getCurrentUserId()
				.or(() -> SecurityUtils.getCurrentUserLogin()
						.flatMap(userRepository::findOneByEmailIgnoreCase)
						.map(User::getId))
				.orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}

	private QuizPlayDTO toPlay(Quiz quiz) {
		List<QuizPlayDTO.Question> questions = quiz.getMode() == QuizMode.MATCHING ? List.of()
				: quiz.getQuestions().stream()
						.map(q -> new QuizPlayDTO.Question(q.getId(), q.getPosition(), q.getPrompt(),
								q.getOptions().stream()
										.map(o -> new QuizPlayDTO.Option(o.getOptionKey(), o.getLabel(),
												o.getDisplayOrder()))
										.toList(),
								q.getOutcome(), q.isResolved()))
						.toList();

		List<QuizPlayDTO.MatchingRound> rounds = quiz.getMode() != QuizMode.MATCHING ? List.of()
				: quiz.getTiles().stream().map(QuizMatchingTile::getRoundNo).distinct().sorted()
						.map(roundNo -> toRound(quiz, roundNo))
						.toList();

		return new QuizPlayDTO(quiz.getId(), quiz.getStatus(), quiz.getMode(), quiz.getDirection(), quiz.getTopicId(),
				quiz.getTopicName(), quiz.getRequestedCount(), questions, rounds);
	}

	private static QuizPlayDTO.MatchingRound toRound(Quiz quiz, int roundNo) {
		List<QuizQuestion> lefts = quiz.getQuestions().stream()
				.filter(q -> Integer.valueOf(roundNo).equals(q.getRoundNo()))
				.sorted(Comparator.comparing(QuizQuestion::getLeftDisplayOrder))
				.toList();
		int matched = (int) lefts.stream().filter(QuizQuestion::isResolved).count();
		return new QuizPlayDTO.MatchingRound(roundNo, lefts.size(), matched, matched == lefts.size(),
				lefts.stream()
						.map(q -> new QuizPlayDTO.LeftTile(q.getLeftKey(), q.getPrompt(), q.getLeftDisplayOrder(),
								q.getId(), q.getOutcome(), q.isResolved(), q.getAttemptCount()))
						.toList(),
				quiz.getTiles().stream()
						.filter(t -> t.getRoundNo() == roundNo)
						.map(t -> new QuizPlayDTO.RightTile(t.getTileKey(), t.getLabel(), t.getDisplayOrder(),
								t.isMatched()))
						.toList());
	}

	private static QuestionCheckDTO toCheck(QuizQuestion q) {
		return new QuestionCheckDTO(q.getId(), q.getOutcome(), q.getOutcome() == QuizOutcome.CORRECT,
				q.getCorrectAnswer(), q.getCorrectOptionKey());
	}

	private static MatchingAttemptDTO toAttempt(QuizMatchingAttempt a) {
		QuizOutcome outcome = a.getAttemptNo() == 1 && a.isCorrect() ? QuizOutcome.CORRECT : QuizOutcome.INCORRECT;
		return new MatchingAttemptDTO(a.getQuiz().getId(), a.getRoundNo(), a.getLeftKey(), a.getRightKey(),
				a.isCorrect(), a.getAttemptNo(), outcome, a.isCorrect(),
				new MatchingAttemptDTO.Round(a.getRoundMatchedCount(), a.getRoundTotalPairs(),
						a.getRoundMatchedCount() == a.getRoundTotalPairs()));
	}

	private static QuizResultDTO toResult(Quiz quiz) {
		QuizResultDTO.Summary summary = quiz.getStatus() != QuizStatus.COMPLETED ? null
				: new QuizResultDTO.Summary(quiz.getTotalUnits(), quiz.getCorrectCount(), quiz.getIncorrectCount(),
						accuracy(quiz.getCorrectCount(), quiz.getTotalUnits()));
		List<QuizResultDTO.Item> items = quiz.getQuestions().stream()
				.map(q -> new QuizResultDTO.Item(q.getId(), q.getPosition(), q.getRoundNo(), q.getPrompt(),
						learnerAnswerLabel(quiz, q), q.getCorrectAnswer(), q.getOutcome(), q.isResolved(),
						q.getAttemptCount()))
				.toList();
		return new QuizResultDTO(quiz.getId(), quiz.getStatus(), quiz.getMode(), quiz.getDirection(),
				quiz.getTopicId(), quiz.getTopicName(), summary, quiz.getCompletedAt(), quiz.getCancelledAt(),
				durationSeconds(quiz), items);
	}

	private static Long durationSeconds(Quiz quiz) {
		if (quiz.getCompletedAt() == null || quiz.getCreatedAt() == null) {
			return null;
		}
		return Duration.between(quiz.getCreatedAt(), quiz.getCompletedAt()).getSeconds();
	}

	private static String learnerAnswerLabel(Quiz quiz, QuizQuestion q) {
		if (quiz.getMode() == QuizMode.MCQ && q.getLearnerAnswer() != null) {
			return q.getOptions().stream()
					.filter(o -> o.getOptionKey().equals(q.getLearnerAnswer()))
					.map(QuizOption::getLabel)
					.findFirst()
					.orElse(null);
		}
		return quiz.getMode() == QuizMode.FILL_BLANK ? q.getLearnerAnswer() : null;
	}

	private static QuizHistoryItemDTO toHistoryItem(Quiz quiz) {
		Double accuracy = quiz.getStatus() == QuizStatus.COMPLETED
				? accuracy(quiz.getCorrectCount(), quiz.getTotalUnits())
				: null;
		return new QuizHistoryItemDTO(quiz.getId(), quiz.getTopicId(), quiz.getTopicName(), quiz.getMode(),
				quiz.getDirection(), quiz.getStatus(), quiz.getRequestedCount(), quiz.getCorrectCount(),
				quiz.getIncorrectCount(), accuracy, quiz.getCreatedAt(), quiz.getCompletedAt());
	}

	private static double accuracy(Integer correct, Integer total) {
		return total == null || total == 0 ? 0.0 : (double) correct / total;
	}
}

