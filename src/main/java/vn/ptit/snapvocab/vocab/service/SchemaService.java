package vn.ptit.snapvocab.vocab.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.dto.template.CreateTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.MyTemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardSide;
import vn.ptit.snapvocab.vocab.domain.enumeration.DataType;
import vn.ptit.snapvocab.vocab.domain.enumeration.TemplateElementType;
import vn.ptit.snapvocab.vocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.vocab.domain.mapper.TemplateMapper;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchemaService {

	private final SchemaRepository schemaRepository;
	private final TopicRepository topicRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;

	private final TopicItemAttributeValueRepository topicItemAttributeValueRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final TemplateRepository templateRepository;
	private final UserRepository userRepository;
	private final SchemaMapper schemaMapper;
	private final TemplateMapper templateMapper;
	private final Logger LOG = LoggerFactory.getLogger(SchemaService.class);

	@Transactional
	public SchemaDTO createSchema(CreateSchemaDTO createSchemaDTO, Long userId) {
		LOG.info("Creating new independent schema: {} for userId: {}", createSchemaDTO.getName(), userId);

		Schema schema = this.schemaMapper.toEntity(createSchemaDTO);
		if (userId != null) {
			User user = this.userRepository.findById(userId).orElse(null);
			schema.setUser(user);
		}
		schema.setIsSystem(false);
		Schema saved = this.schemaRepository.save(schema);

		return getSchemaById(saved.getId(), userId);
	}

	@Transactional
	public SchemaDTO createSchema(CreateSchemaDTO createSchemaDTO) {
		return createSchema(createSchemaDTO, null);
	}

	public SchemaDTO getSchemaById(Long schemaId, Long userId) {
		LOG.info("Fetching schema by id: {} for userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaAccess(schema, userId);

		List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schemaId);
		return this.schemaMapper.toDto(schema, groups);
	}

	public SchemaDTO getSchemaById(Long schemaId) {
		return getSchemaById(schemaId, null);
	}

	public List<SchemaDTO> getAllSchemas(Long userId) {
		LOG.info("Fetching accessible schemas for userId: {}", userId);
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		List<Schema> schemas;
		if (isAdmin) {
			schemas = this.schemaRepository.findAll();
		} else if (userId != null) {
			schemas = this.schemaRepository.findAccessibleSchemas(userId);
		} else {
			schemas = this.schemaRepository.findByIsSystemTrue();
		}
		return schemas.stream()
				.map(schema -> {
					List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository
							.findBySchemaIdWithAttributes(schema.getId());
					return this.schemaMapper.toDto(schema, groups);
				})
				.toList();
	}

	public List<SchemaDTO> getAllSchemas() {
		return getAllSchemas(null);
	}

	@Transactional
	public SchemaDTO updateSchema(Long schemaId, CreateSchemaDTO createSchemaDTO, Long userId) {
		LOG.info("Updating schema with id: {} by userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaModification(schema, userId);

		// Additive Evolution protection: verify that no attribute currently in use is
		// being deleted
		List<SchemaAttributeGroup> existingGroups = this.schemaAttributeGroupRepository
				.findBySchemaIdWithAttributes(schemaId);
		Set<String> newGroupAttrKeys = new HashSet<>();
		if (createSchemaDTO.getSchemaGroups() != null) {
			for (var g : createSchemaDTO.getSchemaGroups()) {
				if (g.getAttributes() != null) {
					for (var a : g.getAttributes()) {
						newGroupAttrKeys
								.add(g.getName().trim().toLowerCase() + "::" + a.getName().trim().toLowerCase());
					}
				}
			}
		}

		for (SchemaAttributeGroup eg : existingGroups) {
			if (eg.getAttributes() != null) {
				for (SchemaAttribute ea : eg.getAttributes()) {
					String key = eg.getName().trim().toLowerCase() + "::" + ea.getName().trim().toLowerCase();
					if (!newGroupAttrKeys.contains(key)) {
						long valueCount = this.topicItemAttributeValueRepository.countByAttributeId(ea.getId());
						long templateCount = this.templateFieldRepository.countBySchemaAttributeId(ea.getId());
						if (valueCount > 0 || templateCount > 0) {
							throw new IllegalStateException(
									"Cannot delete attribute '" + ea.getName() + "' because it is currently used by "
											+ valueCount + " vocabulary item(s) and " + templateCount
											+ " template field(s). Please fork the schema for your topic to customize it independently.");
						}
					}
				}
			}
		}

		this.schemaMapper.updateEntityFromDto(createSchemaDTO, schema);
		Schema updated = this.schemaRepository.save(schema);

		return getSchemaById(updated.getId(), userId);
	}

	@Transactional
	public SchemaDTO updateSchema(Long schemaId, CreateSchemaDTO createSchemaDTO) {
		return updateSchema(schemaId, createSchemaDTO, null);
	}

	@Transactional
	public void deleteSchema(Long schemaId, Long userId) {
		LOG.info("Deleting schema with id: {} by userId: {}", schemaId, userId);
		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		validateSchemaModification(schema, userId);

		long assignedCount = this.topicRepository.countBySchemaId(schemaId);

		if (assignedCount > 0) {
			throw new IllegalStateException(
					"Cannot delete schema because it is currently assigned to " + assignedCount
							+ " topic(s). Fork or reassign those topics first.");
		}

		this.schemaRepository.delete(schema);
	}

	@Transactional
	public void deleteSchema(Long schemaId) {
		deleteSchema(schemaId, null);
	}

	/** Error code: a template still shown by a topic cannot be deleted. */
	public static final String TEMPLATE_IN_USE = "TEMPLATE_IN_USE";

	/** Error code: a template field names an attribute the schema does not have, and no name to create one. */
	public static final String UNKNOWN_ATTRIBUTE = "UNKNOWN_ATTRIBUTE";

	/**
	 * Code of the single personal copy a learner gets of a schema they do not own. Every template they build on
	 * that schema lands in the same copy, so it can be reused for the next topic (a topic only shows templates of
	 * its own schema).
	 */
	static String personalCopyCode(Long userId, Long baseSchemaId) {
		return "CUSTOM_" + userId + "_BASE_" + baseSchemaId;
	}

	@Transactional
	public TemplateDTO createTemplate(Long schemaId, CreateTemplateDTO dto, Long userId) {
		LOG.info("Creating new template '{}' on schema {} for userId {}", dto.getName(), schemaId, userId);
		if (userId == null) {
			throw new AccessDeniedException("Authentication required to create a template");
		}

		Schema baseSchema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));
		validateSchemaAccess(baseSchema, userId);

		User currentUser = this.userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

		List<SchemaAttributeGroup> baseGroups = this.schemaAttributeGroupRepository
				.findBySchemaIdWithAttributes(baseSchema.getId());

		Schema targetSchema;
		List<SchemaAttributeGroup> targetGroups;
		Map<Long, SchemaAttribute> oldAttrToNewAttr = new HashMap<>();

		// Never mutate a system/shared schema directly for a normal user: use their personal copy of it.
		if (owns(baseSchema, userId)) {
			targetSchema = baseSchema;
			targetGroups = new ArrayList<>(baseGroups);
		} else {
			String code = personalCopyCode(userId, baseSchema.getId());
			Optional<Schema> existingCopy = this.schemaRepository.findByCode(code).filter(s -> owns(s, userId));
			if (existingCopy.isPresent()) {
				targetSchema = existingCopy.get();
				targetGroups = new ArrayList<>(this.schemaAttributeGroupRepository
						.findBySchemaIdWithAttributes(targetSchema.getId()));
				mapAttributesByName(baseGroups, targetGroups, oldAttrToNewAttr);
			} else {
				targetSchema = this.schemaRepository.saveAndFlush(
						cloneSchema(baseSchema, baseGroups, currentUser, code, oldAttrToNewAttr));
				targetGroups = new ArrayList<>(targetSchema.getAttributeGroups());
			}
		}

		List<Template> schemaTemplates = this.templateRepository.findBySchemaId(targetSchema.getId());
		boolean makeDefault = Boolean.TRUE.equals(dto.getIsDefault()) || schemaTemplates.isEmpty();
		if (makeDefault) {
			for (Template existing : schemaTemplates) {
				if (existing.isDefault()) {
					existing.setIsDefault(false);
					this.templateRepository.save(existing);
				}
			}
		}

		Template template = Template.builder()
				.schema(targetSchema)
				.code("TPL_" + System.currentTimeMillis())
				.name(dto.getName().trim())
				.isDefault(makeDefault)
				.elements(new ArrayList<>())
				.build();

		if (applyElements(template, dto.getElements(), targetSchema, targetGroups, oldAttrToNewAttr)) {
			this.schemaRepository.saveAndFlush(targetSchema);
		}

		Template saved = this.templateRepository.save(template);
		LOG.info("Created template {} on schema {} for userId {}", saved.getId(), targetSchema.getId(), userId);
		return this.templateMapper.toDto(saved);
	}

	/**
	 * "Cập nhật mẫu này": the learner's own template is rewritten in place, so every topic showing it changes. A
	 * system template is never edited here; the client offers "save as new" instead.
	 */
	@Transactional
	public TemplateDTO updateTemplate(Long schemaId, Long templateId, CreateTemplateDTO dto, Long userId) {
		Template template = ownedTemplate(schemaId, templateId, userId);
		Schema schema = template.getSchema();

		template.setName(dto.getName().trim());
		template.getElements().clear();
		// Remove the old elements first: the rebuilt ones reuse the same positions.
		this.templateRepository.saveAndFlush(template);

		List<SchemaAttributeGroup> groups = new ArrayList<>(
				this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(schema.getId()));
		if (applyElements(template, dto.getElements(), schema, groups, new HashMap<>())) {
			this.schemaRepository.saveAndFlush(schema);
		}

		Template saved = this.templateRepository.saveAndFlush(template);
		// Moves it to the top of "Mẫu thẻ của tôi" even when only its fields changed.
		this.templateRepository.touch(saved.getId(), Instant.now());
		LOG.info("Updated template {} on schema {} for userId {}", saved.getId(), schema.getId(), userId);
		return this.templateMapper.toDto(saved);
	}

	/** Only a template no topic shows: deleting one in use would leave those topics without a card layout. */
	@Transactional
	public void deleteTemplate(Long schemaId, Long templateId, Long userId) {
		Template template = ownedTemplate(schemaId, templateId, userId);
		if (this.topicRepository.countByActiveTemplateId(templateId) > 0) {
			throw new ResourceAlreadyExistException(TEMPLATE_IN_USE);
		}
		this.templateRepository.delete(template);
		LOG.info("Deleted template {} for userId {}", templateId, userId);
	}

	/**
	 * "Mẫu thẻ của tôi": the templates the learner built in Template Builder (code {@code TPL_}), newest first, with
	 * how many topics show each one. Copies made when a topic's schema was forked are not listed.
	 */
	public List<MyTemplateDTO> getMyTemplates(Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required");
		}
		List<Template> templates = this.templateRepository.findLibraryTemplates(userId);
		Map<Long, Long> usage = new HashMap<>();
		if (!templates.isEmpty()) {
			for (Object[] row : this.topicRepository.countByActiveTemplateIds(
					templates.stream().map(Template::getId).toList())) {
				usage.put((Long) row[0], (Long) row[1]);
			}
		}
		return templates.stream().map(template -> new MyTemplateDTO(
				template.getId(),
				template.getSchema().getId(),
				template.getName(),
				usage.getOrDefault(template.getId(), 0L),
				fieldLabels(template, CardSide.FRONT),
				fieldLabels(template, CardSide.BACK),
				template.getUpdatedAt())).toList();
	}

	private static List<String> fieldLabels(Template template, CardSide side) {
		return template.getElements().stream()
				.filter(element -> element.getType() == TemplateElementType.FIELD && element.getField() != null
						&& element.getSide() == side)
				.map(element -> {
					TemplateField field = element.getField();
					if (field.getFieldLabel() != null && !field.getFieldLabel().isBlank()) {
						return field.getFieldLabel();
					}
					return field.getSchemaAttribute() == null ? "" : field.getSchemaAttribute().getLabel();
				})
				.filter(label -> label != null && !label.isBlank())
				.toList();
	}

	private Template ownedTemplate(Long schemaId, Long templateId, Long userId) {
		if (userId == null) {
			throw new AccessDeniedException("Authentication required");
		}
		Template template = this.templateRepository.findById(templateId)
				.filter(t -> t.getSchema() != null && t.getSchema().getId().equals(schemaId))
				.orElseThrow(() -> new ResourceNotFoundException(
						"Template " + templateId + " not found on schema " + schemaId));
		if (!owns(template.getSchema(), userId)) {
			throw new AccessDeniedException("Only the owner can change this template; save it as a new one instead");
		}
		return template;
	}

	private static boolean owns(Schema schema, Long userId) {
		return !schema.isSystem() && schema.getUser() != null && schema.getUser().getId().equals(userId);
	}

	private static Schema cloneSchema(Schema baseSchema, List<SchemaAttributeGroup> baseGroups, User owner, String code,
			Map<Long, SchemaAttribute> oldAttrToNewAttr) {
		Schema cloned = Schema.builder()
				.code(code)
				.isSystem(false)
				.user(owner)
				.name(baseSchema.getName())
				.description(baseSchema.getDescription())
				.attributeGroups(new LinkedHashSet<>())
				.templates(new ArrayList<>())
				.build();

		for (SchemaAttributeGroup oldGroup : baseGroups) {
			SchemaAttributeGroup newGroup = SchemaAttributeGroup.builder()
					.schema(cloned)
					.name(oldGroup.getName())
					.label(oldGroup.getLabel())
					.multiple(oldGroup.isMultiple())
					.position(oldGroup.getPosition())
					.attributes(new ArrayList<>())
					.build();
			if (oldGroup.getAttributes() != null) {
				for (SchemaAttribute oldAttr : oldGroup.getAttributes()) {
					SchemaAttribute newAttr = SchemaAttribute.builder()
							.group(newGroup)
							.name(oldAttr.getName())
							.label(oldAttr.getLabel())
							.dataType(oldAttr.getDataType())
							.required(oldAttr.isRequired())
							.position(oldAttr.getPosition())
							.build();
					newGroup.getAttributes().add(newAttr);
					if (oldAttr.getId() != null) {
						oldAttrToNewAttr.put(oldAttr.getId(), newAttr);
					}
				}
			}
			cloned.getAttributeGroups().add(newGroup);
		}
		return cloned;
	}

	/** Base attribute id -> the copy's attribute with the same group and attribute name. */
	private static void mapAttributesByName(List<SchemaAttributeGroup> baseGroups, List<SchemaAttributeGroup> copyGroups,
			Map<Long, SchemaAttribute> oldAttrToNewAttr) {
		Map<String, SchemaAttribute> copyByKey = new HashMap<>();
		for (SchemaAttributeGroup group : copyGroups) {
			if (group.getAttributes() != null) {
				for (SchemaAttribute attr : group.getAttributes()) {
					copyByKey.put(attributeKey(group, attr), attr);
				}
			}
		}
		for (SchemaAttributeGroup group : baseGroups) {
			if (group.getAttributes() != null) {
				for (SchemaAttribute attr : group.getAttributes()) {
					SchemaAttribute copy = copyByKey.get(attributeKey(group, attr));
					if (copy != null && attr.getId() != null) {
						oldAttrToNewAttr.put(attr.getId(), copy);
					}
				}
			}
		}
	}

	private static String attributeKey(SchemaAttributeGroup group, SchemaAttribute attr) {
		return (group.getName() + "/" + attr.getName()).toLowerCase(Locale.ROOT);
	}

	/**
	 * Builds the template's elements from the request. A field naming an attribute the schema lacks (a field added in
	 * Template Builder) adds that attribute to the schema. Returns whether any attribute was added.
	 */
	private static boolean applyElements(Template template, List<CreateTemplateDTO.CreateTemplateElementDTO> elements,
			Schema targetSchema, List<SchemaAttributeGroup> targetGroups, Map<Long, SchemaAttribute> oldAttrToNewAttr) {
		Map<Long, SchemaAttribute> attrById = new HashMap<>();
		Map<String, SchemaAttribute> attrByName = new HashMap<>();
		for (SchemaAttributeGroup group : targetGroups) {
			if (group.getAttributes() != null) {
				for (SchemaAttribute attr : group.getAttributes()) {
					if (attr.getId() != null) {
						attrById.put(attr.getId(), attr);
					}
					if (attr.getName() != null) {
						attrByName.put(attr.getName().trim().toLowerCase(Locale.ROOT), attr);
					}
				}
			}
		}

		SchemaAttributeGroup customTargetGroup = targetGroups.isEmpty() ? null : targetGroups.get(0);
		boolean addedCustomAttributes = false;
		int pos = 0;

		for (CreateTemplateDTO.CreateTemplateElementDTO elDto : elements) {
			TemplateElement element = TemplateElement.builder()
					.template(template)
					.position(pos++)
					.type(elDto.getType())
					.side(elDto.getSide())
					.sectionLabel(elDto.getSectionLabel())
					.repeatable(Boolean.TRUE.equals(elDto.getRepeatable()))
					.build();

			if (elDto.getType() == TemplateElementType.FIELD) {
				CreateTemplateDTO.CreateTemplateFieldDTO fDto = elDto.getField();
				if (fDto == null) {
					continue;
				}
				SchemaAttribute resolvedAttr = null;
				if (fDto.getSchemaAttributeId() != null) {
					resolvedAttr = oldAttrToNewAttr.get(fDto.getSchemaAttributeId());
					if (resolvedAttr == null) {
						resolvedAttr = attrById.get(fDto.getSchemaAttributeId());
					}
				}
				if (resolvedAttr == null && fDto.getAttributeName() != null && !fDto.getAttributeName().isBlank()) {
					resolvedAttr = attrByName.get(fDto.getAttributeName().trim().toLowerCase(Locale.ROOT));
				}
				if (resolvedAttr == null && (fDto.getAttributeName() == null || fDto.getAttributeName().isBlank())) {
					// A field of another schema, or a stale id: refuse it rather than invent an unnamed attribute
					// (it showed up as a duplicate "Từ vựng" field in the add-word form).
					throw new BadRequestAlertException(UNKNOWN_ATTRIBUTE);
				}
				if (resolvedAttr == null) {
					// Brand-new custom field created in Template Builder (it carries a new attributeName): attach it.
					if (customTargetGroup == null) {
						customTargetGroup = SchemaAttributeGroup.builder()
								.schema(targetSchema)
								.name("custom")
								.label("Custom")
								.multiple(false)
								.position((short) 0)
								.attributes(new ArrayList<>())
								.build();
						targetSchema.getAttributeGroups().add(customTargetGroup);
						targetGroups.add(customTargetGroup);
					}
					String attrName = (fDto.getAttributeName() != null && !fDto.getAttributeName().isBlank())
							? fDto.getAttributeName().trim()
							: "custom_field_" + pos;
					String attrLabel = (fDto.getFieldLabel() != null && !fDto.getFieldLabel().isBlank())
							? fDto.getFieldLabel().trim()
							: attrName;
					resolvedAttr = SchemaAttribute.builder()
							.group(customTargetGroup)
							.name(attrName)
							.label(attrLabel)
							.dataType(fDto.getDataType() != null ? fDto.getDataType() : DataType.TEXT)
							.required(Boolean.TRUE.equals(fDto.getRequired()))
							.position((short) customTargetGroup.getAttributes().size())
							.build();
					customTargetGroup.getAttributes().add(resolvedAttr);
					attrByName.put(attrName.toLowerCase(Locale.ROOT), resolvedAttr);
					addedCustomAttributes = true;
				}

				TemplateField field = TemplateField.builder()
						.element(element)
						.schemaAttribute(resolvedAttr)
						.semanticRole(fDto.getSemanticRole())
						.fieldLabel(fDto.getFieldLabel())
						.hideIfEmpty(fDto.isHideIfEmpty())
						.audioAction(fDto.isAudioAction())
						.fontSize(fDto.getFontSize())
						.alignment(fDto.getAlignment())
						.color(fDto.getColor())
						.hidden(false)
						.build();
				element.setField(field);
			}

			template.addElement(element);
		}
		return addedCustomAttributes;
	}

	private void validateSchemaAccess(Schema schema, Long userId) {
		if (schema.isSystem() || schema.getUser() == null) {
			return; // System schemas or public templates accessible to all
		}
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		if (isAdmin) {
			return;
		}
		if (userId == null || !schema.getUser().getId().equals(userId)) {
			throw new AccessDeniedException("You do not have permission to access this schema");
		}
	}

	private void validateSchemaModification(Schema schema, Long userId) {
		boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
		if (schema.isSystem() || schema.getUser() == null) {
			if (!isAdmin) {
				throw new AccessDeniedException("Only administrators can modify or delete system schemas");
			}
			return;
		}
		if (!isAdmin && (userId == null || !schema.getUser().getId().equals(userId))) {
			throw new AccessDeniedException("You do not have permission to modify or delete this schema");
		}
	}
}

