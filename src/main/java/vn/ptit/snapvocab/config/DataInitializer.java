package vn.ptit.snapvocab.config;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Authority;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateElement;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.repository.AuthorityRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;

@Component
public class DataInitializer implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

	private final AuthorityRepository authorityRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SchemaRepository schemaRepository;
	private final DataSource dataSource;

	public DataInitializer(AuthorityRepository authorityRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			SchemaRepository schemaRepository,
			DataSource dataSource) {
		this.authorityRepository = authorityRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.schemaRepository = schemaRepository;
		this.dataSource = dataSource;
	}

	@Override
	@Transactional
	public void run(String... args) {
		migrateDatabaseIfNeeded();

		createAuthorityIfNotFound(AuthoritiesConstants.ADMIN);
		createAuthorityIfNotFound(AuthoritiesConstants.USER);
		createAuthorityIfNotFound(AuthoritiesConstants.ANONYMOUS);

		createAdminUserIfNotFound();
		createDefaultEnglishSchemaIfNotFound();
		consolidateAndCleanSchemas();
	}

	private void migrateDatabaseIfNeeded() {
		try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

			// 1. Make sure topic_id in templates is nullable
			try {
				stmt.execute("ALTER TABLE templates MODIFY COLUMN topic_id BIGINT NULL");
			} catch (Exception e) {
				LOG.debug("Could not modify topic_id on templates: {}", e.getMessage());
			}

			// 2. Add active_template_id in topics if missing
			try {
				stmt.execute("ALTER TABLE topics ADD COLUMN active_template_id BIGINT NULL");
			} catch (Exception e) {
				LOG.debug("active_template_id column already exists or error: {}", e.getMessage());
			}

			// 3. Align topic_schemas.schema_id with DEFAULT_ENGLISH schema
			try {
				stmt.execute("""
						UPDATE `topic_schemas` ts
						SET ts.schema_id = (SELECT id FROM `schemas` WHERE code = 'DEFAULT_ENGLISH' LIMIT 1)
						WHERE ts.schema_id = 0 OR ts.schema_id IS NULL OR ts.schema_id NOT IN (SELECT id FROM `schemas`)
						""");
			} catch (Exception e) {
				LOG.debug("topic_schemas schema_id update ignored: {}", e.getMessage());
			}

			// 4. Update schema_attribute_groups orphan schema_id if any and point constraint to schemas
			try {
				stmt.execute("""
						UPDATE `schema_attribute_groups` sag
						LEFT JOIN `schemas` s ON sag.schema_id = s.id
						SET sag.schema_id = (SELECT MIN(id) FROM `schemas`)
						WHERE s.id IS NULL
						""");
			} catch (Exception e) {
				LOG.debug("Orphan sag update ignored: {}", e.getMessage());
			}

			try {
				stmt.execute("ALTER TABLE `schema_attribute_groups` DROP FOREIGN KEY `fk_schema_attribute_groups_schema`");
			} catch (Exception ignored) {}

			try {
				stmt.execute("ALTER TABLE `schema_attribute_groups` ADD CONSTRAINT `fk_schema_attribute_groups_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE CASCADE");
			} catch (Exception ignored) {}

			// 5. Update templates schema_id if missing
			try {
				stmt.execute("""
						UPDATE `templates` t
						INNER JOIN `topic_schemas` ts ON t.topic_id = ts.topic_id
						SET t.schema_id = ts.schema_id
						WHERE t.schema_id IS NULL
						""");
			} catch (Exception e) {
				LOG.debug("Template schema_id update ignored: {}", e.getMessage());
			}

			// 6. Ensure user_id column exists on schemas table
			try {
				stmt.execute("ALTER TABLE `schemas` ADD COLUMN `user_id` BIGINT NULL");
			} catch (Exception e) {
				LOG.debug("schemas user_id column update ignored: {}", e.getMessage());
			}

			try {
				stmt.execute("ALTER TABLE `schemas` ADD CONSTRAINT `fk_schemas_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL");
			} catch (Exception ignored) {}

			stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
			LOG.info("Database schema migration verification completed successfully.");
		} catch (Exception e) {
			LOG.warn("Database migration check encountered an issue: {}", e.getMessage());
		}
	}

	private void createAuthorityIfNotFound(String name) {
		if (!authorityRepository.existsById(name)) {
			Authority authority = new Authority();
			authority.setName(name);
			authorityRepository.save(authority);
		}
	}

	private void createAdminUserIfNotFound() {
		String adminEmail = "admin@snapvocab.com";
		if (userRepository.findOneByEmailIgnoreCase(adminEmail).isEmpty()) {
			Authority adminAuth = authorityRepository.findById(AuthoritiesConstants.ADMIN).orElseThrow();
			Authority userAuth = authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow();

			User admin = User.builder()
					.email(adminEmail)
					.password(passwordEncoder.encode("admin"))
					.firstName("Admin")
					.activated(true)
					.authorities(Set.of(adminAuth, userAuth))
					.build();

			userRepository.save(admin);
		}
	}

	private void createDefaultEnglishSchemaIfNotFound() {
		if (schemaRepository.findByCode("DEFAULT_ENGLISH").isEmpty()) {
			Schema schema = Schema.builder()
					.code("DEFAULT_ENGLISH")
					.isSystem(true)
					.name("Tiếng Anh Tiêu Chuẩn")
					.description("Cấu trúc từ vựng tiếng Anh chuẩn gồm Từ, Phiên âm, Định nghĩa, Ví dụ, Âm thanh và Hình ảnh")
					.attributeGroups(new LinkedHashSet<>())
					.templates(new ArrayList<>())
					.build();

			// 1. Group Main
			SchemaAttributeGroup mainGroup = SchemaAttributeGroup.builder()
					.schema(schema)
					.name("main")
					.label("Thông tin chính")
					.position((short) 0)
					.multiple(false)
					.attributes(new ArrayList<>())
					.build();

			SchemaAttribute wordAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("word")
					.label("Từ vựng")
					.dataType("TEXT")
					.required(true)
					.position((short) 0)
					.build();

			SchemaAttribute phoneticAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("phonetic")
					.label("Phiên âm")
					.dataType("TEXT")
					.required(false)
					.position((short) 1)
					.build();

			SchemaAttribute audioAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("audio")
					.label("Phát âm")
					.dataType("AUDIO")
					.required(false)
					.position((short) 2)
					.build();

			mainGroup.getAttributes().addAll(List.of(wordAttr, phoneticAttr, audioAttr));

			// 2. Group Meaning
			SchemaAttributeGroup meaningGroup = SchemaAttributeGroup.builder()
					.schema(schema)
					.name("meaning")
					.label("Ý nghĩa & Ví dụ")
					.position((short) 1)
					.multiple(false)
					.attributes(new ArrayList<>())
					.build();

			SchemaAttribute meaningAttr = SchemaAttribute.builder()
					.group(meaningGroup)
					.name("meaning")
					.label("Giải nghĩa")
					.dataType("TEXT")
					.required(true)
					.position((short) 0)
					.build();

			SchemaAttribute exampleAttr = SchemaAttribute.builder()
					.group(meaningGroup)
					.name("example")
					.label("Ví dụ")
					.dataType("TEXT")
					.required(false)
					.position((short) 1)
					.build();

			SchemaAttribute imageAttr = SchemaAttribute.builder()
					.group(meaningGroup)
					.name("image")
					.label("Hình ảnh minh họa")
					.dataType("IMAGE")
					.required(false)
					.position((short) 2)
					.build();

			meaningGroup.getAttributes().addAll(List.of(meaningAttr, exampleAttr, imageAttr));

			schema.getAttributeGroups().add(mainGroup);
			schema.getAttributeGroups().add(meaningGroup);

			// 3. Template Standard (Thẻ chuẩn)
			Template standardTemplate = Template.builder()
					.schema(schema)
					.code("STANDARD")
					.name("Thẻ Chuẩn (Chữ -> Nghĩa)")
					.isDefault(true)
					.elements(new ArrayList<>())
					.build();

			standardTemplate.addElement(createFieldElement(0, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, false, 24, Alignment.CENTER, "#111827"));
			standardTemplate.addElement(createFieldElement(1, phoneticAttr, null, "Phiên âm", true, true, 16, Alignment.CENTER, "#6B7280"));
			standardTemplate.addElement(createFieldElement(2, audioAttr, SemanticRole.AUDIO, "Phát âm", true, true, 16, Alignment.CENTER, null));
			standardTemplate.addElement(TemplateElement.builder().position(3).type(TemplateElementType.SECTION_BREAK).build());
			standardTemplate.addElement(createFieldElement(4, meaningAttr, SemanticRole.DEFINITION, "Nghĩa", false, false, 20, Alignment.LEFT, "#1F2937"));
			standardTemplate.addElement(createFieldElement(5, exampleAttr, SemanticRole.EXAMPLE_SENTENCE, "Ví dụ", true, false, 15, Alignment.LEFT, "#4B5563"));
			standardTemplate.addElement(createFieldElement(6, imageAttr, SemanticRole.IMAGE, "Hình ảnh", true, false, null, Alignment.CENTER, null));

			// 4. Template Listening (Luyện nghe)
			Template listeningTemplate = Template.builder()
					.schema(schema)
					.code("LISTENING")
					.name("Luyện Nghe (Âm thanh -> Chữ)")
					.isDefault(false)
					.elements(new ArrayList<>())
					.build();

			listeningTemplate.addElement(createFieldElement(0, audioAttr, SemanticRole.AUDIO, "Nghe phát âm", false, true, 32, Alignment.CENTER, null));
			listeningTemplate.addElement(TemplateElement.builder().position(1).type(TemplateElementType.SECTION_BREAK).build());
			listeningTemplate.addElement(createFieldElement(2, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, false, 24, Alignment.CENTER, "#111827"));
			listeningTemplate.addElement(createFieldElement(3, phoneticAttr, null, "Phiên âm", true, true, 16, Alignment.CENTER, "#6B7280"));
			listeningTemplate.addElement(createFieldElement(4, meaningAttr, SemanticRole.DEFINITION, "Nghĩa", false, false, 20, Alignment.LEFT, "#1F2937"));
			listeningTemplate.addElement(createFieldElement(5, exampleAttr, SemanticRole.EXAMPLE_SENTENCE, "Ví dụ", true, false, 15, Alignment.LEFT, "#4B5563"));

			// 5. Template Reverse (Đảo mặt)
			Template reverseTemplate = Template.builder()
					.schema(schema)
					.code("REVERSE")
					.name("Đảo Mặt Thẻ (Nghĩa -> Đoán từ)")
					.isDefault(false)
					.elements(new ArrayList<>())
					.build();

			reverseTemplate.addElement(createFieldElement(0, meaningAttr, SemanticRole.DEFINITION, "Nghĩa", false, false, 22, Alignment.CENTER, "#1F2937"));
			reverseTemplate.addElement(createFieldElement(1, imageAttr, SemanticRole.IMAGE, "Hình ảnh", true, false, null, Alignment.CENTER, null));
			reverseTemplate.addElement(TemplateElement.builder().position(2).type(TemplateElementType.SECTION_BREAK).build());
			reverseTemplate.addElement(createFieldElement(3, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, false, 24, Alignment.CENTER, "#111827"));
			reverseTemplate.addElement(createFieldElement(4, phoneticAttr, null, "Phiên âm", true, true, 16, Alignment.CENTER, "#6B7280"));
			reverseTemplate.addElement(createFieldElement(5, audioAttr, SemanticRole.AUDIO, "Phát âm", true, true, 16, Alignment.CENTER, null));

			schema.getTemplates().addAll(List.of(standardTemplate, listeningTemplate, reverseTemplate));

			schemaRepository.save(schema);
		}
	}

	private TemplateElement createFieldElement(int position, SchemaAttribute attr, SemanticRole role,
			String label, boolean hideIfEmpty, boolean audioAction, Integer fontSize, Alignment alignment, String color) {
		TemplateElement element = TemplateElement.builder()
				.position(position)
				.type(TemplateElementType.FIELD)
				.build();

		TemplateField field = TemplateField.builder()
				.element(element)
				.schemaAttribute(attr)
				.semanticRole(role)
				.fieldLabel(label)
				.hideIfEmpty(hideIfEmpty)
				.audioAction(audioAction)
				.fontSize(fontSize)
				.alignment(alignment)
				.color(color)
				.build();

		element.setField(field);
		return element;
	}

	private void consolidateAndCleanSchemas() {
		try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

			// 1. Locate DEFAULT_ENGLISH schema
			Long defaultEnglishId = null;
			try (java.sql.ResultSet rs = stmt.executeQuery("SELECT id FROM `schemas` WHERE code = 'DEFAULT_ENGLISH' LIMIT 1")) {
				if (rs.next()) {
					defaultEnglishId = rs.getLong(1);
				}
			}

			if (defaultEnglishId == null) {
				LOG.warn("DEFAULT_ENGLISH schema not found, skipping consolidation.");
				stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
				return;
			}

			// 2. Ensure all 3 groups exist on DEFAULT_ENGLISH: main, meaning, examples
			Long mainGroupId = null;
			Long meaningGroupId = null;
			Long examplesGroupId = null;

			try (java.sql.ResultSet rs = stmt.executeQuery("SELECT id, name FROM `schema_attribute_groups` WHERE schema_id = " + defaultEnglishId)) {
				while (rs.next()) {
					String gname = rs.getString("name");
					long gid = rs.getLong("id");
					if ("main".equalsIgnoreCase(gname)) mainGroupId = gid;
					else if ("meaning".equalsIgnoreCase(gname)) meaningGroupId = gid;
					else if ("examples".equalsIgnoreCase(gname)) examplesGroupId = gid;
				}
			}

			if (mainGroupId == null) {
				stmt.execute("INSERT INTO `schema_attribute_groups` (`schema_id`, `name`, `label`, `multiple`, `position`, `created_at`, `updated_at`) VALUES (" + defaultEnglishId + ", 'main', 'Thông tin chính', 0, 0, NOW(6), NOW(6))");
				try (java.sql.ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()")) { if (rs.next()) mainGroupId = rs.getLong(1); }
			}
			if (meaningGroupId == null) {
				stmt.execute("INSERT INTO `schema_attribute_groups` (`schema_id`, `name`, `label`, `multiple`, `position`, `created_at`, `updated_at`) VALUES (" + defaultEnglishId + ", 'meaning', 'Ý nghĩa & Ví dụ', 0, 1, NOW(6), NOW(6))");
				try (java.sql.ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()")) { if (rs.next()) meaningGroupId = rs.getLong(1); }
			}
			if (examplesGroupId == null) {
				stmt.execute("INSERT INTO `schema_attribute_groups` (`schema_id`, `name`, `label`, `multiple`, `position`, `created_at`, `updated_at`) VALUES (" + defaultEnglishId + ", 'examples', 'Ví dụ câu', 1, 2, NOW(6), NOW(6))");
				try (java.sql.ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()")) { if (rs.next()) examplesGroupId = rs.getLong(1); }
			}

			// 3. Insert any missing attributes on DEFAULT_ENGLISH
			ensureAttribute(stmt, mainGroupId, "word", "Từ vựng", "TEXT", true, (short) 0);
			ensureAttribute(stmt, mainGroupId, "phonetic", "Phiên âm", "TEXT", false, (short) 1);
			ensureAttribute(stmt, mainGroupId, "audio", "Phát âm", "AUDIO", false, (short) 2);
			ensureAttribute(stmt, mainGroupId, "translation", "Bản dịch tiếng Việt", "TEXT", false, (short) 3);
			ensureAttribute(stmt, mainGroupId, "level", "Cấp độ", "TEXT", false, (short) 4);
			ensureAttribute(stmt, mainGroupId, "definition_translation", "Dịch định nghĩa", "TEXT", false, (short) 5);
			ensureAttribute(stmt, mainGroupId, "base_word", "Từ gốc", "TEXT", false, (short) 6);
			ensureAttribute(stmt, mainGroupId, "head", "Ký hiệu đầu từ", "TEXT", false, (short) 7);
			ensureAttribute(stmt, mainGroupId, "definition", "Định nghĩa tiếng Anh", "TEXT", false, (short) 8);

			ensureAttribute(stmt, meaningGroupId, "meaning", "Giải nghĩa", "TEXT", true, (short) 0);
			ensureAttribute(stmt, meaningGroupId, "example", "Ví dụ", "TEXT", false, (short) 1);
			ensureAttribute(stmt, meaningGroupId, "image", "Hình ảnh minh họa", "IMAGE", false, (short) 2);

			ensureAttribute(stmt, examplesGroupId, "content", "Nội dung ví dụ", "TEXT", true, (short) 0);
			ensureAttribute(stmt, examplesGroupId, "translation", "Dịch câu ví dụ", "TEXT", false, (short) 1);

			// Retrieve target attribute IDs on DEFAULT_ENGLISH
			Long targetWordId = getAttributeId(stmt, mainGroupId, "word");
			Long targetPhoneticId = getAttributeId(stmt, mainGroupId, "phonetic");
			Long targetAudioId = getAttributeId(stmt, mainGroupId, "audio");
			Long targetTranslationId = getAttributeId(stmt, mainGroupId, "translation");
			Long targetLevelId = getAttributeId(stmt, mainGroupId, "level");
			Long targetDefTranslationId = getAttributeId(stmt, mainGroupId, "definition_translation");
			Long targetMeaningId = getAttributeId(stmt, meaningGroupId, "meaning");
			Long targetExContentId = getAttributeId(stmt, examplesGroupId, "content");
			Long targetExTranslationId = getAttributeId(stmt, examplesGroupId, "translation");

			// 4. Remap topic_item_attribute_groups to DEFAULT_ENGLISH groups
			stmt.execute("UPDATE `topic_item_attribute_groups` tiag " +
					"JOIN `schema_attribute_groups` sag ON tiag.group_definition_id = sag.id " +
					"SET tiag.group_definition_id = " + mainGroupId + " " +
					"WHERE sag.schema_id != " + defaultEnglishId + " AND sag.name = 'main'");

			stmt.execute("UPDATE `topic_item_attribute_groups` tiag " +
					"JOIN `schema_attribute_groups` sag ON tiag.group_definition_id = sag.id " +
					"SET tiag.group_definition_id = " + examplesGroupId + " " +
					"WHERE sag.schema_id != " + defaultEnglishId + " AND sag.name = 'examples'");

			// 5. Remap topic_item_attribute_values to DEFAULT_ENGLISH attributes
			if (targetWordId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetWordId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sa.name = 'base_word'");
			}

			if (targetPhoneticId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetPhoneticId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sa.name = 'head'");
			}

			if (targetMeaningId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetMeaningId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sa.name = 'definition'");
			}

			if (targetTranslationId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetTranslationId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sag.name = 'main' AND sa.name = 'translation'");
			}

			if (targetLevelId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetLevelId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sa.name = 'level'");
			}

			if (targetDefTranslationId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetDefTranslationId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sa.name = 'definition_translation'");
			}

			if (targetExContentId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetExContentId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sag.name = 'examples' AND sa.name = 'content'");
			}

			if (targetExTranslationId != null) {
				stmt.execute("UPDATE `topic_item_attribute_values` tiav " +
						"JOIN `schema_attributes` sa ON tiav.schema_attribute_id = sa.id " +
						"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
						"SET tiav.schema_attribute_id = " + targetExTranslationId + " " +
						"WHERE sag.schema_id != " + defaultEnglishId + " AND sag.name = 'examples' AND sa.name = 'translation'");
			}

			// 6. Point all topic_schemas to DEFAULT_ENGLISH
			stmt.execute("UPDATE `topic_schemas` SET `schema_id` = " + defaultEnglishId);

			// 7. Ensure all topics have valid active_template_id
			stmt.execute("UPDATE `topics` " +
					"SET `active_template_id` = ( " +
					"	SELECT id FROM `templates` WHERE schema_id = " + defaultEnglishId + " AND is_default = 1 LIMIT 1 " +
					") " +
					"WHERE `active_template_id` IS NULL OR `active_template_id` NOT IN ( " +
					"	SELECT id FROM `templates` WHERE schema_id = " + defaultEnglishId + " " +
					")");

			// 8. Delete redundant schemas and their groups/attributes (DO NOT TOUCH dictionary tables!)
			stmt.execute("DELETE sa FROM `schema_attributes` sa " +
					"JOIN `schema_attribute_groups` sag ON sa.group_id = sag.id " +
					"WHERE sag.schema_id != " + defaultEnglishId + " " +
					"  AND sag.schema_id IN (SELECT id FROM `schemas` WHERE is_system = 0 AND user_id IS NULL AND code IS NULL)");

			stmt.execute("DELETE FROM `schema_attribute_groups` " +
					"WHERE schema_id != " + defaultEnglishId + " " +
					"  AND schema_id IN (SELECT id FROM `schemas` WHERE is_system = 0 AND user_id IS NULL AND code IS NULL)");

			int deletedSchemas = stmt.executeUpdate("DELETE FROM `schemas` " +
					"WHERE id != " + defaultEnglishId + " " +
					"  AND is_system = 0 AND user_id IS NULL AND code IS NULL");

			stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
			if (deletedSchemas > 0) {
				LOG.info("Database Schema Consolidation completed: deleted {} redundant schemas. All topics unified under DEFAULT_ENGLISH (id={}).", deletedSchemas, defaultEnglishId);
			}
		} catch (Exception e) {
			LOG.error("Consolidate schemas encountered an issue: {}", e.getMessage(), e);
		}
	}

	private void ensureAttribute(Statement stmt, Long groupId, String name, String label, String dataType, boolean required, short position) {
		if (groupId == null) return;
		try (java.sql.ResultSet rs = stmt.executeQuery("SELECT id FROM `schema_attributes` WHERE group_id = " + groupId + " AND name = '" + name + "' LIMIT 1")) {
			if (!rs.next()) {
				stmt.execute("INSERT INTO `schema_attributes` (`group_id`, `name`, `label`, `data_type`, `required`, `position`, `created_at`, `updated_at`) " +
						"VALUES (" + groupId + ", '" + name + "', '" + label + "', '" + dataType + "', " + (required ? 1 : 0) + ", " + position + ", NOW(6), NOW(6))");
			}
		} catch (Exception e) {
			LOG.debug("ensureAttribute for {} ignored: {}", name, e.getMessage());
		}
	}

	private Long getAttributeId(Statement stmt, Long groupId, String name) {
		if (groupId == null) return null;
		try (java.sql.ResultSet rs = stmt.executeQuery("SELECT id FROM `schema_attributes` WHERE group_id = " + groupId + " AND name = '" + name + "' LIMIT 1")) {
			if (rs.next()) {
				return rs.getLong(1);
			}
		} catch (Exception e) {
			LOG.debug("getAttributeId for {} error: {}", name, e.getMessage());
		}
		return null;
	}
}
