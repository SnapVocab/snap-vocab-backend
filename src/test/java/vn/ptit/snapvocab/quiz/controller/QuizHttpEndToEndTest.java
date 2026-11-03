package vn.ptit.snapvocab.quiz.controller;

import vn.ptit.snapvocab.common.util.FormatRestResponse;
import vn.ptit.snapvocab.config.DataInitializer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.MinioClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.quiz.domain.Quiz;
import vn.ptit.snapvocab.quiz.domain.QuizQuestion;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.quiz.domain.dto.CheckQuestionRequest;
import vn.ptit.snapvocab.quiz.domain.dto.CreateQuizRequest;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptDTO;
import vn.ptit.snapvocab.quiz.domain.dto.MatchingAttemptRequest;
import vn.ptit.snapvocab.quiz.domain.dto.QuestionCheckDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizPlayDTO;
import vn.ptit.snapvocab.quiz.domain.dto.QuizResultDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizDirection;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizOutcome;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizStatus;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.quiz.repository.QuizMatchingAttemptRepository;
import vn.ptit.snapvocab.quiz.repository.QuizQuestionRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.DomainUserDetailsService.UserWithId;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;

/**
 * Real End-to-End HTTP API verification against local Spring Boot context and local database.
 * Tests full client HTTP lifecycle:
 * HTTP Request -> Spring Security Filter (Bearer JWT) -> QuizController -> QuizService -> LOCAL MySQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
public class QuizHttpEndToEndTest {

	@MockitoBean
	private MinioClient minioClient;

	@MockitoBean
	private RedissonClient redissonClient;

	@MockitoBean
	private CacheManager cacheManager;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private DataInitializer dataInitializer;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CollectionRepository collectionRepository;

	@Autowired
	private TopicRepository topicRepository;

	@Autowired
	private TopicItemRepository topicItemRepository;

	@Autowired
	private SchemaRepository schemaRepository;

	@Autowired
	private SchemaAttributeGroupRepository groupRepository;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private QuizRepository quizRepository;

	@Autowired
	private QuizQuestionRepository questionRepository;

	@Autowired
	private QuizMatchingAttemptRepository attemptRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transactionManager;

	private List<QuizQuestion> getQuizQuestions(Long quizId) {
		return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
			Quiz quiz = quizRepository.findById(quizId).orElseThrow();
			quiz.getQuestions().forEach(q -> q.getOptions().size());
			return new ArrayList<>(quiz.getQuestions());
		});
	}

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private <T> T extractData(ResponseEntity<String> response, Class<T> clazz) {
		try {
			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode data = root.path("data");
			if (data.isMissingNode() || data.isNull()) {
				return null;
			}
			return objectMapper.treeToValue(data, clazz);
		} catch (Exception e) {
			throw new RuntimeException("Failed to extract data: " + response.getBody(), e);
		}
	}

	private <T> T postForData(String url, Object body, String token, String idempotencyKey, Class<T> clazz) {
		HttpHeaders headers = authHeaders(token, idempotencyKey);
		HttpEntity<Object> entity = new HttpEntity<>(body, headers);
		ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
		assertThat(resp.getStatusCode().is2xxSuccessful())
				.withFailMessage("POST %s failed with %s: %s", url, resp.getStatusCode(), resp.getBody())
				.isTrue();
		return extractData(resp, clazz);
	}

	private static final String TEST_PASSWORD_HASH = "a".repeat(60);

	private User learnerUser;
	private String learnerToken;

	@BeforeEach
	void setUpUser() {
		// This cached context seeded DEFAULT_ENGLISH at startup, but any later test context with ddl-auto=create
		// recreates the shared test schema; re-seed (idempotent) instead of depending on test class order.
		dataInitializer.run();
		learnerUser = userRepository.save(User.builder()
				.email("learner-" + UUID.randomUUID() + "@snapvocab.local")
				.password(TEST_PASSWORD_HASH)
				.firstName("Learner")
				.activated(true)
				.build());
		learnerToken = generateBearerToken(learnerUser);
	}

	@AfterEach
	void cleanUp() {
		for (String table : List.of(
				"quiz_matching_attempts",
				"quiz_question_options",
				"quiz_matching_tiles",
				"quiz_questions",
				"quizzes",
				"topic_item_attribute_values",
				"topic_item_attribute_groups",
				"topic_items",
				"topics",
				"collections")) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
	}

	private String generateBearerToken(User user) {
		Collection<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				UserWithId.fromUser(user),
				null,
				authorities
		);
		Instant validity = Instant.now().plus(3600, ChronoUnit.SECONDS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuedAt(Instant.now())
				.expiresAt(validity)
				.subject(user.getEmail())
				.claim(SecurityUtils.AUTHORITIES_CLAIM, "ROLE_USER")
				.claim(SecurityUtils.USER_ID_CLAIM, user.getId())
				.build();
		JwsHeader jwsHeader = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
	}

	private HttpHeaders authHeaders(String token, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.setBearerAuth(token);
		}
		if (idempotencyKey != null) {
			headers.set("Idempotency-Key", idempotencyKey);
		}
		return headers;
	}

	// --- Test Data Helpers ----------------------------------------------------------------------

	private Schema defaultSchema() {
		return schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow();
	}

	private Map<String, SchemaAttribute> defaultAttributes() {
		return groupRepository.findBySchemaIdWithAttributes(defaultSchema().getId()).stream()
				.flatMap(g -> g.getAttributes().stream())
				.collect(Collectors.toMap(a -> a.getGroup().getName() + "." + a.getName(), a -> a));
	}

	private Topic createTopic(String templateCode) {
		Schema schema = defaultSchema();
		vn.ptit.snapvocab.vocab.domain.Collection collection = collectionRepository.save(vn.ptit.snapvocab.vocab.domain.Collection.builder()
				.name("SYSTEM Collection")
				.type(CollectionType.SYSTEM)
				.build());
		return topicRepository.save(Topic.builder()
				.collection(collection)
				.name("SYSTEM Topic")
				.schema(schema)
				.activeTemplate(templateCode != null
						? templateRepository.findBySchemaIdAndCode(schema.getId(), templateCode).orElseThrow()
						: null)
				.build());
	}

	private void addVocabularyItem(Topic topic, String word, String translation, String example) {
		Map<String, SchemaAttribute> attrs = defaultAttributes();
		Map<String, SchemaAttributeGroup> groups = attrs.values().stream()
				.map(SchemaAttribute::getGroup)
				.collect(Collectors.toMap(SchemaAttributeGroup::getName, g -> g, (a, b) -> a));

		TopicItem item = TopicItem.builder().topic(topic).build();

		TopicItemAttributeGroup main = TopicItemAttributeGroup.builder().item(item)
				.groupDefinition(groups.get("main")).position((short) 1).build();
		if (word != null) {
			main.getValues().add(TopicItemAttributeValue.builder().groupInstance(main).attribute(attrs.get("main.word")).value(word).build());
		}
		if (translation != null) {
			main.getValues().add(TopicItemAttributeValue.builder().groupInstance(main).attribute(attrs.get("main.translation")).value(translation).build());
		}
		main.getValues().add(TopicItemAttributeValue.builder().groupInstance(main).attribute(attrs.get("main.definition_translation")).value("định nghĩa " + word).build());
		item.getGroupInstances().add(main);

		TopicItemAttributeGroup meaning = TopicItemAttributeGroup.builder().item(item)
				.groupDefinition(groups.get("meaning")).position((short) 1).build();
		meaning.getValues().add(TopicItemAttributeValue.builder().groupInstance(meaning).attribute(attrs.get("meaning.meaning")).value("definition of " + word).build());
		item.getGroupInstances().add(meaning);

		if (example != null) {
			TopicItemAttributeGroup ex = TopicItemAttributeGroup.builder().item(item)
					.groupDefinition(groups.get("examples")).position((short) 1).build();
			ex.getValues().add(TopicItemAttributeValue.builder().groupInstance(ex).attribute(attrs.get("examples.content")).value(example).build());
			item.getGroupInstances().add(ex);
		}
		topicItemRepository.save(item);
	}

	private Topic seedTopicWithVocab(String templateCode, int count) {
		Topic topic = createTopic(templateCode);
		for (int i = 1; i <= count; i++) {
			addVocabularyItem(topic, "word" + i, "nghĩa " + i, "Example sentence containing word" + i + ".");
		}
		return topic;
	}

	// ============================================================================================
	// 1. API RESPONSE SECURITY: Unanswered Quiz must NEVER leak answer key
	// ============================================================================================

	@Test
	@DisplayName("Security: Unanswered Quiz response does NOT expose correct answers or pairs")
	void test01_ResponseSecurity_UnansweredQuizNeverLeaksAnswerKey() throws Exception {
		Topic topic = seedTopicWithVocab("STANDARD", 10);

		// 1. MCQ create response
		CreateQuizRequest mcqReq = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		HttpEntity<CreateQuizRequest> entity = new HttpEntity<>(mcqReq, authHeaders(learnerToken, "sec-key-mcq"));
		ResponseEntity<String> mcqResp = restTemplate.postForEntity("/api/me/quizzes", entity, String.class);

		assertThat(mcqResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String mcqBody = mcqResp.getBody();
		assertThat(mcqBody).doesNotContain("correctOptionKey");
		assertThat(mcqBody).doesNotContain("correctAnswer");
		assertThat(mcqBody).doesNotContain("correct_option_key");
		assertThat(mcqBody).doesNotContain("correct_answer");

		// Inspect JSON structure: options must not have "correct" property
		JsonNode root = objectMapper.readTree(mcqBody);
		JsonNode questions = root.path("data").path("questions");
		assertThat(questions.size()).isEqualTo(5);
		for (JsonNode q : questions) {
			assertThat(q.has("correctOptionKey")).isFalse();
			assertThat(q.has("correctAnswer")).isFalse();
			JsonNode options = q.path("options");
			for (JsonNode opt : options) {
				assertThat(opt.has("correct")).isFalse();
			}
		}

		// 2. FILL_BLANK create response
		CreateQuizRequest fillReq = new CreateQuizRequest(topic.getId(), QuizMode.FILL_BLANK, null, 5);
		ResponseEntity<String> fillResp = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(fillReq, authHeaders(learnerToken, "sec-key-fill")),
				String.class
		);
		assertThat(fillResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String fillBody = fillResp.getBody();
		assertThat(fillBody).doesNotContain("correctAnswer");
		assertThat(fillBody).doesNotContain("correct_answer");

		// 3. MATCHING create response
		CreateQuizRequest matchReq = new CreateQuizRequest(topic.getId(), QuizMode.MATCHING, QuizDirection.EN_VI, 5);
		ResponseEntity<String> matchResp = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(matchReq, authHeaders(learnerToken, "sec-key-match")),
				String.class
		);
		assertThat(matchResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String matchBody = matchResp.getBody();
		JsonNode matchJson = objectMapper.readTree(matchBody);
		JsonNode round = matchJson.path("data").path("matchingRounds").get(0);
		JsonNode leftTiles = round.path("leftTiles");
		JsonNode rightTiles = round.path("rightTiles");
		assertThat(leftTiles.size()).isEqualTo(5);
		assertThat(rightTiles.size()).isEqualTo(5);
		for (JsonNode right : rightTiles) {
			assertThat(right.has("questionId")).isFalse();
			assertThat(right.has("correctLeftKey")).isFalse();
			assertThat(right.has("leftKey")).isFalse();
		}
	}

	// ============================================================================================
	// 2. AUTHENTICATION & USER ISOLATION
	// ============================================================================================

	@Test
	@DisplayName("Auth: Enforces Bearer JWT and protects sessions from other users")
	void test02_AuthenticationAndOwnership() {
		Topic topic = seedTopicWithVocab("STANDARD", 6);

		// Without auth -> 401
		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		ResponseEntity<String> unauthResp = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(req, authHeaders(null, "key-unauth")),
				String.class
		);
		assertThat(unauthResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// With User A auth -> 201
		QuizPlayDTO playA = postForData("/api/me/quizzes", req, learnerToken, "key-user-a", QuizPlayDTO.class);
		Long quizId = playA.quizId();
		assertThat(quizId).isNotNull();

		// User B cannot access User A's quiz
		User userB = userRepository.save(User.builder()
				.email("user-b-" + UUID.randomUUID() + "@test.local")
				.password(TEST_PASSWORD_HASH)
				.firstName("UserB")
				.activated(true)
				.build());
		String userBToken = generateBearerToken(userB);

		ResponseEntity<String> intruderGet = restTemplate.exchange(
				"/api/me/quizzes/" + quizId,
				HttpMethod.GET,
				new HttpEntity<>(authHeaders(userBToken, null)),
				String.class
		);
		assertThat(intruderGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// User B's history does NOT show User A's quiz
		ResponseEntity<String> intruderHistory = restTemplate.exchange(
				"/api/me/quizzes",
				HttpMethod.GET,
				new HttpEntity<>(authHeaders(userBToken, null)),
				String.class
		);
		assertThat(intruderHistory.getBody()).doesNotContain("\"quizId\":" + quizId);
	}

	// ============================================================================================
	// 3. MCQ END-TO-END FLOW (EN_VI & VI_EN)
	// ============================================================================================

	@Test
	@DisplayName("MCQ E2E: Full run with EN_VI and VI_EN, checking options, completion and results")
	void test03_McqEndToEnd_EnVi_And_ViEn() {
		Topic topic = seedTopicWithVocab("STANDARD", 10);

		for (QuizDirection direction : List.of(QuizDirection.EN_VI, QuizDirection.VI_EN)) {
			String createKey = "mcq-create-" + direction;
			CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, direction, 5);

			QuizPlayDTO play = postForData("/api/me/quizzes", req, learnerToken, createKey, QuizPlayDTO.class);
			assertThat(play.questions()).hasSize(5);

			List<QuizQuestion> dbQuestions = getQuizQuestions(play.quizId());
			int correctCount = 0;

			for (int i = 0; i < dbQuestions.size(); i++) {
				QuizQuestion dbQ = dbQuestions.get(i);
				// Answer first 4 correctly, last 1 incorrectly
				boolean answerCorrectly = (i < 4);
				String selectedOptionKey;
				if (answerCorrectly) {
					selectedOptionKey = dbQ.getCorrectOptionKey();
					correctCount++;
				} else {
					selectedOptionKey = dbQ.getOptions().stream()
							.map(o -> o.getOptionKey())
							.filter(k -> !k.equals(dbQ.getCorrectOptionKey()))
							.findFirst()
							.orElseThrow();
				}

				CheckQuestionRequest checkReq = new CheckQuestionRequest(selectedOptionKey, null);
				String checkKey = "check-mcq-" + direction + "-" + dbQ.getId();
				QuestionCheckDTO checkResult = postForData(
						"/api/questions/" + dbQ.getId() + "/check",
						checkReq,
						learnerToken,
						checkKey,
						QuestionCheckDTO.class
				);
				assertThat(checkResult.correct()).isEqualTo(answerCorrectly);
				assertThat(checkResult.outcome()).isEqualTo(answerCorrectly ? QuizOutcome.CORRECT : QuizOutcome.INCORRECT);
				assertThat(checkResult.correctOptionKey()).isEqualTo(dbQ.getCorrectOptionKey());
			}

			// Complete quiz
			QuizResultDTO result = postForData(
					"/api/me/quizzes/" + play.quizId() + "/complete",
					null,
					learnerToken,
					null,
					QuizResultDTO.class
			);
			assertThat(result.status()).isEqualTo(QuizStatus.COMPLETED);
			assertThat(result.summary().totalUnits()).isEqualTo(5);
			assertThat(result.summary().correctCount()).isEqualTo(4);
			assertThat(result.summary().incorrectCount()).isEqualTo(1);
			assertThat(result.summary().accuracy()).isEqualTo(0.8);
		}
	}

	// ============================================================================================
	// 4. FILL_BLANK END-TO-END FLOW
	// ============================================================================================

	@Test
	@DisplayName("FILL_BLANK E2E: Masked prompt, case/whitespace normalization, complete and result")
	void test04_FillBlankEndToEnd() {
		Topic topic = seedTopicWithVocab("STANDARD", 8);

		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.FILL_BLANK, null, 5);
		QuizPlayDTO play = postForData("/api/me/quizzes", req, learnerToken, "fill-blank-create", QuizPlayDTO.class);

		// Prompt must have _____ and NOT leak target word
		for (var q : play.questions()) {
			assertThat(q.prompt()).contains("_____");
			assertThat(q.options()).isEmpty();
		}

		List<QuizQuestion> dbQuestions = getQuizQuestions(play.quizId());

		// Question 1: correct with lowercase and padded spaces
		String target1 = dbQuestions.get(0).getCorrectAnswer();
		QuestionCheckDTO check1 = postForData(
				"/api/questions/" + dbQuestions.get(0).getId() + "/check",
				new CheckQuestionRequest(null, "  " + target1.toLowerCase() + "  "),
				learnerToken,
				"f-1",
				QuestionCheckDTO.class
		);
		assertThat(check1.correct()).isTrue();
		assertThat(check1.outcome()).isEqualTo(QuizOutcome.CORRECT);

		// Question 2: incorrect answer
		QuestionCheckDTO check2 = postForData(
				"/api/questions/" + dbQuestions.get(1).getId() + "/check",
				new CheckQuestionRequest(null, "totallyWrongWord"),
				learnerToken,
				"f-2",
				QuestionCheckDTO.class
		);
		assertThat(check2.correct()).isFalse();
		assertThat(check2.outcome()).isEqualTo(QuizOutcome.INCORRECT);
		assertThat(check2.correctAnswer()).isEqualTo(dbQuestions.get(1).getCorrectAnswer());

		// Complete remaining questions
		for (int i = 2; i < 5; i++) {
			postForData(
					"/api/questions/" + dbQuestions.get(i).getId() + "/check",
					new CheckQuestionRequest(null, dbQuestions.get(i).getCorrectAnswer()),
					learnerToken,
					"f-" + i,
					QuestionCheckDTO.class
			);
		}

		// Complete session
		QuizResultDTO result = postForData(
				"/api/me/quizzes/" + play.quizId() + "/complete",
				null,
				learnerToken,
				null,
				QuizResultDTO.class
		);
		assertThat(result.summary().correctCount()).isEqualTo(4);
		assertThat(result.summary().incorrectCount()).isEqualTo(1);
	}

	// ============================================================================================
	// 5. MATCHING END-TO-END FLOW (Sizes 5, 10, 20)
	// ============================================================================================

	@Test
	@DisplayName("MATCHING E2E: Test sizes 5 (1x5), 10 (2x5), 20 (4x5), per-pair attempts and retry without reveal")
	void test05_MatchingEndToEnd_Sizes5_10_20() {
		// Test sizes 5, 10, 20
		for (int requestedCount : List.of(5, 10, 20)) {
			Topic topic = seedTopicWithVocab("STANDARD", 20);
			CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MATCHING, QuizDirection.EN_VI, requestedCount);

			QuizPlayDTO play = postForData(
					"/api/me/quizzes",
					req,
					learnerToken,
					"match-create-" + requestedCount,
					QuizPlayDTO.class
			);

			int expectedRounds = requestedCount / 5;
			assertThat(play.matchingRounds()).hasSize(expectedRounds);

			Long quizId = play.quizId();
			List<QuizQuestion> allQuestions = getQuizQuestions(quizId);

			// For each round
			for (int r = 1; r <= expectedRounds; r++) {
				final int roundNo = r;
				List<QuizQuestion> roundQuestions = allQuestions.stream()
						.filter(q -> Integer.valueOf(roundNo).equals(q.getRoundNo()))
						.toList();
				assertThat(roundQuestions).hasSize(5);

				// In Round 1, test WRONG pair first: must not reveal answer, stays available
				if (roundNo == 1) {
					QuizQuestion firstQ = roundQuestions.get(0);
					QuizQuestion secondQ = roundQuestions.get(1);

					MatchingAttemptRequest wrongAttempt = new MatchingAttemptRequest(
							firstQ.getLeftKey(),
							secondQ.getCorrectRightKey() // mismatched
					);

					MatchingAttemptDTO wrongResult = postForData(
							"/api/me/quizzes/" + quizId + "/rounds/" + roundNo + "/attempts",
							wrongAttempt,
							learnerToken,
							"wrong-pair-1",
							MatchingAttemptDTO.class
					);

					assertThat(wrongResult.correct()).isFalse();
					assertThat(wrongResult.resolved()).isFalse();
					assertThat(wrongResult.outcome()).isEqualTo(QuizOutcome.INCORRECT);
					assertThat(wrongResult.round().matchedCount()).isZero();
				}

				// Now match all pairs correctly in this round
				for (QuizQuestion q : roundQuestions) {
					MatchingAttemptRequest pair = new MatchingAttemptRequest(q.getLeftKey(), q.getCorrectRightKey());
					MatchingAttemptDTO attemptResp = postForData(
							"/api/me/quizzes/" + quizId + "/rounds/" + roundNo + "/attempts",
							pair,
							learnerToken,
							"correct-pair-" + q.getId(),
							MatchingAttemptDTO.class
					);
					assertThat(attemptResp.correct()).isTrue();
					assertThat(attemptResp.resolved()).isTrue();
				}
			}

			// Complete quiz
			QuizResultDTO result = postForData(
					"/api/me/quizzes/" + quizId + "/complete",
					null,
					learnerToken,
					null,
					QuizResultDTO.class
			);
			assertThat(result.status()).isEqualTo(QuizStatus.COMPLETED);
			assertThat(result.summary().totalUnits()).isEqualTo(requestedCount);
		}
	}

	// ============================================================================================
	// 6. IDEMPOTENCY END-TO-END FLOW (Create, Check, Matching Attempt, Complete)
	// ============================================================================================

	@Test
	@DisplayName("Idempotency: Replaying requests with the same key returns identical results without duplicates")
	void test06_IdempotencyRetries() {
		Topic topic = seedTopicWithVocab("STANDARD", 10);
		String createKey = "idemp-create-key";

		// 1. Create retry
		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		ResponseEntity<String> create1 = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(req, authHeaders(learnerToken, createKey)),
				String.class
		);
		ResponseEntity<String> create2 = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(req, authHeaders(learnerToken, createKey)),
				String.class
		);
		assertThat(create1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(create2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		QuizPlayDTO play1 = extractData(create1, QuizPlayDTO.class);
		QuizPlayDTO play2 = extractData(create2, QuizPlayDTO.class);
		assertThat(play1.quizId()).isEqualTo(play2.quizId());
		assertThat(quizRepository.count()).isEqualTo(1);

		Long quizId = play1.quizId();
		List<QuizQuestion> questions = getQuizQuestions(quizId);
		QuizQuestion q1 = questions.get(0);

		// 2. Question check retry
		String checkKey = "idemp-check-key";
		CheckQuestionRequest checkReq = new CheckQuestionRequest(q1.getCorrectOptionKey(), null);
		ResponseEntity<String> check1 = restTemplate.postForEntity(
				"/api/questions/" + q1.getId() + "/check",
				new HttpEntity<>(checkReq, authHeaders(learnerToken, checkKey)),
				String.class
		);
		ResponseEntity<String> check2 = restTemplate.postForEntity(
				"/api/questions/" + q1.getId() + "/check",
				new HttpEntity<>(checkReq, authHeaders(learnerToken, checkKey)),
				String.class
		);
		assertThat(check1.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(check2.getStatusCode()).isEqualTo(HttpStatus.OK);
		QuestionCheckDTO chk1 = extractData(check1, QuestionCheckDTO.class);
		QuestionCheckDTO chk2 = extractData(check2, QuestionCheckDTO.class);
		assertThat(chk1).isEqualTo(chk2);

		// Check with DIFFERENT key after checked throws 409 QUESTION_ALREADY_CHECKED
		ResponseEntity<String> conflictCheck = restTemplate.postForEntity(
				"/api/questions/" + q1.getId() + "/check",
				new HttpEntity<>(checkReq, authHeaders(learnerToken, "different-key")),
				String.class
		);
		assertThat(conflictCheck.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflictCheck.getBody()).contains("QUESTION_ALREADY_CHECKED");

		// Complete remaining questions
		for (int i = 1; i < questions.size(); i++) {
			QuizQuestion q = questions.get(i);
			postForData(
					"/api/questions/" + q.getId() + "/check",
					new CheckQuestionRequest(q.getCorrectOptionKey(), null),
					learnerToken,
					"chk-" + q.getId(),
					QuestionCheckDTO.class
			);
		}

		// 3. Complete retry
		ResponseEntity<String> comp1 = restTemplate.postForEntity(
				"/api/me/quizzes/" + quizId + "/complete",
				new HttpEntity<>(authHeaders(learnerToken, null)),
				String.class
		);
		ResponseEntity<String> comp2 = restTemplate.postForEntity(
				"/api/me/quizzes/" + quizId + "/complete",
				new HttpEntity<>(authHeaders(learnerToken, null)),
				String.class
		);
		assertThat(comp1.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(comp2.getStatusCode()).isEqualTo(HttpStatus.OK);
		QuizResultDTO res1 = extractData(comp1, QuizResultDTO.class);
		QuizResultDTO res2 = extractData(comp2, QuizResultDTO.class);
		assertThat(res1.summary()).isEqualTo(res2.summary());
	}

	// ============================================================================================
	// 7. INSUFFICIENT DATA ERROR FLOW
	// ============================================================================================

	@Test
	@DisplayName("Errors: Returns QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS when eligible pool < requestedCount")
	void test07_InsufficientData() {
		// Topic with only 7 items
		Topic topic = seedTopicWithVocab("STANDARD", 7);

		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 10);
		ResponseEntity<String> resp = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(req, authHeaders(learnerToken, "insufficient-test-key")),
				String.class
		);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(resp.getBody()).contains("QUIZ_INSUFFICIENT_ELIGIBLE_ITEMS");
		assertThat(resp.getBody()).contains("\"requestedCount\":10");
		assertThat(resp.getBody()).contains("\"eligibleCount\":7");
		assertThat(quizRepository.count()).isZero();
	}

	// ============================================================================================
	// 8. TEMPLATE CONFIGURATION ERROR FLOW
	// ============================================================================================

	@Test
	@DisplayName("Errors: Returns TOPIC_TEMPLATE_NOT_CONFIGURED when topic has no activeTemplate")
	void test08_TemplateNotConfigured() {
		// Topic without activeTemplate
		Topic topic = createTopic(null);
		for (int i = 1; i <= 6; i++) {
			addVocabularyItem(topic, "word" + i, "nghĩa " + i, "Example sentence " + i);
		}

		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		ResponseEntity<String> resp = restTemplate.postForEntity(
				"/api/me/quizzes",
				new HttpEntity<>(req, authHeaders(learnerToken, "no-template-key")),
				String.class
		);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(resp.getBody()).contains("TOPIC_TEMPLATE_NOT_CONFIGURED");
		assertThat(quizRepository.count()).isZero();
	}

	// ============================================================================================
	// 9. CANCEL FLOW
	// ============================================================================================

	@Test
	@DisplayName("Cancel: Cancels session, sets CANCELLED, and blocks subsequent grading or completion")
	void test09_CancelFlow() {
		Topic topic = seedTopicWithVocab("STANDARD", 6);

		CreateQuizRequest req = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		QuizPlayDTO play = postForData("/api/me/quizzes", req, learnerToken, "cancel-key-create", QuizPlayDTO.class);
		Long quizId = play.quizId();

		// Cancel quiz
		ResponseEntity<String> cancelResp = restTemplate.postForEntity(
				"/api/me/quizzes/" + quizId + "/cancel",
				new HttpEntity<>(authHeaders(learnerToken, "cancel-action-key")),
				String.class
		);
		assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelResp.getBody()).contains("\"status\":\"CANCELLED\"");

		Quiz inDb = quizRepository.findById(quizId).orElseThrow();
		assertThat(inDb.getStatus()).isEqualTo(QuizStatus.CANCELLED);
		assertThat(inDb.getCancelledAt()).isNotNull();

		// Check question on cancelled quiz -> 409 QUIZ_NOT_IN_PROGRESS
		QuizQuestion q1 = getQuizQuestions(quizId).get(0);
		ResponseEntity<String> checkResp = restTemplate.postForEntity(
				"/api/questions/" + q1.getId() + "/check",
				new HttpEntity<>(new CheckQuestionRequest(q1.getCorrectOptionKey(), null), authHeaders(learnerToken, "chk-cancelled")),
				String.class
		);
		assertThat(checkResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(checkResp.getBody()).contains("QUIZ_NOT_IN_PROGRESS");

		// Complete on cancelled quiz -> 409 QUIZ_NOT_IN_PROGRESS
		ResponseEntity<String> compResp = restTemplate.postForEntity(
				"/api/me/quizzes/" + quizId + "/complete",
				new HttpEntity<>(authHeaders(learnerToken, null)),
				String.class
		);
		assertThat(compResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(compResp.getBody()).contains("QUIZ_NOT_IN_PROGRESS");
	}

	// ============================================================================================
	// 10. HISTORY FLOW
	// ============================================================================================

	@Test
	@DisplayName("History: GET /api/me/quizzes returns paginated list sorted newest first with accurate status")
	void test10_HistoryFlow() {
		Topic topic = seedTopicWithVocab("STANDARD", 10);

		// Create quiz 1 (completed)
		CreateQuizRequest req1 = new CreateQuizRequest(topic.getId(), QuizMode.MCQ, QuizDirection.EN_VI, 5);
		QuizPlayDTO play1 = postForData("/api/me/quizzes", req1, learnerToken, "h-create-1", QuizPlayDTO.class);
		for (var q : getQuizQuestions(play1.quizId())) {
			postForData(
					"/api/questions/" + q.getId() + "/check",
					new CheckQuestionRequest(q.getCorrectOptionKey(), null),
					learnerToken,
					"h-chk-" + q.getId(),
					QuestionCheckDTO.class
			);
		}
		postForData("/api/me/quizzes/" + play1.quizId() + "/complete", null, learnerToken, null, QuizResultDTO.class);

		// Create quiz 2 (in-progress)
		CreateQuizRequest req2 = new CreateQuizRequest(topic.getId(), QuizMode.MATCHING, QuizDirection.VI_EN, 5);
		QuizPlayDTO play2 = postForData("/api/me/quizzes", req2, learnerToken, "h-create-2", QuizPlayDTO.class);

		// Fetch history
		ResponseEntity<String> historyResp = restTemplate.exchange(
				"/api/me/quizzes?page=0&size=10",
				HttpMethod.GET,
				new HttpEntity<>(authHeaders(learnerToken, null)),
				String.class
		);
		assertThat(historyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

		try {
			JsonNode json = objectMapper.readTree(historyResp.getBody());
			// FormatRestResponse wraps in { statusCode, data: { data: [...], meta: {...} } }
			JsonNode data = json.path("data").path("data");
			assertThat(data.isArray()).isTrue();
			assertThat(data.size()).isEqualTo(2);

			// Newest first: quiz2 (matching, in-progress) first, quiz1 (mcq, completed) second
			assertThat(data.get(0).path("quizId").asLong()).isEqualTo(play2.quizId());
			assertThat(data.get(0).path("status").asText()).isEqualTo("IN_PROGRESS");
			assertThat(data.get(0).path("mode").asText()).isEqualTo("MATCHING");

			assertThat(data.get(1).path("quizId").asLong()).isEqualTo(play1.quizId());
			assertThat(data.get(1).path("status").asText()).isEqualTo("COMPLETED");
			assertThat(data.get(1).path("mode").asText()).isEqualTo("MCQ");
			assertThat(data.get(1).path("accuracy").asDouble()).isEqualTo(1.0);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

