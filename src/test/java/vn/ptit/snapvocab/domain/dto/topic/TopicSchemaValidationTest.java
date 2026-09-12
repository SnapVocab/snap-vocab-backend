package vn.ptit.snapvocab.domain.dto.topic;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TopicSchemaValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	void shouldPassWhenDtoIsValid() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.topicId(1L)
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("main_info")
								.multiple(false)
								.position((short) 0)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("word")
												.dataType("TEXT")
												.position(0)
												.build()
								))
								.build()
				))
				.build();

		Set<ConstraintViolation<CreateTopicSchemaDTO>> violations = validator.validate(dto);
		assertThat(violations).isEmpty();
	}

	@Test
	void shouldFailWhenSchemaGroupsIsEmpty() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.topicId(1L)
				.schemaGroups(List.of())
				.build();

		Set<ConstraintViolation<CreateTopicSchemaDTO>> violations = validator.validate(dto);
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("schemaGroups");
	}

	@Test
	void shouldFailWhenGroupNameIsBlankOrInvalid() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("invalid group name!")
								.position((short) 0)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("word")
												.dataType("TEXT")
												.position(0)
												.build()
								))
								.build()
				))
				.build();

		Set<ConstraintViolation<CreateTopicSchemaDTO>> violations = validator.validate(dto);
		assertThat(violations).anyMatch(v -> v.getMessage().contains("letters, numbers, and underscores"));
	}

	@Test
	void shouldFailWhenDuplicateGroupNamesProvided() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("main_group")
								.position((short) 0)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("word")
												.dataType("TEXT")
												.position(0)
												.build()
								))
								.build(),
						CreateSchemaAttributeGroupDTO.builder()
								.name("MAIN_GROUP")
								.position((short) 1)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("meaning")
												.dataType("TEXT")
												.position(0)
												.build()
								))
								.build()
				))
				.build();

		Set<ConstraintViolation<CreateTopicSchemaDTO>> violations = validator.validate(dto);
		assertThat(violations).anyMatch(v -> v.getMessage().contains("Schema group names must be unique"));
	}

	@Test
	void shouldFailWhenDuplicateAttributeNamesInGroup() {
		CreateTopicSchemaDTO dto = CreateTopicSchemaDTO.builder()
				.schemaGroups(List.of(
						CreateSchemaAttributeGroupDTO.builder()
								.name("main_group")
								.position((short) 0)
								.attributes(List.of(
										CreateSchemaAttributeDTO.builder()
												.name("word")
												.dataType("TEXT")
												.position(0)
												.build(),
										CreateSchemaAttributeDTO.builder()
												.name("WORD")
												.dataType("TEXT")
												.position(1)
												.build()
								))
								.build()
				))
				.build();

		Set<ConstraintViolation<CreateTopicSchemaDTO>> violations = validator.validate(dto);
		assertThat(violations).anyMatch(v -> v.getMessage().contains("Attribute names within a group must be unique"));
	}
}
