package vn.ptit.snapvocab.vocab.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;

/**
 * One-time, idempotent configuration migration that adds the Quiz semantic-only
 * (hidden) bindings
 * to the DEFAULT_ENGLISH templates (decisions D10/D13):
 * <ul>
 * <li>{@code main.translation} → {@code NATIVE_TRANSLATION}</li>
 * <li>{@code examples.content} → {@code EXAMPLE_SENTENCE}</li>
 * </ul>
 * Group/attribute names are used here only to locate the configuration once;
 * runtime Quiz
 * resolution goes through {@code TemplateField.semanticRole} and never matches
 * names.
 *
 * <p>
 * Forked schemas are only touched when they are structurally derived from
 * DEFAULT_ENGLISH and
 * {@code snapvocab.semantic-binding.fork-mode=APPLY}; the default
 * {@code REPORT} logs what would
 * change and writes nothing.
 */
@Component
public class SemanticBindingMigration {

	private static final Logger LOG = LoggerFactory.getLogger(SemanticBindingMigration.class);

	static final String DEFAULT_SCHEMA_CODE = "DEFAULT_ENGLISH";
	static final Set<String> SYSTEM_TEMPLATE_CODES = Set.of("STANDARD", "LISTENING", "REVERSE");

	record Binding(String groupName, String attributeName, SemanticRole role) {
	}

	static final List<Binding> BINDINGS = List.of(
			new Binding("main", "translation", SemanticRole.NATIVE_TRANSLATION),
			new Binding("examples", "content", SemanticRole.EXAMPLE_SENTENCE));

	public enum ForkMode {
		REPORT, APPLY
	}

	/** One line of the migration report, per schema. */
	public record Entry(Long schemaId, boolean defaultSchema, boolean eligible, String reason,
			int bindingsAdded, int bindingsPlanned) {
	}

	private final SchemaRepository schemaRepository;
	private final SchemaAttributeGroupRepository groupRepository;
	private final TemplateRepository templateRepository;
	private final ForkMode forkMode;

	public SemanticBindingMigration(SchemaRepository schemaRepository,
			SchemaAttributeGroupRepository groupRepository,
			TemplateRepository templateRepository,
			@Value("${snapvocab.semantic-binding.fork-mode:REPORT}") ForkMode forkMode) {
		this.schemaRepository = schemaRepository;
		this.groupRepository = groupRepository;
		this.templateRepository = templateRepository;
		this.forkMode = forkMode;
	}

	@Transactional
	public List<Entry> migrate() {
		return migrate(forkMode);
	}

	@Transactional
	public List<Entry> migrate(ForkMode mode) {
		List<Entry> report = new ArrayList<>();
		Optional<Schema> defaultSchema = schemaRepository.findByCode(DEFAULT_SCHEMA_CODE);
		if (defaultSchema.isEmpty()) {
			LOG.warn("Semantic binding migration skipped: schema {} not found", DEFAULT_SCHEMA_CODE);
			return report;
		}

		Schema def = defaultSchema.get();
		List<SchemaAttributeGroup> defGroups = groupRepository.findBySchemaIdWithAttributes(def.getId());
		report.add(migrateSchema(def, defGroups, true, true));

		for (Schema schema : schemaRepository.findByIsSystemFalse()) {
			if (schema.getCode() != null) {
				continue;
			}
			List<SchemaAttributeGroup> groups = groupRepository.findBySchemaIdWithAttributes(schema.getId());
			List<Template> templates = templateRepository.findBySchemaId(schema.getId());
			String mismatch = structuralMismatch(defGroups, groups, templates);
			if (mismatch != null) {
				report.add(new Entry(schema.getId(), false, false, mismatch, 0, 0));
				continue;
			}
			report.add(migrateSchema(schema, groups, false, mode == ForkMode.APPLY));
		}

		for (Entry e : report) {
			LOG.info("Semantic binding migration [{}] schema={} default={} eligible={} added={} planned={} {}",
					mode, e.schemaId(), e.defaultSchema(), e.eligible(), e.bindingsAdded(),
					e.bindingsPlanned(), e.reason() == null ? "" : "reason=" + e.reason());
		}
		return report;
	}

