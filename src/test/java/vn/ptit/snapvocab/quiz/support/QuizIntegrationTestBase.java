package vn.ptit.snapvocab.quiz.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.ptit.snapvocab.config.DataInitializer;
import vn.ptit.snapvocab.config.SemanticBindingMigration;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.TopicItem;
import vn.ptit.snapvocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateRepository;
import vn.ptit.snapvocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.repository.TopicRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver;
import vn.ptit.snapvocab.quiz.service.QuizGenerator;
import vn.ptit.snapvocab.quiz.service.QuizService;
import vn.ptit.snapvocab.service.TopicService;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;
import vn.ptit.snapvocab.quiz.repository.QuizMatchingAttemptRepository;
import vn.ptit.snapvocab.quiz.repository.QuizQuestionRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.xp.service.XpService;

@LocalMySqlJpaTest
@Import({ DataInitializer.class, SemanticBindingMigration.class, QuizService.class, QuizContentResolver.class,
		QuizGenerator.class, XpService.class, QuizIntegrationTestBase.Beans.class })
public abstract class QuizIntegrationTestBase {

	@TestConfiguration
	static class Beans {
		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder(4);
		}
	}

	@MockitoBean
	protected TopicService topicService;

	@Autowired
	protected DataSource dataSource;
	@Autowired
	protected PlatformTransactionManager transactionManager;
	@Autowired
	protected UserRepository userRepository;
	@Autowired
	protected CollectionRepository collectionRepository;
	@Autowired
	protected TopicRepository topicRepository;
	@Autowired
	protected TopicItemRepository topicItemRepository;
	@Autowired
	protected SchemaRepository schemaRepository;
	@Autowired
	protected SchemaAttributeGroupRepository groupRepository;
	@Autowired
	protected TemplateRepository templateRepository;

	protected JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	protected TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
		JdbcTemplate jdbc = jdbc();
		for (String table : List.of("quiz_matching_attempts", "quiz_question_options", "quiz_matching_tiles",
				"quiz_questions", "quizzes", "topic_item_attribute_values", "topic_item_attribute_groups",
				"topic_items", "topics", "collections")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("DELETE FROM experience_logs WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'it-%')");
		jdbc.update("DELETE FROM booster_activations WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'it-%')");
		jdbc.update("DELETE FROM users WHERE email LIKE 'it-%'");
	}

	protected User newUser() {
		return userRepository.save(User.builder()
				.email("it-" + UUID.randomUUID() + "@test.local")
				.password("x".repeat(60))
				.firstName("IT")
				.activated(true)
				.build());
	}

	protected static void authenticate(User user) {
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(user.getEmail())
				.claim("userId", user.getId()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	protected Schema defaultSchema() {
		return schemaRepository.findByCode("DEFAULT_ENGLISH").orElseThrow();
	}

	protected Map<String, SchemaAttribute> defaultAttributes() {
		return groupRepository.findBySchemaIdWithAttributes(defaultSchema().getId()).stream()
				.flatMap(g -> g.getAttributes().stream())
				.collect(Collectors.toMap(a -> a.getGroup().getName() + "." + a.getName(), a -> a));
	}

	protected Topic newTopic(String templateCode) {
		Schema schema = defaultSchema();
		Collection collection = collectionRepository.save(Collection.builder()
				.name("IT collection").type(CollectionType.SYSTEM).build());
		return topicRepository.save(Topic.builder()
				.collection(collection)
				.name("IT topic")
				.schema(schema)
				.activeTemplate(templateRepository.findBySchemaIdAndCode(schema.getId(), templateCode).orElseThrow())
				.build());
	}

	protected void addItem(Topic topic, String word, String translation, String... examples) {
		Map<String, SchemaAttribute> attrs = defaultAttributes();
		Map<String, SchemaAttributeGroup> groups = attrs.values().stream()
				.map(SchemaAttribute::getGroup)
				.collect(Collectors.toMap(SchemaAttributeGroup::getName, g -> g, (a, b) -> a));
		TopicItem item = TopicItem.builder().topic(topic).build();

		TopicItemAttributeGroup main = TopicItemAttributeGroup.builder().item(item)
				.groupDefinition(groups.get("main")).position((short) 1).build();
		if (word != null) {
			main.getValues().add(value(main, attrs.get("main.word"), word));
		}
		if (translation != null) {
			main.getValues().add(value(main, attrs.get("main.translation"), translation));
		}
		main.getValues().add(value(main, attrs.get("main.definition_translation"), "định nghĩa của " + word));
		item.getGroupInstances().add(main);

		TopicItemAttributeGroup meaning = TopicItemAttributeGroup.builder().item(item)
				.groupDefinition(groups.get("meaning")).position((short) 1).build();
		meaning.getValues().add(value(meaning, attrs.get("meaning.meaning"), "English definition of " + word));
		item.getGroupInstances().add(meaning);

		short position = 1;
		for (String example : examples) {
			TopicItemAttributeGroup ex = TopicItemAttributeGroup.builder().item(item)
					.groupDefinition(groups.get("examples")).position(position++).build();
			ex.getValues().add(value(ex, attrs.get("examples.content"), example));
			ex.getValues().add(value(ex, attrs.get("examples.translation"), "câu dịch"));
			item.getGroupInstances().add(ex);
		}
		topicItemRepository.save(item);
	}

	private static TopicItemAttributeValue value(TopicItemAttributeGroup group, SchemaAttribute attr, String v) {
		return TopicItemAttributeValue.builder().groupInstance(group).attribute(attr).value(v).build();
	}

	protected Topic topicWithVocabulary(String templateCode, int size) {
		Topic topic = newTopic(templateCode);
		for (int i = 1; i <= size; i++) {
			addItem(topic, "word" + i, "nghĩa " + i, "I say word" + i + " every day.");
		}
		return topic;
	}
}
