package vn.ptit.snapvocab.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import vn.ptit.snapvocab.config.SemanticBindingMigration.Entry;
import vn.ptit.snapvocab.config.SemanticBindingMigration.ForkMode;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateElement;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.domain.mapper.TemplateMapper;
import vn.ptit.snapvocab.quiz.support.QuizIntegrationTestBase;

class SemanticBindingMigrationIntegrationTest extends QuizIntegrationTestBase {

	@Autowired
	private SemanticBindingMigration migration;
	@Autowired
	private DataInitializer dataInitializer;

	private final List<Long> createdSchemas = new ArrayList<>();

	@AfterEach
	void dropCreatedSchemas() {
		var jdbc = jdbc();
		for (Long id : createdSchemas) {
			jdbc.update("DELETE tf FROM template_fields tf JOIN template_elements te ON te.id = tf.element_id "
					+ "JOIN templates t ON t.id = te.template_id WHERE t.schema_id = ?", id);
			jdbc.update("DELETE te FROM template_elements te JOIN templates t ON t.id = te.template_id "
					+ "WHERE t.schema_id = ?", id);
			jdbc.update("DELETE FROM templates WHERE schema_id = ?", id);
			jdbc.update("DELETE sa FROM schema_attributes sa JOIN schema_attribute_groups g ON g.id = sa.group_id "
					+ "WHERE g.schema_id = ?", id);
			jdbc.update("DELETE FROM schema_attribute_groups WHERE schema_id = ?", id);
			jdbc.update("DELETE FROM `schemas` WHERE id = ?", id);
		}
	}

	/** Role → "group.attribute" of the hidden fields, per template code. */
	private Map<String, List<String>> hiddenBindings(Long schemaId) {
		return tx().execute(s -> {
			Map<String, List<String>> result = new java.util.TreeMap<>();
			for (Template t : templateRepository.findBySchemaId(schemaId)) {
				result.put(t.getCode(), t.getElements().stream()
						.map(TemplateElement::getField)
						.filter(f -> f != null && f.isHidden())
						.map(f -> f.getSemanticRole() + "=" + f.getSchemaAttribute().getGroup().getName() + "."
								+ f.getSchemaAttribute().getName())
						.sorted()
						.toList());
			}
			return result;
		});
	}

	/** Everything a client sees: visible elements with their field role/attribute/styling. */
	private List<String> visibleSnapshot(Long schemaId) {
		return tx().execute(s -> {
			List<String> result = new ArrayList<>();
			for (Template t : templateRepository.findBySchemaId(schemaId)) {
				for (TemplateElement e : t.getElements()) {
					TemplateField f = e.getField();
					if (f != null && f.isHidden()) {
						continue;
					}
					result.add(t.getCode() + "|" + e.getPosition() + "|" + e.getSide() + "|" + e.getType() + "|"
							+ (f == null ? "-" : f.getSchemaAttribute().getId() + ":" + f.getSemanticRole() + ":"
									+ f.getFieldLabel() + ":" + f.isHideIfEmpty() + ":" + f.getFontSize()));
				}
			}
			return result;
		});
	}

	@Test
	void startupAddsTheTwoHiddenBindingsToEveryDefaultTemplate() {
		List<String> expected = List.of("EXAMPLE_SENTENCE=examples.content", "NATIVE_TRANSLATION=main.translation");
		assertThat(hiddenBindings(defaultSchema().getId()))
				.containsOnlyKeys("STANDARD", "LISTENING", "REVERSE")
				.allSatisfy((code, bindings) -> assertThat(bindings).isEqualTo(expected));

		Integer hiddenRows = jdbc().queryForObject("SELECT COUNT(*) FROM template_fields WHERE hidden = 1", Integer.class);
		assertThat(hiddenRows).isEqualTo(6);
	}

	@Test
	void migrationAndInitializerAreIdempotentAndLeaveVisibleFieldsUntouched() throws Exception {
		Long schemaId = defaultSchema().getId();
		List<String> visibleBefore = visibleSnapshot(schemaId);
		Map<String, List<String>> hiddenBefore = hiddenBindings(schemaId);

		List<Entry> report = migration.migrate(ForkMode.APPLY);
		dataInitializer.run();

		assertThat(report.get(0).defaultSchema()).isTrue();
		assertThat(report.get(0).bindingsAdded()).isZero();
		assertThat(report.get(0).bindingsPlanned()).isZero();
		assertThat(visibleSnapshot(schemaId)).isEqualTo(visibleBefore);
		assertThat(hiddenBindings(schemaId)).isEqualTo(hiddenBefore);
	}

