package vn.ptit.snapvocab.vocab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;

/**
 * Admin content v1 over HTTP → Security → services → LOCAL MySQL: dictionary edit / soft delete, system topic edit /
 * delete, topic item edit / delete (WI-04 guard), card template edit / default. Same mocks as AdminAuditHttpTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class AdminContentHttpTest {

	private static final String REASON = "Chủ đề trùng nội dung, gộp vào chủ đề khác.";

	@MockitoBean
	MinioClient minioClient;
	@MockitoBean
	RedissonClient redissonClient;
	@MockitoBean
	CacheManager cacheManager;

	@LocalServerPort
	int port;
	@Autowired
	JwtEncoder jwtEncoder;
	@Autowired
	UserRepository userRepository;
	@Autowired
	SchemaRepository schemaRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	TransactionTemplate tx;

	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newHttpClient();
	private final String tag = "content-it-" + UUID.randomUUID().toString().substring(0, 8);
	private String admin;
	private String learnerToken;
	private User learner;
	private Fixture schema;

	/** The test's own system schema (main: word, translation, phonetic; examples: content) and two templates. */
	record Fixture(long schemaId, long word, long translation, long phonetic, long example, long standardId,
			long listeningId, long hiddenOnlyId) {
	}

	@BeforeEach
	void setUp() {
		cleanUp();
		when(cacheManager.getCache(anyString())).thenAnswer(inv -> new ConcurrentMapCache(inv.getArgument(0)));
		jdbc.update("INSERT IGNORE INTO authorities (name) VALUES ('ROLE_ADMIN')");
		User adminUser = saveUser("admin");
		jdbc.update("INSERT INTO user_authorities (user_id, authority_name) VALUES (?, 'ROLE_ADMIN')", adminUser.getId());
		admin = bearer(adminUser, "ROLE_USER ROLE_ADMIN");
		learner = saveUser("learner");
		learnerToken = bearer(learner, "ROLE_USER");
		schema = tx.execute(status -> buildSchema());
	}

	@AfterEach
	void cleanUp() {
		String users = "SELECT id FROM users WHERE email LIKE 'content-it-%'";
		String missions = "SELECT id FROM user_daily_missions WHERE user_id IN (SELECT id FROM (" + users + ") u)";
		for (String child : List.of("user_daily_mission_claims", "user_daily_mission_progress_logs", "user_word_credits")) {
			jdbc.update("DELETE FROM " + child + " WHERE user_daily_mission_id IN (SELECT id FROM (" + missions + ") m)");
		}
		// What a learner adding a word to their own topic leaves behind (missions, badges, progress, study settings).
		jdbc.update("DELETE qq FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id WHERE q.user_id IN (SELECT id FROM ("
				+ users + ") u)");
		for (String table : List.of("quizzes", "user_word_credits", "user_daily_missions", "user_badge_word_credits", "user_badges",
				"learning_events", "experience_logs", "coin_transactions", "study_daily_cards", "study_daily_progress",
				"collection_study_settings", "fsrs_records", "user_words", "user_settings")) {
			jdbc.update("DELETE FROM " + table + " WHERE user_id IN (SELECT id FROM (" + users + ") u)");
		}
		String words = "SELECT id FROM words WHERE word LIKE 'content-it-%'";
		jdbc.update("DELETE FROM user_words WHERE word_id IN (" + words + ")");
		jdbc.update("DELETE FROM pronunciations WHERE word_id IN (" + words + ")");
		jdbc.update("DELETE FROM translations WHERE word_id IN (" + words + ")");
		jdbc.update("DELETE FROM word_definitions WHERE word_id IN (" + words + ")");
		jdbc.update("DELETE FROM definitions WHERE definition LIKE 'content-it-%'");
		jdbc.update("DELETE FROM words WHERE word LIKE 'content-it-%'");
		String topics = "SELECT t.id FROM topics t JOIN collections c ON c.id = t.collection_id WHERE c.name LIKE 'content-it-%'";
		String items = "SELECT ti.id FROM topic_items ti WHERE ti.topic_id IN (SELECT id FROM (" + topics + ") x)";
		jdbc.update("DELETE FROM fsrs_records WHERE topic_item_id IN (SELECT id FROM (" + items + ") y)");
		jdbc.update("DELETE v FROM topic_item_attribute_values v JOIN topic_item_attribute_groups g ON g.id = v.group_instance_id"
				+ " WHERE g.topic_item_id IN (SELECT id FROM (" + items + ") y)");
		jdbc.update("DELETE FROM topic_item_attribute_groups WHERE topic_item_id IN (SELECT id FROM (" + items + ") y)");
		jdbc.update("DELETE FROM topic_items WHERE topic_id IN (SELECT id FROM (" + topics + ") x)");
		jdbc.update("UPDATE topics SET parent_id = NULL WHERE id IN (SELECT id FROM (" + topics + ") x)");
		jdbc.update("DELETE FROM topics WHERE id IN (SELECT id FROM (" + topics + ") x)");
		jdbc.update("DELETE FROM collections WHERE name LIKE 'content-it-%'");
		String schemas = "SELECT id FROM `schemas` WHERE code LIKE 'CONTENT_IT_%'";
		jdbc.update("DELETE f FROM template_fields f JOIN template_elements e ON e.id = f.element_id JOIN templates t"
				+ " ON t.id = e.template_id WHERE t.schema_id IN (" + schemas + ")");
		jdbc.update("DELETE e FROM template_elements e JOIN templates t ON t.id = e.template_id WHERE t.schema_id IN ("
				+ schemas + ")");
		jdbc.update("DELETE FROM templates WHERE schema_id IN (" + schemas + ")");
		jdbc.update("DELETE a FROM schema_attributes a JOIN schema_attribute_groups g ON g.id = a.group_id"
				+ " WHERE g.schema_id IN (" + schemas + ")");
		jdbc.update("DELETE FROM schema_attribute_groups WHERE schema_id IN (" + schemas + ")");
		jdbc.update("DELETE FROM `schemas` WHERE code LIKE 'CONTENT_IT_%'");
		jdbc.update("DELETE FROM admin_audit_logs WHERE target_name LIKE 'content-it-%' OR target_code LIKE 'IT_%'");
		jdbc.update("DELETE FROM user_authorities WHERE user_id IN (SELECT id FROM (" + users + ") u)");
		jdbc.update("DELETE FROM users WHERE email LIKE 'content-it-%'");
	}

	// ---- Dictionary -------------------------------------------------------------------------------------------

	@Test
	void editingAWordCopiesSharedDefinitionsAndKeepsOtherLanguages() throws Exception {
		long word = insertWord(tag + "-apple");
		long other = insertWord(tag + "-pome");
		long shared = insertDefinition("N", "vi", tag + " quả táo");
		long own = insertDefinition("V", "en", tag + " to apple");
		long dropped = insertDefinition("N", "en", tag + " a fruit");
		long sharedLink = link(word, shared, "an apple a day");
		long ownLink = link(word, own, null);
		link(word, dropped, null);
		link(other, shared, null);
		jdbc.update("INSERT INTO pronunciations (word_id, ipa, region, created_at) VALUES (?, '/ˈæp.əl/', 'US', NOW())", word);
		jdbc.update("INSERT INTO translations (word_id, lang_code, translation, created_at) VALUES (?, 'vi', 'táo', NOW())", word);
		jdbc.update("INSERT INTO translations (word_id, lang_code, translation, created_at) VALUES (?, 'fr', 'pomme', NOW())", word);

		JsonNode before = call(admin, "GET", "/api/words/" + word, null, 200).path("data");
		assertThat(before.path("version").asLong()).isZero();
		assertThat(before.path("meanings")).extracting(m -> m.path("id").asLong()).contains(sharedLink, ownLink);

		Map<String, Object> body = wordBody(0,
				List.of(Map.of("ipa", "/ˈæp.l̩/", "region", "UK")),
				List.of("quả táo", "táo tây"),
				List.of(meaning(sharedLink, "N", "vi", tag + " trái táo", "an apple a day"),
						meaning(ownLink, "V", "en", tag + " to apple (edited)", "they appled"),
						meaning(null, "A", "vi", tag + " thuộc về táo", null)));
		JsonNode after = call(admin, "PUT", "/api/admin/words/" + word, body, 200).path("data");

		assertThat(after.path("version").asLong()).isEqualTo(1);
		assertThat(after.path("pronunciations")).extracting(p -> p.path("ipa").asText()).containsExactly("/ˈæp.l̩/");
		assertThat(after.path("translations")).extracting(t -> t.path("translation").asText())
				.containsExactlyInAnyOrder("quả táo", "táo tây");
		assertThat(after.path("meanings")).extracting(m -> m.path("definition").asText()).containsExactlyInAnyOrder(
				tag + " trái táo", tag + " to apple (edited)", tag + " thuộc về táo");
		// The shared definition still says what it said for the other word; this word moved to a copy.
		assertThat(jdbc.queryForObject("SELECT definition FROM definitions WHERE id = ?", String.class, shared))
				.isEqualTo(tag + " quả táo");
		assertThat(jdbc.queryForObject("SELECT definition_id FROM word_definitions WHERE id = ?", Long.class, sharedLink))
				.isNotEqualTo(shared);
		// An unshared definition is edited in place; a dropped one is gone with its link.
		assertThat(jdbc.queryForObject("SELECT definition FROM definitions WHERE id = ?", String.class, own))
				.isEqualTo(tag + " to apple (edited)");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM definitions WHERE id = ?", Long.class, dropped)).isZero();
		assertThat(jdbc.queryForObject("SELECT translation FROM translations WHERE word_id = ? AND lang_code = 'fr'",
				String.class, word)).isEqualTo("pomme");
		assertThat(auditActions(String.valueOf(word), "WORD")).containsExactly("WORD_UPDATED");

		// Saving what is already stored changes nothing: same version, no audit row.
		List<Map<String, Object>> same = new ArrayList<>();
		for (JsonNode m : after.path("meanings")) {
			same.add(meaning(m.path("id").asLong(), m.path("pos").asText().equals("Danh từ") ? "N" : m.path("pos").asText().equals("Động từ") ? "V"
					: "A", m.path("definitionLang").asText(), m.path("definition").asText(), m.path("example").isNull() ? null : m.path("example").asText()));
		}
		JsonNode unchanged = call(admin, "PUT", "/api/admin/words/" + word, wordBody(1, List.of(Map.of("ipa", "/ˈæp.l̩/", "region", "UK")),
				List.of("quả táo", "táo tây"), same), 200).path("data");
		assertThat(unchanged.path("version").asLong()).isEqualTo(1);
		assertThat(auditActions(String.valueOf(word), "WORD")).containsExactly("WORD_UPDATED");

		// Same version again: someone else saved in between.
		JsonNode conflict = call(admin, "PUT", "/api/admin/words/" + word, body, 409);
		assertThat(conflict.path("error").asText()).isEqualTo("VERSION_CONFLICT");
		assertThat(conflict.path("data").path("currentVersion").asLong()).isEqualTo(1);
	}

	@Test
	void wordEditRejectsBadBodiesAndNonAdmins() throws Exception {
		long word = insertWord(tag + "-pear");
		long otherWord = insertWord(tag + "-plum");
		long foreignLink = link(otherWord, insertDefinition("N", "vi", tag + " mận"), null);

		Map<String, Object> dupIpa = wordBody(0, List.of(Map.of("ipa", "/peə/"), Map.of("ipa", "/peə/")), List.of(), List.of());
		assertThat(call(admin, "PUT", "/api/admin/words/" + word, dupIpa, 400).path("data").path("field").asText())
				.isEqualTo("pronunciations[1]");
		Map<String, Object> badPos = wordBody(0, List.of(), List.of(), List.of(meaning(null, "Danh từ", "vi", "lê", null)));
		assertThat(call(admin, "PUT", "/api/admin/words/" + word, badPos, 400).path("error").asText())
				.isEqualTo("VALIDATION_ERROR");
		Map<String, Object> foreign = wordBody(0, List.of(), List.of(), List.of(meaning(foreignLink, "N", "vi", "lê", null)));
		assertThat(call(admin, "PUT", "/api/admin/words/" + word, foreign, 400).path("data").path("field").asText())
				.isEqualTo("meanings[0].id");
		call(admin, "PUT", "/api/admin/words/" + word, wordBody(0, List.of(), List.of(), List.of(meaning(null, "N", "vi", " ", null))), 400);
		call(admin, "PUT", "/api/admin/words/999999999", wordBody(0, List.of(), List.of(), List.of()), 404);
		call(learnerToken, "PUT", "/api/admin/words/" + word, wordBody(0, List.of(), List.of(), List.of()), 403);
		call(learnerToken, "DELETE", "/api/admin/words/" + word, null, 403);
		assertThat(jdbc.queryForObject("SELECT version FROM words WHERE id = ?", Long.class, word)).isZero();
	}

	@Test
	void softDeleteHidesTheWordButKeepsSavedWordsAndCanBeUndone() throws Exception {
		String text = tag + "-kiwi";
		long word = insertWord(text);
		call(learnerToken, "PUT", "/api/words/my/" + word, null, 204);

		JsonNode deleted = call(admin, "DELETE", "/api/admin/words/" + word, Map.of("reason", "Từ nhập trùng với kiwi."), 200)
				.path("data");
		assertThat(deleted.path("deletedAt").isNull()).isFalse();
		call(admin, "GET", "/api/words/lookup?q=" + text, null, 404);
		assertThat(call(admin, "GET", "/api/words/suggest?q=" + text, null, 200).path("data").path("data")).isEmpty();
		assertThat(call(admin, "POST", "/api/words/lookup", Map.of("words", List.of(text)), 200).path("data")).isEmpty();
		assertThat(call(admin, "GET", "/api/admin/words/deleted?q=" + tag, null, 200).path("data").path("data"))
				.extracting(n -> n.path("id").asLong()).containsExactly(word);
		// A learner who saved it keeps it and can still open it; nobody can save it anew.
		assertThat(call(learnerToken, "GET", "/api/words/my", null, 200).path("data").path("data"))
				.extracting(n -> n.path("id").asLong()).contains(word);
		call(learnerToken, "GET", "/api/words/" + word, null, 200);
		call(learnerToken, "DELETE", "/api/words/my/" + word, null, 204);
		call(learnerToken, "PUT", "/api/words/my/" + word, null, 404);

		assertThat(call(admin, "DELETE", "/api/admin/words/" + word, null, 409).path("error").asText()).isEqualTo("WORD_DELETED");
		assertThat(call(admin, "PUT", "/api/admin/words/" + word, wordBody(1, List.of(), List.of(), List.of()), 409)
				.path("error").asText()).isEqualTo("WORD_DELETED");
		assertThat(call(admin, "DELETE", "/api/admin/words/" + word, Map.of("reason", "ngắn"), 400).path("error").asText())
				.isEqualTo("AUDIT_REASON_INVALID");

		JsonNode restored = call(admin, "POST", "/api/admin/words/" + word + "/restore", null, 200).path("data");
		assertThat(restored.path("deletedAt").isNull()).isTrue();
		assertThat(restored.path("version").asLong()).isEqualTo(2);
		call(admin, "GET", "/api/words/lookup?q=" + text, null, 200);
		assertThat(call(admin, "POST", "/api/admin/words/" + word + "/restore", null, 409).path("error").asText())
				.isEqualTo("WORD_NOT_DELETED");
		assertThat(auditActions(String.valueOf(word), "WORD")).containsExactly("WORD_DELETED", "WORD_RESTORED");
	}

	// ---- Topics -----------------------------------------------------------------------------------------------

	@Test
	void editsASystemTopicButNotALearnersOwn() throws Exception {
		long collection = systemCollection();
		long topic = topic(collection, null, tag + "-Animals");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", tag + "-Wild animals");
		body.put("translation", "Động vật hoang dã");
		body.put("description", "  ");
		body.put("descriptionTranslation", null);
		JsonNode updated = call(admin, "PUT", "/api/admin/topics/" + topic, body, 200).path("data");
		assertThat(updated.path("name").asText()).isEqualTo(tag + "-Wild animals");
		assertThat(updated.path("translation").asText()).isEqualTo("Động vật hoang dã");
		assertThat(updated.path("description").isNull()).isTrue();
		assertThat(call(admin, "GET", "/api/collections/" + collection + "/topics", null, 200).path("data").path("data"))
				.extracting(n -> n.path("name").asText()).containsExactly(tag + "-Wild animals");
		assertThat(auditActions(String.valueOf(topic), "TOPIC")).containsExactly("TOPIC_CREATED", "TOPIC_UPDATED");

		call(admin, "PUT", "/api/admin/topics/" + topic, Map.of("name", "x"), 400);
		call(learnerToken, "PUT", "/api/admin/topics/" + topic, body, 403);
		long own = call(learnerToken, "POST", "/api/collections", Map.of("name", tag + "-mine"), 200).path("data").path("id").asLong();
		long ownTopic = topic(own, null, tag + "-mine topic", learnerToken);
		call(admin, "PUT", "/api/admin/topics/" + ownTopic, body, 404);
		call(admin, "DELETE", "/api/admin/topics/" + ownTopic, Map.of("reason", REASON), 404);
	}

	@Test
	void deletesAnUnstudiedTopicOnlyWithAReasonAndWithoutSubtopics() throws Exception {
		long collection = systemCollection();
		long root = topic(collection, null, tag + "-Root");
		long child = topic(collection, root, tag + "-Child");
		long item = addItem(child, "content-it-lion", "sư tử");
		addItem(child, "content-it-tiger", "hổ");

		assertThat(call(admin, "DELETE", "/api/admin/topics/" + child, null, 400).path("error").asText())
				.isEqualTo("AUDIT_REASON_REQUIRED");
		JsonNode hasChildren = call(admin, "DELETE", "/api/admin/topics/" + root, Map.of("reason", REASON), 409);
		assertThat(hasChildren.path("error").asText()).isEqualTo("TOPIC_HAS_CHILDREN");
		assertThat(hasChildren.path("data").path("children").asLong()).isEqualTo(1);

		call(admin, "DELETE", "/api/admin/topics/" + child, Map.of("reason", REASON), 204);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id = ?", Long.class, child)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_items WHERE topic_id = ?", Long.class, child)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_item_attribute_groups WHERE topic_item_id = ?", Long.class,
				item)).isZero();
		assertThat(auditActions(String.valueOf(child), "TOPIC")).endsWith("TOPIC_DELETED");
		assertThat(jdbc.queryForObject("SELECT reason FROM admin_audit_logs WHERE target_id = ? AND action = 'TOPIC_DELETED'",
				String.class, String.valueOf(child))).isEqualTo(REASON);
		call(admin, "DELETE", "/api/admin/topics/" + root, Map.of("reason", REASON), 204);
		call(admin, "DELETE", "/api/admin/topics/" + root, Map.of("reason", REASON), 404);
	}

	@Test
	void aStudiedTopicIsNotDeleted() throws Exception {
		long topic = topic(systemCollection(), null, tag + "-Studied");
		long item = addItem(topic, "content-it-owl", "cú");
		studied(item);

		JsonNode refused = call(admin, "DELETE", "/api/admin/topics/" + topic, Map.of("reason", REASON), 409);
		assertThat(refused.path("error").asText()).isEqualTo("TOPIC_HAS_LEARNING_DATA");
		assertThat(refused.path("data").path("learners").asLong()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_items WHERE topic_id = ?", Long.class, topic)).isOne();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_records WHERE topic_item_id = ?", Long.class, item)).isOne();
		assertThat(auditActions(String.valueOf(topic), "TOPIC")).doesNotContain("TOPIC_DELETED");
	}

	@Test
	void hidingATopicKeepsReviewsButStopsBrowsingAndNewCards() throws Exception {
		long collection = systemCollection();
		long root = topic(collection, null, tag + "-Hide root");
		long child = topic(collection, root, tag + "-Hide child");
		long studiedItem = addItem(child, "content-it-mole", "chuột chũi");
		long newItem = addItem(child, "content-it-newt", "sa giông");
		dueReview(studiedItem);

		call(admin, "POST", "/api/admin/topics/" + root + "/hide", null, 400); // reason required
		JsonNode hidden = call(admin, "POST", "/api/admin/topics/" + root + "/hide",
				Map.of("reason", "QA: chủ đề trùng, ẩn để dọn danh mục."), 200).path("data");
		assertThat(hidden.path("subtopics").asInt()).isEqualTo(1);
		assertThat(hidden.path("learners").asInt()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id IN (?, ?) AND hidden_at IS NOT NULL", Long.class,
				root, child)).isEqualTo(2);

		// Learners: gone from the catalog, cannot open it or start a Quiz on it...
		assertThat(call(learnerToken, "GET", "/api/collections/" + collection + "/topics", null, 200).path("data").path("data"))
				.isEmpty();
		call(learnerToken, "GET", "/api/topics/" + child + "?page=0&size=50", null, 404);
		// ...but the card they studied is still due for review, and no new card comes from the hidden topic.
		JsonNode queue = call(learnerToken, "GET", "/api/collections/" + collection + "/study/queue", null, 200).path("data");
		assertThat(queue.path("cards")).extracting(c -> c.path("item").path("topicItemId").asLong())
				.contains(studiedItem).doesNotContain(newItem);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_records WHERE topic_item_id = ?", Long.class, studiedItem))
				.isOne();
		// The study screen draws that card with the topic schema and grades it (found on the emulator 2026-11-26:
		// a 404 here left the card loading forever).
		call(learnerToken, "GET", "/api/topics/" + child + "/schema", null, 200);
		call(learnerToken, "POST", "/api/collections/" + collection + "/study/items/" + studiedItem + "/reviews",
				Map.of("rating", "GOOD"), 200);

		// Admin: listed under "Đã ẩn" with who, why and how many learners; a subtopic waits for its parent.
		JsonNode list = call(admin, "GET", "/api/admin/topics/hidden?collectionId=" + collection, null, 200).path("data");
		assertThat(list).extracting(n -> n.path("id").asLong()).containsExactlyInAnyOrder(root, child);
		assertThat(list.get(0).path("reason").asText()).isEqualTo("QA: chủ đề trùng, ẩn để dọn danh mục.");
		assertThat(call(admin, "POST", "/api/admin/topics/" + child + "/restore", null, 409).path("error").asText())
				.isEqualTo("TOPIC_PARENT_HIDDEN");
		assertThat(call(admin, "POST", "/api/admin/topics/" + root + "/hide", Map.of("reason", "QA: ẩn lần hai cho chắc."), 409)
				.path("error").asText()).isEqualTo("TOPIC_ALREADY_HIDDEN");
		call(learnerToken, "POST", "/api/admin/topics/" + root + "/restore", null, 403);

		call(admin, "POST", "/api/admin/topics/" + root + "/restore", null, 200);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id IN (?, ?) AND hidden_at IS NULL", Long.class,
				root, child)).isEqualTo(2);
		assertThat(call(learnerToken, "GET", "/api/collections/" + collection + "/topics", null, 200).path("data").path("data"))
				.extracting(n -> n.path("id").asLong()).containsExactly(root);
		call(learnerToken, "GET", "/api/topics/" + child + "?page=0&size=50", null, 200);
		assertThat(auditActions(String.valueOf(root), "TOPIC")).containsSubsequence("TOPIC_HIDDEN", "TOPIC_RESTORED");
	}

	@Test
	void aSubtopicHiddenOnItsOwnComesBackAloneAndAQuizCannotStartOnAHiddenTopic() throws Exception {
		long collection = systemCollection();
		long root = topic(collection, null, tag + "-Keep root");
		long child = topic(collection, root, tag + "-Own child");
		addItem(child, "content-it-yak", "bò Tây Tạng");
		call(admin, "POST", "/api/admin/topics/" + child + "/hide", Map.of("reason", "QA: ẩn riêng chủ đề con."), 200);
		assertThat(call(learnerToken, "GET", "/api/collections/" + collection + "/topics", null, 200).path("data").path("data")
				.get(0).path("hasChildren").asBoolean()).isFalse();
		JsonNode quiz = call(learnerToken, "POST", "/api/me/quizzes", Map.of("topicId", child, "mode", "MCQ", "direction",
				"EN_VI", "requestedCount", 5), 404, Map.of("Idempotency-Key", tag + "-quiz"));
		assertThat(quiz.path("statusCode").asInt()).isEqualTo(404);
		call(admin, "POST", "/api/admin/topics/" + child + "/restore", null, 200);
		assertThat(jdbc.queryForObject("SELECT hidden_at IS NULL FROM topics WHERE id = ?", Boolean.class, root)).isTrue();
	}

	// ---- Topic items ------------------------------------------------------------------------------------------

	@Test
	void editingASystemItemKeepsItsIdAndTheLearnersCard() throws Exception {
		long topic = topic(systemCollection(), null, tag + "-Edit");
		long item = addItem(topic, "content-it-cat", "mèo");
		studied(item);

		Map<String, Object> body = Map.of("main", Map.of("word", "content-it-cat", "translation", "con mèo", "phonetic", "/kæt/"),
				"examples", List.of(Map.of("content", "The cat sleeps.")));
		call(admin, "PUT", "/api/topics/" + topic + "/items/" + item, body, 200);

		JsonNode items = call(admin, "GET", "/api/topics/" + topic + "?page=0&size=50", null, 200).path("data").path("data");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).path("topicItemId").asLong()).isEqualTo(item);
		assertThat(items.get(0).path("main").path("translation").asText()).isEqualTo("con mèo");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_records WHERE topic_item_id = ?", Long.class, item)).isOne();
		assertThat(auditActions(String.valueOf(topic), "TOPIC")).contains("TOPIC_ITEM_UPDATED");
		String changes = jdbc.queryForObject("SELECT changes FROM admin_audit_logs WHERE target_id = ? AND action = "
				+ "'TOPIC_ITEM_UPDATED'", String.class, String.valueOf(topic));
		assertThat(changes).contains("con mèo").contains("/kæt/").doesNotContain("main[1].word");
	}

	@Test
	void aStudiedSystemItemIsNotDeletedButAnUnstudiedOneIs() throws Exception {
		long topic = topic(systemCollection(), null, tag + "-Items");
		long studied = addItem(topic, "content-it-dog", "chó");
		long fresh = addItem(topic, "content-it-fox", "cáo");
		studied(studied);

		JsonNode refused = call(admin, "DELETE", "/api/topics/" + topic + "/items/" + studied, null, 409);
		assertThat(refused.path("error").asText()).isEqualTo("TOPIC_ITEM_HAS_LEARNING_DATA");
		assertThat(refused.path("data").path("learners").asLong()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_records WHERE topic_item_id = ?", Long.class, studied)).isOne();

		call(admin, "DELETE", "/api/topics/" + topic + "/items/" + fresh, null, 204);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_items WHERE id = ?", Long.class, fresh)).isZero();
		assertThat(auditActions(String.valueOf(topic), "TOPIC")).contains("TOPIC_ITEM_DELETED");
		call(learnerToken, "DELETE", "/api/topics/" + topic + "/items/" + studied, null, 403);
	}

	@Test
	void aLearnerStillDeletesAStudiedWordFromTheirOwnTopic() throws Exception {
		long own = call(learnerToken, "POST", "/api/collections", Map.of("name", tag + "-own"), 200).path("data").path("id").asLong();
		long ownTopic = topic(own, null, tag + "-own topic", learnerToken);
		long item = addItem(ownTopic, "content-it-bee", "ong", learnerToken);
		studied(item);
		call(learnerToken, "DELETE", "/api/topics/" + ownTopic + "/items/" + item, null, 204);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fsrs_records WHERE topic_item_id = ?", Long.class, item)).isZero();
	}

	// ---- Card templates ---------------------------------------------------------------------------------------

	@Test
	void editsATemplateKeepingHiddenBindingsAndQuiz() throws Exception {
		JsonNode before = template(schema.standardId());
		assertThat(before.path("version").asLong()).isZero();
		List<Map<String, Object>> elements = new ArrayList<>();
		for (JsonNode e : before.path("elements")) {
			elements.add(json.convertValue(e, Map.class));
		}
		// Rename, move the phonetic line before the word, add the example on the back.
		Map<String, Object> phonetic = elements.remove(2);
		elements.add(0, phonetic);
		elements.add(Map.of("type", "FIELD", "side", "BACK", "field", Map.of("schemaAttributeId", schema.example(),
				"semanticRole", "EXAMPLE_SENTENCE", "fieldLabel", "Ví dụ", "hideIfEmpty", true, "fontSize", 14,
				"alignment", "LEFT", "color", "#334155")));
		Map<String, Object> body = Map.of("version", 0, "name", "IT Thẻ chuẩn (sửa)", "elements", elements);

		JsonNode saved = call(admin, "PUT", "/api/admin/templates/" + schema.standardId(), body, 200).path("data");
		assertThat(saved.path("version").asLong()).isEqualTo(1);
		assertThat(saved.path("name").asText()).isEqualTo("IT Thẻ chuẩn (sửa)");
		assertThat(saved.path("elements")).extracting(e -> e.path("position").asInt()).containsExactly(0, 1, 2, 3, 4);
		assertThat(saved.path("elements").get(0).path("field").path("schemaAttributeId").asLong()).isEqualTo(schema.phonetic());
		assertThat(saved.path("elements").get(0).path("id").asLong())
				.isEqualTo(before.path("elements").get(2).path("id").asLong());
		// The two server-only bindings are not in the DTO and survive the edit, numbered after the visible elements.
		assertThat(jdbc.queryForList("SELECT e.position FROM template_fields f JOIN template_elements e ON e.id = f.element_id"
				+ " WHERE e.template_id = ? AND f.hidden = TRUE ORDER BY e.position", Integer.class, schema.standardId()))
				.containsExactly(5, 6);
		assertThat(template(schema.standardId()).path("name").asText()).isEqualTo("IT Thẻ chuẩn (sửa)");
		assertThat(auditActions(String.valueOf(schema.standardId()), "CARD_TEMPLATE")).containsExactly("CARD_TEMPLATE_UPDATED");

		assertThat(call(admin, "PUT", "/api/admin/templates/" + schema.standardId(), body, 409).path("error").asText())
				.isEqualTo("VERSION_CONFLICT");
	}

	@Test
	void templateSaveRulesAreCheckedOnTheServer() throws Exception {
		long id = schema.standardId();
		List<Map<String, Object>> elements = new ArrayList<>();
		for (JsonNode e : template(id).path("elements")) {
			elements.add(json.convertValue(e, Map.class));
		}
		// No TARGET_WORD role left: Quiz cannot read the word.
		List<Map<String, Object>> noWord = new ArrayList<>(elements);
		Map<String, Object> word = new LinkedHashMap<>(noWord.get(1));
		Map<String, Object> wordField = new LinkedHashMap<>((Map<String, Object>) word.get("field"));
		wordField.put("semanticRole", null);
		word.put("field", wordField);
		noWord.set(1, word);
		JsonNode unbound = call(admin, "PUT", "/api/admin/templates/" + id, Map.of("version", 0, "name", "x", "elements", noWord), 422);
		assertThat(unbound.path("error").asText()).isEqualTo("TEMPLATE_BINDING_INVALID");
		assertThat(unbound.path("data").path("role").asText()).isEqualTo("TARGET_WORD");
		assertThat(unbound.path("data").path("kind").asText()).isEqualTo("MISSING");

		List<Map<String, Object>> frontOnly = elements.stream()
				.filter(e -> !"BACK".equals(e.get("side"))).toList();
		call(admin, "PUT", "/api/admin/templates/" + id, Map.of("version", 0, "name", "x", "elements", frontOnly), 400);
		List<Map<String, Object>> foreign = new ArrayList<>(elements);
		foreign.add(Map.of("type", "FIELD", "side", "BACK", "field", Map.of("schemaAttributeId", 999999999L)));
		assertThat(call(admin, "PUT", "/api/admin/templates/" + id, Map.of("version", 0, "name", "x", "elements", foreign), 400)
				.path("data").path("field").asText()).isEqualTo("elements[" + elements.size() + "].field.schemaAttributeId");
		call(admin, "PUT", "/api/admin/templates/" + id, Map.of("version", 0, "name", "x", "elements", elements.stream()
				.map(e -> { Map<String, Object> c = new LinkedHashMap<>(e); c.put("id", 999999999L); return c; }).toList()), 400);
		call(learnerToken, "PUT", "/api/admin/templates/" + id, Map.of("version", 0, "name", "x", "elements", elements), 403);
		assertThat(template(id).path("version").asLong()).isZero();
	}

	@Test
	void makingATemplateTheDefaultMovesTheFlagInOneStep() throws Exception {
		JsonNode made = call(admin, "POST", "/api/admin/templates/" + schema.listeningId() + "/default", Map.of("version", 0), 200)
				.path("data");
		assertThat(made.path("isDefault").asBoolean()).isTrue();
		assertThat(made.path("version").asLong()).isEqualTo(1);
		assertThat(template(schema.standardId()).path("isDefault").asBoolean()).isFalse();
		assertThat(template(schema.standardId()).path("version").asLong()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM templates WHERE schema_id = ? AND is_default = TRUE", Long.class,
				schema.schemaId())).isOne();
		assertThat(auditActions(String.valueOf(schema.listeningId()), "CARD_TEMPLATE"))
				.containsExactly("CARD_TEMPLATE_DEFAULT_CHANGED");
		assertThat(call(admin, "POST", "/api/admin/templates/" + schema.standardId() + "/default", Map.of("version", 0), 409)
				.path("error").asText()).isEqualTo("VERSION_CONFLICT");
		call(learnerToken, "POST", "/api/admin/templates/" + schema.standardId() + "/default", Map.of("version", 1), 403);

		// A template with no visible field would blank every learner's flashcard: refused, nothing moves.
		JsonNode empty = call(admin, "POST", "/api/admin/templates/" + schema.hiddenOnlyId() + "/default", Map.of("version", 0), 400);
		assertThat(empty.path("data").path("field").asText()).isEqualTo("elements");
		assertThat(jdbc.queryForObject("SELECT id FROM templates WHERE schema_id = ? AND is_default = TRUE", Long.class,
				schema.schemaId())).isEqualTo(schema.listeningId());
	}

	// ---- helpers ----------------------------------------------------------------------------------------------

	private Fixture buildSchema() {
		Schema s = Schema.builder().code("CONTENT_IT_" + tag.substring(tag.length() - 8).toUpperCase()).isSystem(true)
				.name(tag + " schema").build();
		SchemaAttributeGroup main = SchemaAttributeGroup.builder().schema(s).name("main").label("Main").position((short) 0).build();
		SchemaAttributeGroup examples = SchemaAttributeGroup.builder().schema(s).name("examples").label("Examples")
				.multiple(true).position((short) 1).build();
		SchemaAttribute word = attr(main, "word", 0, true);
		SchemaAttribute translation = attr(main, "translation", 1, false);
		SchemaAttribute phonetic = attr(main, "phonetic", 2, false);
		SchemaAttribute example = attr(examples, "content", 0, false);
		s.getAttributeGroups().add(main);
		s.getAttributeGroups().add(examples);

		Template standard = Template.builder().schema(s).code("IT_STANDARD").name("IT Thẻ chuẩn").isDefault(true).build();
		element(standard, 0, CardSide.FRONT, TemplateElementType.SECTION_BREAK, null, null, false);
		element(standard, 1, CardSide.FRONT, TemplateElementType.FIELD, word, SemanticRole.TARGET_WORD, false);
		element(standard, 2, CardSide.FRONT, TemplateElementType.FIELD, phonetic, null, false);
		element(standard, 3, CardSide.BACK, TemplateElementType.FIELD, translation, null, false);
		element(standard, 4, CardSide.BACK, TemplateElementType.FIELD, translation, SemanticRole.NATIVE_TRANSLATION, true);
		element(standard, 5, CardSide.BACK, TemplateElementType.FIELD, example, SemanticRole.EXAMPLE_SENTENCE, true);
		Template listening = Template.builder().schema(s).code("IT_LISTENING").name("IT Luyện nghe").isDefault(false).build();
		element(listening, 0, CardSide.FRONT, TemplateElementType.FIELD, word, SemanticRole.AUDIO, false);
		element(listening, 1, CardSide.BACK, TemplateElementType.FIELD, word, SemanticRole.TARGET_WORD, false);
		element(listening, 2, CardSide.BACK, TemplateElementType.FIELD, translation, SemanticRole.NATIVE_TRANSLATION, false);
		element(listening, 3, CardSide.BACK, TemplateElementType.FIELD, example, SemanticRole.EXAMPLE_SENTENCE, false);
		// Like LISTENING / REVERSE on prod 2026-11-09: only server-side Quiz bindings, nothing a card would show.
		Template hiddenOnly = Template.builder().schema(s).code("IT_HIDDEN_ONLY").name("IT Chỉ binding ẩn").isDefault(false).build();
		element(hiddenOnly, 0, CardSide.BACK, TemplateElementType.FIELD, translation, SemanticRole.NATIVE_TRANSLATION, true);
		element(hiddenOnly, 1, CardSide.BACK, TemplateElementType.FIELD, example, SemanticRole.EXAMPLE_SENTENCE, true);
		s.getTemplates().add(standard);
		s.getTemplates().add(listening);
		s.getTemplates().add(hiddenOnly);
		Schema saved = schemaRepository.saveAndFlush(s);
		return new Fixture(saved.getId(), word.getId(), translation.getId(), phonetic.getId(), example.getId(),
				standard.getId(), listening.getId(), hiddenOnly.getId());
	}

	private static SchemaAttribute attr(SchemaAttributeGroup group, String name, int position, boolean required) {
		SchemaAttribute a = SchemaAttribute.builder().group(group).name(name).label(name).dataType(DataType.TEXT)
				.required(required).position((short) position).build();
		group.getAttributes().add(a);
		return a;
	}

	private static void element(Template t, int position, CardSide side, TemplateElementType type, SchemaAttribute attribute,
			SemanticRole role, boolean hidden) {
		TemplateElement e = TemplateElement.builder().position(position).side(side).type(type)
				.sectionLabel(type == TemplateElementType.SECTION_BREAK ? "Phần chính" : null).repeatable(false).build();
		if (type == TemplateElementType.FIELD) {
			e.setField(TemplateField.builder().element(e).schemaAttribute(attribute).semanticRole(role)
					.fieldLabel(attribute.getName()).hidden(hidden).build());
		}
		t.addElement(e);
	}

	private JsonNode template(long id) throws Exception {
		for (JsonNode s : call(admin, "GET", "/api/schemas", null, 200).path("data")) {
			for (JsonNode t : s.path("templates")) {
				if (t.path("id").asLong() == id) {
					return t;
				}
			}
		}
		throw new AssertionError("template " + id + " not in GET /api/schemas");
	}

	private long systemCollection() throws Exception {
		return call(admin, "POST", "/api/collections", Map.of("name", tag + "-system", "type", "SYSTEM"), 200)
				.path("data").path("id").asLong();
	}

	private long topic(long collection, Long parent, String name) throws Exception {
		return topic(collection, parent, name, admin);
	}

	private long topic(long collection, Long parent, String name, String token) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("schemaId", schema.schemaId());
		body.put("parentId", parent);
		return call(token, "POST", "/api/collections/" + collection + "/topics", body, 200).path("data").path("id").asLong();
	}

	private long addItem(long topic, String word, String translation) throws Exception {
		return addItem(topic, word, translation, admin);
	}

	private long addItem(long topic, String word, String translation, String token) throws Exception {
		return call(token, "POST", "/api/topics/" + topic + "/items",
				Map.of("main", Map.of("word", word, "translation", translation)), 201).path("data").path("topicItemId").asLong();
	}

	/** A REVIEW card due yesterday (FSRS times are UTC). */
	private void dueReview(long item) {
		jdbc.update("INSERT INTO fsrs_records (user_id, topic_item_id, state, due, stability, difficulty, reps, lapses,"
				+ " last_review, created_at, updated_at) VALUES (?, ?, 2, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), 3.5, 5.0, 2, 0,"
				+ " DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), NOW(), NOW())", learner.getId(), item);
	}

	private void studied(long item) {
		jdbc.update("INSERT INTO fsrs_records (user_id, topic_item_id, state, due, stability, difficulty, reps, lapses,"
				+ " created_at, updated_at) VALUES (?, ?, 2, NOW(), 3.5, 5.0, 1, 0, NOW(), NOW())", learner.getId(), item);
	}

	private long insertWord(String word) {
		return insert("INSERT INTO words (word, lang_code, created_at, version) VALUES (?, 'en', NOW(), 0)", word);
	}

	private long insertDefinition(String pos, String lang, String text) {
		return insert("INSERT INTO definitions (definition, pos, definition_lang, created_at) VALUES (?, ?, ?, NOW())", text,
				pos, lang);
	}

	private long link(long word, long definition, String example) {
		return insert("INSERT INTO word_definitions (word_id, definition_id, example, created_at) VALUES (?, ?, ?, NOW())",
				word, definition, example);
	}

	private long insert(String sql, Object... args) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) {
				ps.setObject(i + 1, args[i]);
			}
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static Map<String, Object> wordBody(long version, List<Map<String, Object>> ipa, List<String> vi,
			List<Map<String, Object>> meanings) {
		return Map.of("version", version, "pronunciations", ipa, "translations", vi, "meanings", meanings);
	}

	private static Map<String, Object> meaning(Long id, String pos, String lang, String definition, String example) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", id);
		m.put("pos", pos);
		m.put("definitionLang", lang);
		m.put("definition", definition);
		m.put("example", example);
		return m;
	}

	private List<String> auditActions(String targetId, String targetType) {
		return jdbc.queryForList("SELECT action FROM admin_audit_logs WHERE target_id = ? AND target_type = ? ORDER BY id",
				String.class, targetId, targetType);
	}

	private JsonNode call(String token, String method, String path, Object body, int expected) throws Exception {
		return call(token, method, path, body, expected, Map.of());
	}

	private JsonNode call(String token, String method, String path, Object body, int expected, Map<String, String> headers)
			throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token);
		headers.forEach(req::header);
		HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(resp.statusCode()).withFailMessage("%s %s → %s %s", method, path, resp.statusCode(), resp.body())
				.isEqualTo(expected);
		return resp.body().isEmpty() ? json.nullNode() : json.readTree(resp.body());
	}

	private User saveUser(String who) {
		return userRepository.save(User.builder().email(tag + "-" + who + "@snapvocab.local")
				.password(passwordEncoder.encode("Secret#123")).firstName(who).lastName("Tester").activated(true).coin(0L)
				.build());
	}

	private String bearer(User u, String authorities) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuedAt(Instant.now()).expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
				.subject(u.getEmail()).claim(SecurityUtils.AUTHORITIES_CLAIM, authorities)
				.claim(SecurityUtils.USER_ID_CLAIM, u.getId()).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build(), claims))
				.getTokenValue();
	}
}
