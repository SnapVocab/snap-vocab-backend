package vn.ptit.snapvocab.vocab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.quiz.domain.enumeration.QuizMode;
import vn.ptit.snapvocab.quiz.exception.QuizException;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.UpdateTemplateRequest;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.domain.mapper.TemplateMapper;
import vn.ptit.snapvocab.vocab.exception.ContentErrorCode;
import vn.ptit.snapvocab.vocab.exception.ContentException;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;

/**
 * Admin › Mẫu thẻ writes for SYSTEM schemas (FR-13.07, snapAdmin templates_ui.md §8): edit a template's elements, make
 * a template the schema default. The app draws learners' cards with the default template; Quiz reads the template a
 * topic chose. A learner's forked schema is never touched.
 */
@Service
@RequiredArgsConstructor
public class AdminTemplateService {

	private final SchemaAttributeGroupRepository groupRepository;
	private final TemplateMapper templateMapper;
	private final AdminAuditService audit;
	private final ObjectMapper objectMapper;

	@PersistenceContext
	private EntityManager em;

	@Transactional
	@Caching(evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)})
	public TemplateDTO update(Long id, UpdateTemplateRequest request) {
		Template template = lockSystemTemplate(id);
		if (request.version() != template.getVersion()) {
			throw ContentException.versionConflict("Template " + id, template.getVersion());
		}
		Map<Long, SchemaAttribute> attributes = new HashMap<>();
		for (SchemaAttributeGroup group : groupRepository.findBySchemaIdWithAttributes(template.getSchema().getId())) {
			group.getAttributes().forEach(a -> attributes.put(a.getId(), a));
		}
		Map<Long, TemplateElement> visible = new LinkedHashMap<>();
		List<TemplateElement> hidden = new ArrayList<>();
		for (TemplateElement e : template.getElements()) {
			if (e.getField() != null && e.getField().isHidden()) {
				hidden.add(e);
			} else {
				visible.put(e.getId(), e);
			}
		}
		check(request, attributes, visible, hidden);

		List<String> frontBefore = side(template, CardSide.FRONT);
		List<String> backBefore = side(template, CardSide.BACK);
		String nameBefore = template.getName();

		// (template_id, position) is unique: park every row on a negative position before numbering 0..n.
		int parked = -1;
		for (TemplateElement e : template.getElements()) {
			e.setPosition(parked--);
		}
		em.flush();

		List<TemplateElement> next = new ArrayList<>();
		for (UpdateTemplateRequest.Element in : request.elements()) {
			TemplateElement element = in.id() == null ? TemplateElement.builder().template(template).build()
					: visible.remove(in.id());
			element.setPosition(next.size());
			element.setType(in.type());
			element.setSide(in.side());
			boolean section = in.type() == TemplateElementType.SECTION_BREAK;
			element.setSectionLabel(section ? blankToNull(in.sectionLabel()) : null);
			element.setRepeatable(section && Boolean.TRUE.equals(in.repeatable()));
			if (in.type() == TemplateElementType.FIELD) {
				TemplateField field = element.getField() != null ? element.getField()
						: TemplateField.builder().element(element).build();
				apply(field, in.field(), attributes);
				element.setField(field);
			} else if (element.getField() != null) {
				em.remove(element.getField());
				element.setField(null);
			}
			next.add(element);
		}
		for (TemplateElement e : hidden) {
			e.setPosition(next.size());
			next.add(e);
		}
		// Elements the body left out: orphanRemoval deletes them with their field (cascade ALL).
		visible.values().forEach(template::removeElement);
		for (TemplateElement e : next) {
			if (e.getId() == null) {
				template.addElement(e);
			}
		}
		template.getElements().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
		template.setName(request.name().strip());
		template.setVersion(template.getVersion() + 1);
		em.flush();

		List<AuditChange> changes = new ArrayList<>();
		if (!nameBefore.equals(template.getName())) {
			changes.add(AuditDiff.change(objectMapper, "name", nameBefore, template.getName()));
		}
		addChange(changes, "front", frontBefore, side(template, CardSide.FRONT));
		addChange(changes, "back", backBefore, side(template, CardSide.BACK));
		if (!changes.isEmpty()) {
			audit.record(AuditAction.CARD_TEMPLATE_UPDATED, target(template), changes, null, null, template.getVersion());
		}
		return templateMapper.toDto(template);
	}

	/**
	 * Moves the schema's default flag to this template: every learner's flashcards of the schema are drawn with it
	 * from the app's next load. Quiz is unchanged (topics keep the template they chose).
	 */
	@Transactional
	@Caching(evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)})
	public TemplateDTO makeDefault(Long id, long version) {
		Template template = lockSystemTemplate(id);
		if (version != template.getVersion()) {
			throw ContentException.versionConflict("Template " + id, template.getVersion());
		}
		if (template.isDefault()) {
			return templateMapper.toDto(template);
		}
		// The default draws every learner's flashcard. On prod (2026-11-09) LISTENING and REVERSE hold only hidden Quiz
		// bindings: as default they would give every card two blank sides.
		for (CardSide side : CardSide.values()) {
			if (template.getElements().stream().noneMatch(e -> e.getSide() == side && e.getType() == TemplateElementType.FIELD
					&& e.getField() != null && !e.getField().isHidden())) {
				throw ContentException.invalid("elements",
						"Template " + id + " shows no field on its " + side + " side; edit it before making it the default");
			}
		}
		List<Template> siblings = em.createQuery("SELECT t FROM Template t WHERE t.schema.id = :schemaId", Template.class)
				.setParameter("schemaId", template.getSchema().getId()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultList();
		Template previous = null;
		for (Template sibling : siblings) {
			if (sibling.isDefault() && !sibling.getId().equals(id)) {
				previous = sibling;
				sibling.setIsDefault(false);
				sibling.setVersion(sibling.getVersion() + 1);
			}
		}
		template.setIsDefault(true);
		template.setVersion(template.getVersion() + 1);
		em.flush();
		audit.record(AuditAction.CARD_TEMPLATE_DEFAULT_CHANGED, target(template),
				List.of(AuditDiff.change(objectMapper, "defaultTemplate", previous == null ? null : label(previous),
						label(template))),
				null, null, template.getVersion());
		return templateMapper.toDto(template);
	}

	private Template lockSystemTemplate(Long id) {
		Template template = em.find(Template.class, id, LockModeType.PESSIMISTIC_WRITE);
		if (template == null || !template.getSchema().isSystem()) {
			// A learner's forked schema keeps its own copy (templates_ui.md §1 "Bản riêng").
			throw new ResourceNotFoundException("System template not found: " + id);
		}
		return template;
	}

	/** §5.4 save rules: same as the editor, plus what only the server knows (ids, attributes, hidden bindings). */
	private static void check(UpdateTemplateRequest request, Map<Long, SchemaAttribute> attributes,
			Map<Long, TemplateElement> visible, List<TemplateElement> hidden) {
		List<TemplateField> fields = new ArrayList<>();
		boolean front = false;
		boolean back = false;
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int i = 0; i < request.elements().size(); i++) {
			UpdateTemplateRequest.Element e = request.elements().get(i);
			String at = "elements[" + i + "]";
			if (e.id() != null && (!visible.containsKey(e.id()) || !seen.add(e.id()))) {
				throw ContentException.invalid(at + ".id", "Element " + e.id() + " is not on this template or repeats");
			}
			if (e.type() != TemplateElementType.FIELD) {
				continue;
			}
			if (e.field() == null) {
				throw ContentException.invalid(at + ".field", "A FIELD element needs its field");
			}
			SchemaAttribute attribute = attributes.get(e.field().schemaAttributeId());
			if (attribute == null) {
				throw ContentException.invalid(at + ".field.schemaAttributeId",
						"Attribute " + e.field().schemaAttributeId() + " is not in this template's schema");
			}
			fields.add(TemplateField.builder().schemaAttribute(attribute).semanticRole(e.field().semanticRole()).build());
			front |= e.side() == CardSide.FRONT;
			back |= e.side() == CardSide.BACK;
		}
		if (!front || !back) {
			throw ContentException.invalid("elements", "Each side needs at least one field");
		}
		hidden.forEach(e -> fields.add(e.getField()));
		for (QuizMode mode : QuizMode.values()) {
			try {
				QuizContentResolver.bindings(fields, QuizContentResolver.requiredRoles(mode));
			} catch (QuizException e) {
				Map<String, Object> details = new LinkedHashMap<>();
				details.put("mode", mode.name());
				details.put("kind", e.getCode().name().endsWith("AMBIGUOUS") ? "AMBIGUOUS" : "MISSING");
				if (e.getDetails() instanceof Map<?, ?> d) {
					details.put("role", String.valueOf(d.get("semanticRole")));
				}
				throw new ContentException(ContentErrorCode.TEMPLATE_BINDING_INVALID,
						"Quiz " + mode + " cannot read this template: " + e.getMessage(), details);
			}
		}
	}

	private static void apply(TemplateField field, UpdateTemplateRequest.Field in, Map<Long, SchemaAttribute> attributes) {
		field.setSchemaAttribute(attributes.get(in.schemaAttributeId()));
		field.setSemanticRole(in.semanticRole());
		field.setFieldLabel(blankToNull(in.fieldLabel()));
		field.setHideIfEmpty(in.hideIfEmpty());
		field.setAudioAction(in.audioAction());
		field.setFontSize(in.fontSize());
		field.setAlignment(in.alignment());
		field.setColor(in.color());
		field.setHidden(false);
	}

	/** One line per visible element, for the audit row: what the side shows, in order. */
	private static List<String> side(Template template, CardSide side) {
		return template.getElements().stream()
				.filter(e -> e.getSide() == side && (e.getField() == null || !e.getField().isHidden()))
				.sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
				.map(e -> switch (e.getType()) {
					case SECTION_BREAK -> "§ " + Objects.toString(e.getSectionLabel(), "")
							+ (Boolean.TRUE.equals(e.getRepeatable()) ? " (lặp)" : "");
					case COLUMN_BREAK -> "| cột";
					case FIELD -> e.getField().getSchemaAttribute().getGroup().getName() + "."
							+ e.getField().getSchemaAttribute().getName()
							+ (e.getField().getSemanticRole() == null ? "" : " [" + e.getField().getSemanticRole() + "]")
							+ " “" + Objects.toString(e.getField().getFieldLabel(), "") + "”";
				}).toList();
	}

	private void addChange(List<AuditChange> changes, String field, List<String> before, List<String> after) {
		if (!before.equals(after)) {
			changes.add(AuditDiff.change(objectMapper, field, before, after));
		}
	}

	private static String label(Template template) {
		return template.getName() + " (#" + template.getId() + ")";
	}

	private static AuditTarget target(Template template) {
		return AuditTarget.of(AuditTargetType.CARD_TEMPLATE, template.getId(), template.getName(), template.getCode());
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