	@Test
	void visibleFieldCountMatchesTheOriginalSeed() {
		// Seed: STANDARD shows 6 fields, LISTENING 5, REVERSE 5 — unchanged by the migration.
		Map<String, Long> visibleFields = tx().execute(s -> {
			Map<String, Long> counts = new java.util.TreeMap<>();
			for (Template t : templateRepository.findBySchemaId(defaultSchema().getId())) {
				counts.put(t.getCode(), t.getElements().stream()
						.filter(e -> e.getField() != null && !e.getField().isHidden()).count());
			}
			return counts;
		});
		assertThat(visibleFields).containsExactlyInAnyOrderEntriesOf(Map.of("STANDARD", 6L, "LISTENING", 5L, "REVERSE", 5L));
	}

	@Test
	void clientTemplateDtoNeverContainsHiddenBindings() {
		TemplateMapper mapper = Mappers.getMapper(TemplateMapper.class);
		tx().executeWithoutResult(s -> {
			for (Template t : templateRepository.findBySchemaId(defaultSchema().getId())) {
				assertThat(mapper.toDto(t).getElements())
						.allSatisfy(e -> {
							if (e.getType() == TemplateElementType.FIELD) {
								assertThat(e.getField()).isNotNull();
								assertThat(e.getField().getSemanticRole())
										.isNotIn(SemanticRole.NATIVE_TRANSLATION);
							}
						})
						.hasSize((int) t.getElements().stream()
								.filter(e -> e.getField() == null || !e.getField().isHidden()).count());
			}
		});
	}

	@Test
	void derivedForkIsOnlyReportedByDefaultAndMigratedOnApply() {
		Long forkId = createForkLikeSchema(true);

		Entry reported = entryFor(migration.migrate(ForkMode.REPORT), forkId);
		assertThat(reported.eligible()).isTrue();
		assertThat(reported.bindingsPlanned()).isEqualTo(2);
		assertThat(reported.bindingsAdded()).isZero();
		assertThat(hiddenBindings(forkId).get("STANDARD")).isEmpty();

		Entry applied = entryFor(migration.migrate(ForkMode.APPLY), forkId);
		assertThat(applied.bindingsAdded()).isEqualTo(2);
		assertThat(hiddenBindings(forkId).get("STANDARD"))
				.containsExactly("EXAMPLE_SENTENCE=examples.content", "NATIVE_TRANSLATION=main.translation");

		Entry again = entryFor(migration.migrate(ForkMode.APPLY), forkId);
		assertThat(again.bindingsAdded()).isZero();
		assertThat(again.bindingsPlanned()).isZero();
	}

	@Test
	void unrelatedCustomSchemaIsNeverTouched() {
		Long customId = createForkLikeSchema(false);

		Entry entry = entryFor(migration.migrate(ForkMode.APPLY), customId);

		assertThat(entry.eligible()).isFalse();
		assertThat(entry.reason()).contains("group");
		assertThat(hiddenBindings(customId).get("STANDARD")).isEmpty();
	}

	private static Entry entryFor(List<Entry> report, Long schemaId) {
		return report.stream().filter(e -> e.schemaId().equals(schemaId)).findFirst().orElseThrow();
	}

	/**
	 * A user schema with a STANDARD template and no hidden bindings, like a fork taken before the
	 * migration existed. {@code derived=false} renames a group so it no longer matches DEFAULT_ENGLISH.
	 */
	private Long createForkLikeSchema(boolean derived) {
		Long id = tx().execute(s -> {
			Schema fork = Schema.builder().name("IT fork " + derived).code(null).isSystem(false)
					.attributeGroups(new LinkedHashSet<>()).templates(new ArrayList<>()).build();
			List<SchemaAttribute> word = new ArrayList<>();
			for (SchemaAttributeGroup g : groupRepository.findBySchemaIdWithAttributes(defaultSchema().getId())) {
				SchemaAttributeGroup copy = SchemaAttributeGroup.builder().schema(fork)
						.name(derived || !g.getName().equals("main") ? g.getName() : "core")
						.label(g.getLabel()).multiple(g.isMultiple()).position(g.getPosition())
						.attributes(new ArrayList<>()).build();
				for (SchemaAttribute a : g.getAttributes()) {
					SchemaAttribute attr = SchemaAttribute.builder().group(copy).name(a.getName()).label(a.getLabel())
							.dataType(a.getDataType()).required(a.isRequired()).position(a.getPosition()).build();
					copy.getAttributes().add(attr);
					if (a.getName().equals("word")) {
						word.add(attr);
					}
				}
				fork.getAttributeGroups().add(copy);
			}
			Template standard = Template.builder().schema(fork).code("STANDARD").name("Standard").isDefault(true)
					.elements(new ArrayList<>()).build();
			TemplateElement element = TemplateElement.builder().position(0).side(CardSide.FRONT)
					.type(TemplateElementType.FIELD).build();
			element.setField(TemplateField.builder().element(element).schemaAttribute(word.get(0))
					.semanticRole(SemanticRole.TARGET_WORD).build());
			standard.addElement(element);
			fork.getTemplates().add(standard);
			return schemaRepository.save(fork).getId();
		});
		createdSchemas.add(id);
		return id;
	}
}
