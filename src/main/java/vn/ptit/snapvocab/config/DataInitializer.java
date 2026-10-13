package vn.ptit.snapvocab.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.user.domain.Authority;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.AuthorityRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.config.SemanticBindingMigration;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.enumeration.Alignment;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;

@Component
public class DataInitializer implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

	private final AuthorityRepository authorityRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SchemaRepository schemaRepository;
	private final SemanticBindingMigration semanticBindingMigration;

	public DataInitializer(AuthorityRepository authorityRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			SchemaRepository schemaRepository,
			SemanticBindingMigration semanticBindingMigration) {
		this.authorityRepository = authorityRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.schemaRepository = schemaRepository;
		this.semanticBindingMigration = semanticBindingMigration;
	}

	@Override
	@Transactional
	public void run(String... args) {
		createAuthorityIfNotFound(AuthoritiesConstants.ADMIN);
		createAuthorityIfNotFound(AuthoritiesConstants.USER);
		createAuthorityIfNotFound(AuthoritiesConstants.ANONYMOUS);

		createAdminUserIfNotFound();
		createDefaultEnglishSchemaIfNotFound();
		semanticBindingMigration.migrate();
	}

	private void createAuthorityIfNotFound(String name) {
		if (!authorityRepository.existsById(name)) {
			Authority authority = new Authority();
			authority.setName(name);
			authorityRepository.save(authority);
			LOG.info("Created authority: {}", name);
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
			LOG.info("Created default admin user: {}", adminEmail);
		}
	}

	private void createDefaultEnglishSchemaIfNotFound() {
		if (schemaRepository.findByCode("DEFAULT_ENGLISH").isEmpty()) {
			Schema schema = Schema.builder()
					.code("DEFAULT_ENGLISH")
					.isSystem(true)
					.name("Tiếng Anh Tiêu Chuẩn")
					.description(
							"Cấu trúc từ vựng tiếng Anh chuẩn gồm Từ, Phiên âm, Định nghĩa, Ví dụ, Âm thanh và Hình ảnh")
					.attributeGroups(new LinkedHashSet<>())
					.templates(new ArrayList<>())
					.build();

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
					.dataType(DataType.TEXT)
					.required(true)
					.position((short) 0)
					.build();

			SchemaAttribute phoneticAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("phonetic")
					.label("Phiên âm")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 1)
					.build();

			SchemaAttribute translationAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("translation")
					.label("Bản dịch tiếng Việt")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 2)
					.build();

			SchemaAttribute levelAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("level")
					.label("Cấp độ")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 3)
					.build();

			SchemaAttribute defTranslationAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("definition_translation")
					.label("Dịch định nghĩa")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 4)
					.build();

			SchemaAttribute definitionAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("definition")
					.label("Định nghĩa tiếng Anh")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 5)
					.build();

			mainGroup.getAttributes().addAll(
					List.of(wordAttr, phoneticAttr, translationAttr, levelAttr,
							defTranslationAttr, definitionAttr));

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
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 0)
					.build();

			SchemaAttribute exampleAttr = SchemaAttribute.builder()
					.group(meaningGroup)
					.name("example")
					.label("Ví dụ")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 1)
					.build();

			SchemaAttribute imageAttr = SchemaAttribute.builder()
					.group(meaningGroup)
					.name("image")
					.label("Hình ảnh minh họa")
					.dataType(DataType.IMAGE)
					.required(false)
					.position((short) 2)
					.build();

			meaningGroup.getAttributes().addAll(List.of(meaningAttr, exampleAttr, imageAttr));

			SchemaAttributeGroup examplesGroup = SchemaAttributeGroup.builder()
					.schema(schema)
					.name("examples")
					.label("Ví dụ câu")
					.position((short) 2)
					.multiple(true)
					.attributes(new ArrayList<>())
					.build();

			SchemaAttribute contentAttr = SchemaAttribute.builder()
					.group(examplesGroup)
					.name("content")
					.label("Nội dung ví dụ")
					.dataType(DataType.TEXT)
					.required(true)
					.position((short) 0)
					.build();

			SchemaAttribute exTranslationAttr = SchemaAttribute.builder()
					.group(examplesGroup)
					.name("translation")
					.label("Dịch câu ví dụ")
					.dataType(DataType.TEXT)
					.required(false)
					.position((short) 1)
					.build();

			examplesGroup.getAttributes().addAll(List.of(contentAttr, exTranslationAttr));

			schema.getAttributeGroups().add(mainGroup);
			schema.getAttributeGroups().add(meaningGroup);
			schema.getAttributeGroups().add(examplesGroup);

			// =========================================================================
			// Template 1: STANDARD (Thẻ Chuẩn: Chữ -> Nghĩa)
			// =========================================================================
			Template standardTemplate = Template.builder()
					.schema(schema)
					.code("STANDARD")
					.name("Thẻ Chuẩn (Chữ -> Nghĩa)")
					.isDefault(true)
					.elements(new ArrayList<>())
					.build();

			// FRONT
			standardTemplate.addElement(
					TemplateElement.builder().position(0).side(CardSide.FRONT)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Thông tin chính").repeatable(false).build());
			standardTemplate.addElement(
					createFieldElement(1, CardSide.FRONT, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, false, 32,
							Alignment.CENTER, "#0F172A"));
			standardTemplate.addElement(
					createFieldElement(2, CardSide.FRONT, phoneticAttr, null, "Phiên âm", true, true, 18, Alignment.CENTER,
							"#0284C7"));
			standardTemplate.addElement(
					createFieldElement(3, CardSide.FRONT, levelAttr, null, "Cấp độ", true, false, 12,
							Alignment.CENTER, "#15803D"));

			// BACK
			standardTemplate.addElement(
					TemplateElement.builder().position(4).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Giải nghĩa").repeatable(false).build());
			standardTemplate.addElement(
					createFieldElement(5, CardSide.BACK, translationAttr, SemanticRole.NATIVE_TRANSLATION, "Bản dịch tiếng Việt", false, false, 24,
							Alignment.CENTER, "#15803D"));
			standardTemplate.addElement(
					createFieldElement(6, CardSide.BACK, definitionAttr, SemanticRole.DEFINITION, "Định nghĩa tiếng Anh", true, false,
							15, Alignment.LEFT, "#334155"));
			standardTemplate.addElement(
					createFieldElement(7, CardSide.BACK, defTranslationAttr, null, "Dịch định nghĩa", true, false,
							14, Alignment.LEFT, "#64748B"));
			standardTemplate.addElement(
					createFieldElement(8, CardSide.BACK, imageAttr, SemanticRole.IMAGE, "Hình ảnh", true, false, null,
							Alignment.CENTER, null));

			standardTemplate.addElement(
					TemplateElement.builder().position(9).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Ví dụ & Ngữ cảnh").repeatable(true).build());
			standardTemplate.addElement(
					createFieldElement(10, CardSide.BACK, contentAttr, SemanticRole.EXAMPLE_SENTENCE, "Câu ví dụ", false, false,
							15, Alignment.LEFT, "#1E293B"));
			standardTemplate.addElement(
					createFieldElement(11, CardSide.BACK, exTranslationAttr, null /* not the word meaning: quiz.md D10-D11 */, "Dịch câu ví dụ", true, false,
							14, Alignment.LEFT, "#64748B"));

			// =========================================================================
			// Template 2: LISTENING (Luyện Nghe: Âm thanh -> Chữ)
			// =========================================================================
			Template listeningTemplate = Template.builder()
					.schema(schema)
					.code("LISTENING")
					.name("Luyện Nghe (Âm thanh -> Chữ)")
					.isDefault(false)
					.elements(new ArrayList<>())
					.build();

			// FRONT
			listeningTemplate.addElement(
					TemplateElement.builder().position(0).side(CardSide.FRONT)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Luyện nghe").repeatable(false).build());
			listeningTemplate.addElement(
					createFieldElement(1, CardSide.FRONT, wordAttr, SemanticRole.AUDIO, "Nghe phát âm", false, true, 32,
							Alignment.CENTER, null));

			// BACK
			listeningTemplate.addElement(
					TemplateElement.builder().position(2).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Từ vựng & Giải nghĩa").repeatable(false).build());
			listeningTemplate.addElement(
					createFieldElement(3, CardSide.BACK, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, true, 24,
							Alignment.CENTER, "#0F172A"));
			listeningTemplate.addElement(
					createFieldElement(4, CardSide.BACK, phoneticAttr, null, "Phiên âm", true, true, 16, Alignment.CENTER,
							"#0284C7"));
			listeningTemplate.addElement(
					createFieldElement(5, CardSide.BACK, translationAttr, SemanticRole.NATIVE_TRANSLATION, "Bản dịch tiếng Việt", false, false, 18,
							Alignment.LEFT, "#15803D"));
			listeningTemplate.addElement(
					createFieldElement(6, CardSide.BACK, definitionAttr, SemanticRole.DEFINITION, "Định nghĩa tiếng Anh", true, false, 15,
							Alignment.LEFT, "#334155"));

			listeningTemplate.addElement(
					TemplateElement.builder().position(7).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Ví dụ & Ngữ cảnh").repeatable(true).build());
			listeningTemplate.addElement(
					createFieldElement(8, CardSide.BACK, contentAttr, SemanticRole.EXAMPLE_SENTENCE, "Câu ví dụ", false, false,
							15, Alignment.LEFT, "#1E293B"));
			listeningTemplate.addElement(
					createFieldElement(9, CardSide.BACK, exTranslationAttr, null /* not the word meaning: quiz.md D10-D11 */, "Dịch câu ví dụ", true, false,
							14, Alignment.LEFT, "#64748B"));

			// =========================================================================
			// Template 3: REVERSE (Đảo Mặt Thẻ: Nghĩa -> Đoán từ)
			// =========================================================================
			Template reverseTemplate = Template.builder()
					.schema(schema)
					.code("REVERSE")
					.name("Đảo Mặt Thẻ (Nghĩa -> Đoán từ)")
					.isDefault(false)
					.elements(new ArrayList<>())
					.build();

			// FRONT
			reverseTemplate.addElement(
					TemplateElement.builder().position(0).side(CardSide.FRONT)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Giải nghĩa").repeatable(false).build());
			reverseTemplate.addElement(
					createFieldElement(1, CardSide.FRONT, translationAttr, SemanticRole.NATIVE_TRANSLATION, "Bản dịch tiếng Việt", false, false, 22,
							Alignment.CENTER, "#15803D"));
			reverseTemplate.addElement(
					createFieldElement(2, CardSide.FRONT, definitionAttr, SemanticRole.DEFINITION, "Định nghĩa tiếng Anh", true, false, 16,
							Alignment.CENTER, "#334155"));
			reverseTemplate.addElement(
					createFieldElement(3, CardSide.FRONT, imageAttr, SemanticRole.IMAGE, "Hình ảnh", true, false, null,
							Alignment.CENTER, null));

			// BACK
			reverseTemplate.addElement(
					TemplateElement.builder().position(4).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Từ vựng").repeatable(false).build());
			reverseTemplate.addElement(
					createFieldElement(5, CardSide.BACK, wordAttr, SemanticRole.TARGET_WORD, "Từ vựng", false, true, 24,
							Alignment.CENTER, "#0F172A"));
			reverseTemplate.addElement(
					createFieldElement(6, CardSide.BACK, phoneticAttr, null, "Phiên âm", true, true, 16, Alignment.CENTER,
							"#0284C7"));

			reverseTemplate.addElement(
					TemplateElement.builder().position(7).side(CardSide.BACK)
							.type(TemplateElementType.SECTION_BREAK).sectionLabel("Ví dụ & Ngữ cảnh").repeatable(true).build());
			reverseTemplate.addElement(
					createFieldElement(8, CardSide.BACK, contentAttr, SemanticRole.EXAMPLE_SENTENCE, "Câu ví dụ", false, false,
							15, Alignment.LEFT, "#1E293B"));
			reverseTemplate.addElement(
					createFieldElement(9, CardSide.BACK, exTranslationAttr, null /* not the word meaning: quiz.md D10-D11 */, "Dịch câu ví dụ", true, false,
							14, Alignment.LEFT, "#64748B"));

			schema.getTemplates().addAll(List.of(standardTemplate, listeningTemplate, reverseTemplate));

			schemaRepository.save(schema);
			LOG.info("Created DEFAULT_ENGLISH system schema and 3 default templates.");
		}
	}

	private TemplateElement createFieldElement(int position, CardSide side,
			SchemaAttribute attr, SemanticRole role, String label, boolean hideIfEmpty, boolean audioAction,
			Integer fontSize, Alignment alignment, String color) {
		TemplateElement element = TemplateElement.builder()
				.position(position)
				.side(side)
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
}
