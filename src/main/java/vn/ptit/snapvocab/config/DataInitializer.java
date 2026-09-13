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

	public DataInitializer(AuthorityRepository authorityRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			SchemaRepository schemaRepository) {
		this.authorityRepository = authorityRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.schemaRepository = schemaRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		createAuthorityIfNotFound(AuthoritiesConstants.ADMIN);
		createAuthorityIfNotFound(AuthoritiesConstants.USER);
		createAuthorityIfNotFound(AuthoritiesConstants.ANONYMOUS);

		createAdminUserIfNotFound();
		createDefaultEnglishSchemaIfNotFound();
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

			SchemaAttribute translationAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("translation")
					.label("Bản dịch tiếng Việt")
					.dataType("TEXT")
					.required(false)
					.position((short) 3)
					.build();

			SchemaAttribute levelAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("level")
					.label("Cấp độ")
					.dataType("TEXT")
					.required(false)
					.position((short) 4)
					.build();

			SchemaAttribute defTranslationAttr = SchemaAttribute.builder()
					.group(mainGroup)
					.name("definition_translation")
					.label("Dịch định nghĩa")
					.dataType("TEXT")
					.required(false)
					.position((short) 5)
					.build();

			mainGroup.getAttributes().addAll(List.of(wordAttr, phoneticAttr, audioAttr, translationAttr, levelAttr, defTranslationAttr));

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

			// 3. Group Examples
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
					.dataType("TEXT")
					.required(true)
					.position((short) 0)
					.build();

			SchemaAttribute exTranslationAttr = SchemaAttribute.builder()
					.group(examplesGroup)
					.name("translation")
					.label("Dịch câu ví dụ")
					.dataType("TEXT")
					.required(false)
					.position((short) 1)
					.build();

			examplesGroup.getAttributes().addAll(List.of(contentAttr, exTranslationAttr));

			schema.getAttributeGroups().add(mainGroup);
			schema.getAttributeGroups().add(meaningGroup);
			schema.getAttributeGroups().add(examplesGroup);

			// 4. Template Standard (Thẻ chuẩn)
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

			// 5. Template Listening (Luyện nghe)
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

			// 6. Template Reverse (Đảo mặt)
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
			LOG.info("Created DEFAULT_ENGLISH system schema and 3 default templates.");
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
}
