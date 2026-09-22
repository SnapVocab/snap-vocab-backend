package vn.ptit.snapvocab.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.domain.Collection;
import vn.ptit.snapvocab.domain.Schema;
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Template;
import vn.ptit.snapvocab.domain.TemplateElement;
import vn.ptit.snapvocab.domain.TemplateField;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.TopicItem;
import vn.ptit.snapvocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.common.Meta;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemAttributeRow;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.domain.mapper.SchemaMapperUtils;
import vn.ptit.snapvocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.SchemaRepository;
import vn.ptit.snapvocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.repository.TemplateRepository;
import vn.ptit.snapvocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.repository.TopicRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.util.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.util.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

	private final TopicItemRepository topicItemRepository;
	private final TopicRepository topicRepository;
	private final CollectionRepository collectionRepository;
	private final SchemaRepository schemaRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;
	private final TemplateRepository templateRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final UserRepository userRepository;
	private final TopicMapper topicMapper;
	private final TopicSchemaMapper topicSchemaMapper;
	private final SchemaMapper schemaMapper;
	private final Logger LOG = LoggerFactory.getLogger(TopicService.class);


	/**
	 * Retrieves paginated vocabulary items for a given topic with EAV attributes aggregated into a
	 * clean, client-friendly structure, including the topic schema definition.
	 * <p>
	 * Query Strategy:
	 * <ol>
	 *   <li>Fetch paginated {@code topicItemIds} for the given topic ID.</li>
	 *   <li>Batch fetch attribute rows for those IDs (avoiding N+1 queries).</li>
	 *   <li>Aggregate rows into dynamic item properties and extract schema metadata with English labels.</li>
	 * </ol>
	 *
	 * @param topicId  The topic ID to fetch items for.
	 * @param pageable Pagination parameters (page, size, sort).
	 * @return A {@link PaginationObject} containing the aggregated items, pagination meta, and schema
	 * metadata.
	 */
	@Cacheable(
			cacheNames = "topic-items",
			key = "#topicId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize"
	)
	public PaginationObject<List<TopicItemData>> getTopicItems(Long topicId, Pageable pageable) {
		this.validateTopicAccess(topicId);
		LOG.info("Fetching topic items for topic ID: {}", topicId);

		// Step 1: Query paginated IDs for the target topic
		Page<Long> topicItemIds = this.topicItemRepository.findIdsByTopicId(topicId, pageable);
		LOG.info("Found {} topic items for topic ID: {}", topicItemIds.getContent().size(), topicId);

		Meta meta = Meta.builder()
				.pages((long) topicItemIds.getTotalPages())
				.size((long) pageable.getPageSize())
				.page((long) pageable.getPageNumber())
				.total(topicItemIds.getTotalElements())
				.build();

		if (topicItemIds.isEmpty()) {
			return PaginationObject.<List<TopicItemData>>builder()
					.data(new ArrayList<>())
					.meta(meta)
					.build();
		}

		// Step 2: Batch fetch EAV attribute rows for the page's item IDs
		List<TopicItemAttributeRow> rows = this.topicItemRepository.findAttributeRows(
				topicItemIds.getContent());

		// Step 3: Build schema definition
		TopicSchemaDTO schema = buildSchema(rows, topicId);

		// Step 4: Aggregate item data values by single vs multiple groups
		Map<Long, Instant> createdAtByItem = new LinkedHashMap<>();
		Map<Long, Map<String, Object>> singleGroupsByItem = new LinkedHashMap<>();
		Map<Long, Map<String, Map<Long, Map<String, Object>>>> multiGroupsByItem = new LinkedHashMap<>();

		for (TopicItemAttributeRow row : rows) {
			Long itemId = row.getTopicItemId();
			createdAtByItem.putIfAbsent(itemId, row.getItemCreatedAt());
			String groupName = row.getGroupName();
			boolean multiple = row.isGroupMultiple();
			Long instanceId = row.getGroupInstanceId();
			String attrName = row.getAttributeName();
			String value = row.getValue();

			if (!multiple) {
				// Single group: flatten directly into a Map<attribute, value>
				Map<String, Object> itemGroups = singleGroupsByItem.computeIfAbsent(itemId,
						k -> new LinkedHashMap<>());
				@SuppressWarnings("unchecked")
				Map<String, Object> groupAttrs = (Map<String, Object>) itemGroups.computeIfAbsent(
						groupName,
						k -> new LinkedHashMap<String, Object>()
				);
				if (attrName != null) {
					groupAttrs.put(attrName, value);
				}
			} else {
				// Multiple group: collect instances by instanceId preserving position order
				Map<String, Map<Long, Map<String, Object>>> itemMultiGroups =
						multiGroupsByItem.computeIfAbsent(itemId, k -> new LinkedHashMap<>());
				Map<Long, Map<String, Object>> instances =
						itemMultiGroups.computeIfAbsent(groupName, k -> new LinkedHashMap<>());
				if (instanceId != null) {
					Map<String, Object> instanceAttrs = instances.computeIfAbsent(
							instanceId,
							k -> new LinkedHashMap<>()
					);
					if (attrName != null) {
						instanceAttrs.put(attrName, value);
					}
				}
			}
		}

		// Step 5: Assemble TopicItemData list in original pagination order
		List<TopicItemData> items = new ArrayList<>();
		for (Long itemId : topicItemIds.getContent()) {
			Map<String, Object> groupMap = singleGroupsByItem.getOrDefault(itemId, new LinkedHashMap<>());

			Map<String, Map<Long, Map<String, Object>>> multiGroups = multiGroupsByItem.get(itemId);
			if (multiGroups != null) {
				for (Map.Entry<String, Map<Long, Map<String, Object>>> entry : multiGroups.entrySet()) {
					groupMap.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
				}
			}

			items.add(TopicItemData.builder()
					.topicItemId(itemId)
					.createdAt(createdAtByItem.get(itemId))
					.groups(groupMap)
					.build());
		}

		return PaginationObject.<List<TopicItemData>>builder()
				.data(items)
				.meta(meta)
				.build();
	}

	/**
	 * Builds structured schema metadata from attribute rows. Returns a {@link TopicSchemaDTO}
	 * containing a list of groups, where each group has a list of attributes. Preserves group and
	 * attribute position ordering.
	 */
	private TopicSchemaDTO buildSchema(List<TopicItemAttributeRow> rows, Long topicId) {
		Map<String, SchemaAttributeGroupDTO> groupMap = new LinkedHashMap<>();
		Map<String, Map<String, SchemaAttributeDTO>> attributeMapByGroup = new LinkedHashMap<>();

		for (TopicItemAttributeRow row : rows) {
			String groupName = row.getGroupName();
			if (groupName == null) {
				continue;
			}

			groupMap.computeIfAbsent(
					groupName,
					name -> SchemaAttributeGroupDTO.builder()
							.groupId(row.getGroupId())
							.name(name)
							.label(SchemaMapperUtils.resolveLabel(row.getGroupLabel(), name))
							.multiple(row.isGroupMultiple())
							.position(row.getGroupInstancePosition())
							.attributes(new ArrayList<>())
							.build()
			);

			String attrName = row.getAttributeName();
			if (attrName != null) {
				Map<String, SchemaAttributeDTO> groupAttrs =
						attributeMapByGroup.computeIfAbsent(groupName, k -> new LinkedHashMap<>());

				groupAttrs.computeIfAbsent(
						attrName,
						name -> SchemaAttributeDTO.builder()
								.attributeId(row.getAttributeId())
								.name(name)
								.label(SchemaMapperUtils.resolveLabel(row.getAttributeLabel(), name))
								.dataType(row.getDataType())
								.required(row.isAttributeRequired())
								.position(row.getAttributePosition())
								.build()
				);
			}
		}

		for (Map.Entry<String, SchemaAttributeGroupDTO> entry : groupMap.entrySet()) {
			Map<String, SchemaAttributeDTO> attrMap = attributeMapByGroup.get(entry.getKey());
			if (attrMap != null) {
				entry.getValue().setAttributes(new ArrayList<>(attrMap.values()));
			}
		}

		return TopicSchemaDTO.builder()
				.topicId(topicId)
				.groups(new ArrayList<>(groupMap.values()))
				.build();
	}

	/**
	 * Retrieves the full schema definition for a topic directly from the database, containing a list
	 * of groups and their respective lists of attributes.
	 */
	@Cacheable(
			cacheNames = "topic-schema",
			key = "#topicId"
	)
	public TopicSchemaDTO getTopicSchema(Long topicId) {
		this.validateTopicAccess(topicId);
		LOG.info("Fetching topic schema for topic ID: {}", topicId);
		Topic topic = this.topicRepository.findById(topicId)
				.orElse(null);

		if (topic == null || topic.getSchema() == null) {
			return TopicSchemaDTO.builder()
					.topicId(topicId)
					.groups(new ArrayList<>())
					.build();
		}

		List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(
				topic.getSchema().getId());
		long usageCount = this.topicRepository.countBySchemaId(topic.getSchema().getId());
		TopicSchemaDTO dto = this.topicSchemaMapper.toDto(topic, topic.getSchema(), groups);
		dto.setShared(usageCount > 1);
		dto.setUsageCount(usageCount);
		return dto;
	}

	@Cacheable(
			cacheNames = "topic-tree",
			key = "#collectionId + ':parent:' + (#parentId != null ? #parentId : 'root') + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize"
	)
	public PaginationObject<List<TopicDTO>> getTopicsByCollection(Long collectionId, Long parentId,
			Pageable pageable) {
		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		validateCollectionAccess(collection);

		Page<TopicDTO> topicPage = (parentId == null)
				? this.topicRepository.findByCollectionIdAndParentIsNull(collectionId, pageable)
				: this.topicRepository.findByCollectionIdAndParentId(collectionId, parentId, pageable);

		Meta meta = Meta.builder()
				.page((long) pageable.getPageNumber())
				.pages((long) topicPage.getTotalPages())
				.size((long) pageable.getPageSize())
				.total(topicPage.getTotalElements())
				.build();

		return PaginationObject.<List<TopicDTO>>builder()
				.data(topicPage.getContent())
				.meta(meta)
				.build();
	}

	@Transactional
	@Caching(
			evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)}
	)
	public TopicDTO createCollectionTopic(Long collectionId, CreateTopicDTO createTopicDTO) {
		Collection collection = collectionRepository.findById(collectionId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		if (collection.getType() == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException("Only administrators can add topics to system collections");
			}
		} else {
			Long currentUserId = SecurityUtils.getCurrentUserId()
					.or(() -> SecurityUtils.getCurrentUserLogin()
							.flatMap(userRepository::findOneByEmailIgnoreCase).map(User::getId))
					.orElse(null);

			if (currentUserId == null) {
				throw new AccessDeniedException("Authentication required to add topics to this collection");
			}
			boolean isOwner =
					collection.getOwner() != null && collection.getOwner().getId().equals(currentUserId);
			boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
			if (!isOwner && !isAdmin) {
				throw new AccessDeniedException(
						"You do not have permission to add topics to this collection");
			}
		}

		Topic parent = createTopicDTO.getParentId() != null ? topicRepository.findById(
				createTopicDTO.getParentId()).orElse(null) : null;

		Schema schema;
		if (createTopicDTO.getSchemaId() != null) {
			schema = this.schemaRepository.findById(createTopicDTO.getSchemaId())
					.orElseThrow(() -> new ResourceNotFoundException(
							"Schema not found with id: " + createTopicDTO.getSchemaId()));
		} else {
			schema = this.schemaRepository.findByCode("DEFAULT_ENGLISH")
					.orElseGet(() -> {
						User creator = collection.getOwner();
						Schema defaultSchema = Schema.builder()
								.name(createTopicDTO.getName() + " Schema")
								.description("Default schema for topic " + createTopicDTO.getName())
								.code(null)
								.isSystem(false)
								.user(creator)
								.attributeGroups(new LinkedHashSet<>())
								.templates(new ArrayList<>())
								.build();
						return this.schemaRepository.save(defaultSchema);
					});
		}

		// Resolve active template (from DTO if requested, or schema default)
		Template activeTemplate = null;
		if (createTopicDTO.getActiveTemplateId() != null) {
			activeTemplate = this.templateRepository.findById(createTopicDTO.getActiveTemplateId())
					.orElse(null);
		}
		if (activeTemplate == null && schema.getId() != null) {
			activeTemplate = this.templateRepository.findBySchemaIdAndIsDefaultTrue(schema.getId())
					.or(() -> this.templateRepository.findBySchemaId(schema.getId()).stream().findFirst())
					.orElse(null);
		}

		Topic topic = Topic.builder()
				.collection(collection)
				.parent(parent)
				.children(new ArrayList<>())
				.name(createTopicDTO.getName())
				.translation(createTopicDTO.getTranslation())
				.description(createTopicDTO.getDescription())
				.descriptionTranslation(createTopicDTO.getDescriptionTranslation())
				.schema(schema)
				.activeTemplate(activeTemplate)
				.items(new ArrayList<>())
				.build();

		Topic saved = topicRepository.save(topic);

		return topicMapper.toDto(saved);
	}

	public void validateTopicAccess(Long topicId) {
		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateCollectionAccess(topic.getCollection());
	}

	private void validateCollectionAccess(Collection collection) {
		if (collection.getType() == CollectionType.USER) {
			Long currentUserId = SecurityUtils.getCurrentUserId()
					.or(() -> SecurityUtils.getCurrentUserLogin()
							.flatMap(userRepository::findOneByEmailIgnoreCase).map(User::getId))
					.orElse(null);

			if (currentUserId == null) {
				throw new AccessDeniedException(
						"Authentication required to access this private collection");
			}
			boolean isOwner =
					collection.getOwner() != null && collection.getOwner().getId().equals(currentUserId);
			boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
			if (!isOwner && !isAdmin) {
				throw new AccessDeniedException("You do not have permission to access this collection");
			}
		}
	}

	@Transactional
	@CacheEvict(
			cacheNames = "topic-schema",
			key = "#topicId"
	)
	public TopicSchemaDTO createTopicSchema(Long topicId,
			CreateTopicSchemaDTO createTopicSchemaDTO) {
		this.validateTopicAccess(topicId);
		LOG.info("Creating schema for topicId: {}", topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));

		List<SchemaAttributeGroup> existingGroups = this.schemaAttributeGroupRepository.findByTopicIdWithAttributes(
				topicId);
		if (!existingGroups.isEmpty()) {
			throw new ResourceAlreadyExistException(
					"Topic already has a schema with groups for topicId: " + topicId);
		}

		String schemaName =
				createTopicSchemaDTO.getName() != null && !createTopicSchemaDTO.getName().isBlank()
						? createTopicSchemaDTO.getName()
						: topic.getName() + " Schema";

		CreateSchemaDTO schemaDTO = CreateSchemaDTO.builder()
				.name(schemaName)
				.description(createTopicSchemaDTO.getDescription())
				.schemaGroups(createTopicSchemaDTO.getSchemaGroups())
				.build();

		Schema schema = this.schemaMapper.toEntity(schemaDTO);
		User topicOwner = topic.getCollection() != null ? topic.getCollection().getOwner() : null;
		schema.setUser(topicOwner);
		schema.setIsSystem(false);
		Schema savedSchema = this.schemaRepository.save(schema);

		topic.setSchema(savedSchema);
		this.topicRepository.save(topic);

		return getTopicSchema(topicId);
	}

	@Transactional
	@CacheEvict(
			cacheNames = "topic-schema",
			key = "#topicId"
	)
	public TopicSchemaDTO assignSchemaToTopic(Long topicId, Long schemaId) {
		this.validateTopicAccess(topicId);
		LOG.info("Assigning schemaId {} to topicId {}", schemaId, topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));

		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		topic.setSchema(schema);
		this.topicRepository.save(topic);

		return getTopicSchema(topicId);
	}


	@Transactional
	@CacheEvict(
			cacheNames = "topic-schema",
			key = "#topicId"
	)
	public TopicSchemaDTO forkTopicSchema(Long topicId) {
		this.validateTopicAccess(topicId);
		LOG.info("Forking schema for topicId: {}", topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));

		if (topic.getSchema() == null) {
			throw new ResourceNotFoundException("Topic has no assigned schema for topicId: " + topicId);
		}

		Schema originalSchema = topic.getSchema();
		long usageCount = this.topicRepository.countBySchemaId(originalSchema.getId());

		if (usageCount <= 1) {
			LOG.info("Schema {} is already dedicated to topicId {}, no fork needed",
					originalSchema.getId(), topicId);
			return getTopicSchema(topicId);
		}

		// 1. Create a cloned Schema
		User topicOwner = topic.getCollection() != null ? topic.getCollection().getOwner() : null;
		Schema forkedSchema = Schema.builder()
				.name(topic.getName() + " Schema (Custom)")
				.description("Customized schema for topic " + topic.getName())
				.code(null)
				.isSystem(false)
				.user(topicOwner)
				.attributeGroups(new LinkedHashSet<>())
				.templates(new ArrayList<>())
				.build();

		// 2. Clone Groups and Attributes
		List<SchemaAttributeGroup> oldGroups = this.schemaAttributeGroupRepository.findBySchemaIdWithAttributes(
				originalSchema.getId());
		Map<Long, SchemaAttributeGroup> oldGroupToNewGroup = new HashMap<>();
		Map<Long, SchemaAttribute> oldAttrToNewAttr = new HashMap<>();

		for (SchemaAttributeGroup oldGroup : oldGroups) {
			SchemaAttributeGroup newGroup = SchemaAttributeGroup.builder()
					.schema(forkedSchema)
					.name(oldGroup.getName())
					.label(oldGroup.getLabel())
					.multiple(oldGroup.isMultiple())
					.position(oldGroup.getPosition())
					.attributes(new ArrayList<>())
					.build();

			if (oldGroup.getAttributes() != null) {
				for (var oldAttr : oldGroup.getAttributes()) {
					var newAttr = vn.ptit.snapvocab.domain.SchemaAttribute.builder()
							.group(newGroup)
							.name(oldAttr.getName())
							.label(oldAttr.getLabel())
							.dataType(oldAttr.getDataType())
							.required(oldAttr.isRequired())
							.position(oldAttr.getPosition())
							.build();
					newGroup.getAttributes().add(newAttr);
					oldAttrToNewAttr.put(oldAttr.getId(), newAttr);
				}
			}

			forkedSchema.getAttributeGroups().add(newGroup);
			oldGroupToNewGroup.put(oldGroup.getId(), newGroup);
		}

		// 3. Clone Templates of the original Schema
		List<Template> oldTemplates = this.templateRepository.findBySchemaId(originalSchema.getId());
		Template clonedActiveTemplate = null;
		for (Template oldTemplate : oldTemplates) {
			Template newTemplate = Template.builder()
					.schema(forkedSchema)
					.code(oldTemplate.getCode())
					.name(oldTemplate.getName())
					.isDefault(oldTemplate.isDefault())
					.elements(new ArrayList<>())
					.build();

			if (oldTemplate.getElements() != null) {
				for (TemplateElement oldElement : oldTemplate.getElements()) {
					TemplateElement newElement = TemplateElement.builder()
							.template(newTemplate)
							.position(oldElement.getPosition())
							.type(oldElement.getType())
							.side(oldElement.getSide())
							.sectionLabel(oldElement.getSectionLabel())
							.repeatable(oldElement.getRepeatable())
							.build();

					if (oldElement.getField() != null) {
						TemplateField oldField = oldElement.getField();
						SchemaAttribute oldAttr = oldField.getSchemaAttribute();
						SchemaAttribute newAttr = (oldAttr != null && oldAttr.getId() != null)
								? oldAttrToNewAttr.get(oldAttr.getId()) : null;

						TemplateField newField = TemplateField.builder()
								.element(newElement)
								.schemaAttribute(newAttr)
								.semanticRole(oldField.getSemanticRole())
								.fieldLabel(oldField.getFieldLabel())
								.hideIfEmpty(oldField.isHideIfEmpty())
								.audioAction(oldField.isAudioAction())
								.fontSize(oldField.getFontSize())
								.alignment(oldField.getAlignment())
								.color(oldField.getColor())
								.build();
						newElement.setField(newField);
					}
					newTemplate.getElements().add(newElement);
				}
			}

			forkedSchema.getTemplates().add(newTemplate);

			if (topic.getActiveTemplate() != null && oldTemplate.getId() != null
					&& topic.getActiveTemplate().getId().equals(oldTemplate.getId())) {
				clonedActiveTemplate = newTemplate;
			} else if (clonedActiveTemplate == null && oldTemplate.isDefault()) {
				clonedActiveTemplate = newTemplate;
			}
		}

		Schema savedForkedSchema = this.schemaRepository.save(forkedSchema);

		// 4. Update Topic active template & schema
		if (clonedActiveTemplate != null) {
			topic.setActiveTemplate(clonedActiveTemplate);
		}

		topic.setSchema(savedForkedSchema);
		this.topicRepository.save(topic);

		// 5. Remap TopicItem EAV values for this topic
		List<TopicItem> items = this.topicItemRepository.findByTopicIdWithEav(topicId);
		for (TopicItem item : items) {
			if (item.getGroupInstances() != null) {
				for (TopicItemAttributeGroup gi : item.getGroupInstances()) {
					if (gi.getGroupDefinition() != null && oldGroupToNewGroup.containsKey(
							gi.getGroupDefinition().getId())) {
						gi.setGroupDefinition(oldGroupToNewGroup.get(gi.getGroupDefinition().getId()));
					}
					if (gi.getValues() != null) {
						for (TopicItemAttributeValue val : gi.getValues()) {
							if (val.getAttribute() != null && oldAttrToNewAttr.containsKey(
									val.getAttribute().getId())) {
								val.setAttribute(oldAttrToNewAttr.get(val.getAttribute().getId()));
							}
						}
					}
				}
			}
		}

		LOG.info("Successfully forked schema {} to {} for topicId {}", originalSchema.getId(),
				savedForkedSchema.getId(), topicId);
		return getTopicSchema(topicId);
	}

	@Transactional
	@Caching(
			evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)}
	)
	public TopicDTO setActiveTemplate(Long topicId, Long templateId) {
		this.validateTopicAccess(topicId);
		LOG.info("Setting active template {} for topicId {}", templateId, topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));

		Template template = this.templateRepository.findById(templateId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Template not found with id: " + templateId));

		if (topic.getSchema() == null) {
			throw new ResourceNotFoundException("Topic has no assigned schema");
		}

		if (template.getSchema() == null || !template.getSchema().getId()
				.equals(topic.getSchema().getId())) {
			throw new IllegalArgumentException(
					"Template does not belong to the schema assigned to this topic");
		}

		topic.setActiveTemplate(template);
		Topic saved = this.topicRepository.save(topic);
		return this.topicMapper.toDto(saved);
	}


	public TemplateDTO createTopicTemplate() {
		return null;
	}
}