	private Entry migrateSchema(Schema schema, List<SchemaAttributeGroup> groups, boolean isDefault,
			boolean apply) {
		Map<Binding, SchemaAttribute> targets = new LinkedHashMap<>();
		for (Binding b : BINDINGS) {
			SchemaAttribute attr = uniqueAttribute(groups, b.groupName(), b.attributeName());
			if (attr == null) {
				return new Entry(schema.getId(), isDefault, false,
						"attribute " + b.groupName() + "." + b.attributeName() + " missing or ambiguous", 0, 0);
			}
			targets.put(b, attr);
		}

		int planned = 0;
		int added = 0;
		List<String> conflicts = new ArrayList<>();
		for (Template template : templateRepository.findBySchemaId(schema.getId())) {
			if (!SYSTEM_TEMPLATE_CODES.contains(template.getCode())) {
				continue;
			}
			for (Map.Entry<Binding, SchemaAttribute> t : targets.entrySet()) {
				SemanticRole role = t.getKey().role();
				SchemaAttribute attr = t.getValue();
				List<TemplateField> sameRole = template.getElements().stream()
						.map(TemplateElement::getField)
						.filter(Objects::nonNull)
						.filter(f -> f.getSemanticRole() == role)
						.toList();
				if (sameRole.stream().anyMatch(f -> Objects.equals(f.getSchemaAttribute().getId(), attr.getId()))) {
					continue; // already bound: idempotent
				}
				if (role == SemanticRole.NATIVE_TRANSLATION && !sameRole.isEmpty()) {
					// Singleton role already bound elsewhere; adding would make the template
					// ambiguous.
					conflicts.add(template.getCode() + ":" + role);
					continue;
				}
				planned++;
				if (apply) {
					addHiddenBinding(template, attr, role);
					added++;
				}
			}
			if (apply) {
				templateRepository.save(template);
			}
		}
		return new Entry(schema.getId(), isDefault, true,
				conflicts.isEmpty() ? null : "skipped conflicting " + conflicts, added, planned);
	}

	private static void addHiddenBinding(Template template, SchemaAttribute attr, SemanticRole role) {
		int nextPosition = template.getElements().stream()
				.map(TemplateElement::getPosition)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(-1) + 1;
		TemplateElement element = TemplateElement.builder()
				.position(nextPosition)
				.side(CardSide.BACK)
				.type(TemplateElementType.FIELD)
				.build();
		TemplateField field = TemplateField.builder()
				.element(element)
				.schemaAttribute(attr)
				.semanticRole(role)
				.fieldLabel(attr.getLabel())
				.hideIfEmpty(true)
				.hidden(true)
				.build();
		element.setField(field);
		template.addElement(element);
	}

	private static SchemaAttribute uniqueAttribute(List<SchemaAttributeGroup> groups, String groupName,
			String attributeName) {
		List<SchemaAttributeGroup> matchingGroups = groups.stream()
				.filter(g -> groupName.equals(g.getName()))
				.toList();
		if (matchingGroups.size() != 1) {
			return null;
		}
		List<SchemaAttribute> attrs = matchingGroups.get(0).getAttributes().stream()
				.filter(a -> attributeName.equals(a.getName()))
				.toList();
		if (attrs.size() != 1 || attrs.get(0).getDataType() != DataType.TEXT) {
			return null;
		}
		return attrs.get(0);
	}

	/**
	 * A fork counts as derived from DEFAULT_ENGLISH when every default group exists
	 * exactly once
	 * with the same {@code multiple} flag, every default attribute exists exactly
	 * once in that group
	 * with the same data type (extra user-added attributes are allowed), and every
	 * template carries
	 * a system template code. Returns why not, or {@code null} when derived.
	 */
	static String structuralMismatch(List<SchemaAttributeGroup> defGroups, List<SchemaAttributeGroup> groups,
			List<Template> templates) {
		if (templates.isEmpty()) {
			return "no templates";
		}
		for (Template t : templates) {
			if (!SYSTEM_TEMPLATE_CODES.contains(t.getCode())) {
				return "non-system template code " + t.getCode();
			}
		}
		for (SchemaAttributeGroup dg : defGroups) {
			List<SchemaAttributeGroup> same = groups.stream().filter(g -> dg.getName().equals(g.getName())).toList();
			if (same.size() != 1 || same.get(0).isMultiple() != dg.isMultiple()) {
				return "group " + dg.getName() + " missing, duplicated or different";
			}
			for (SchemaAttribute da : dg.getAttributes()) {
				List<SchemaAttribute> sameAttr = same.get(0).getAttributes().stream()
						.filter(a -> da.getName().equals(a.getName()))
						.toList();
				if (sameAttr.size() != 1 || sameAttr.get(0).getDataType() != da.getDataType()) {
					return "attribute " + dg.getName() + "." + da.getName() + " missing, duplicated or different";
				}
			}
		}
		return null;
	}
}

