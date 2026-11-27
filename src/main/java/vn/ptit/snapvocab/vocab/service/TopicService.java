package vn.ptit.snapvocab.vocab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.dto.AuditTarget;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.audit.service.AuditDiff;
import vn.ptit.snapvocab.badge.service.BadgeService;
import vn.ptit.snapvocab.common.dto.CursorPaginationResponse;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.common.exception.ResourceAlreadyExistException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.service.MissionFacts;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.progress.service.ProgressService;
import vn.ptit.snapvocab.quiz.service.QuizContentResolver;
import vn.ptit.snapvocab.security.AuthoritiesConstants;
import vn.ptit.snapvocab.shop.service.Economy;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;
import vn.ptit.snapvocab.scan.repository.ScanRequestRepository;
import vn.ptit.snapvocab.scan.service.ScanDetectedWords;
import vn.ptit.snapvocab.study.repository.StudyDailyCardRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Schema;
import vn.ptit.snapvocab.vocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.Template;
import vn.ptit.snapvocab.vocab.domain.TemplateElement;
import vn.ptit.snapvocab.vocab.domain.TemplateField;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeGroup;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.vocab.domain.dto.template.TemplateDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicItemDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaAttributeDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.SchemaAttributeGroupDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemAttributeRow;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicSchemaDTO;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.domain.mapper.SchemaMapper;
import vn.ptit.snapvocab.vocab.domain.mapper.SchemaMapperUtils;
import vn.ptit.snapvocab.vocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.vocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.vocab.repository.SchemaRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateFieldRepository;
import vn.ptit.snapvocab.vocab.repository.TemplateRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

	private final TopicItemRepository topicItemRepository;
	private final StudyQueueRepository studyQueueRepository;
	private final StudyDailyCardRepository studyDailyCardRepository;
	private final TopicRepository topicRepository;
	private final CollectionRepository collectionRepository;
	private final SchemaRepository schemaRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;
	private final TemplateRepository templateRepository;
	private final TemplateFieldRepository templateFieldRepository;
	private final TopicItemAttributeValueRepository topicItemAttributeValueRepository;
	private final UserRepository userRepository;
	private final TopicMapper topicMapper;
	private final TopicSchemaMapper topicSchemaMapper;
	private final SchemaMapper schemaMapper;
	private final MissionTracker missionTracker;
	private final BadgeService badgeService;
	private final QuizContentResolver quizContentResolver;
	private final ScanRequestRepository scanRequestRepository;
	private final ProgressService progressService;
	private final AdminAuditService audit;
	private final ObjectMapper objectMapper;
	private final Logger LOG = LoggerFactory.getLogger(TopicService.class);

	private static final List<String> TOPIC_CREATE_FIELDS = List.of("name", "translation", "description", "schema",
			"template");

	/**
	 * Retrieves paginated vocabulary items for a given topic with EAV attributes aggregated into a
	 * clean, client-friendly structure, including the topic schema definition.
	 * <p>
	 * Query Strategy:
	 * <ol>
	 * <li>Fetch paginated {@code topicItemIds} for the given topic ID.</li>
	 * <li>Batch fetch attribute rows for those IDs (avoiding N+1 queries).</li>
	 * <li>Aggregate rows into dynamic item properties and extract schema metadata
	 * with English labels.</li>
	 * </ol>
	 *
	 * @param topicId  The topic ID to fetch items for.
	 * @param pageable Pagination parameters (page, size, sort).
	 * @return A {@link PaginationObject} containing the aggregated items, pagination meta, and schema
	 * metadata.
	 */
	@Cacheable(cacheNames = "topic-items", key = "#topicId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize")
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

		return PaginationObject.<List<TopicItemData>>builder()
				.data(buildTopicItemDataList(topicItemIds.getContent()))
				.meta(meta)
				.build();
	}

	public TopicItemData getTopicItem(Long topicId, Long itemId) {
		validateTopicAccess(topicId);

		TopicItem item = topicItemRepository.findByIdAndTopicId(itemId, topicId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Topic item not found with id: " + itemId + " in topic: " + topicId));

		TopicItemData itemData = buildTopicItemDataList(List.of(itemId)).get(0);
		itemData.setCreatedAt(item.getCreatedAt());
		return itemData;
	}

	private List<TopicItemData> buildTopicItemDataList(List<Long> topicItemIds) {
		if (topicItemIds == null || topicItemIds.isEmpty()) {
			return new ArrayList<>();
		}

		// Step 2: Batch fetch EAV attribute rows for the item IDs
		List<TopicItemAttributeRow> rows = this.topicItemRepository.findAttributeRows(topicItemIds);

		// Step 3: Aggregate item data values by single vs multiple groups
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
						k -> new LinkedHashMap<String, Object>());
				if (attrName != null) {
					groupAttrs.put(attrName, value);
				}
			} else {
				// Multiple group: collect instances by instanceId preserving position order
				Map<String, Map<Long, Map<String, Object>>> itemMultiGroups = multiGroupsByItem.computeIfAbsent(
						itemId,
						k -> new LinkedHashMap<>());
				Map<Long, Map<String, Object>> instances = itemMultiGroups.computeIfAbsent(groupName,
						k -> new LinkedHashMap<>());
				if (instanceId != null) {
					Map<String, Object> instanceAttrs = instances.computeIfAbsent(
							instanceId,
							k -> new LinkedHashMap<>());
					if (attrName != null) {
						instanceAttrs.put(attrName, value);
					}
				}
			}
		}

		// Step 4: Assemble TopicItemData list in original ID order
		List<TopicItemData> items = new ArrayList<>();
		for (Long itemId : topicItemIds) {
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

		return items;
	}

	/** Builds item DTOs for another application module while preserving requested order. */
	public List<TopicItemData> getTopicItemsByIds(List<Long> topicItemIds) {
		return buildTopicItemDataList(topicItemIds);
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
							.build());

			String attrName = row.getAttributeName();
			if (attrName != null) {
				Map<String, SchemaAttributeDTO> groupAttrs = attributeMapByGroup.computeIfAbsent(groupName,
						k -> new LinkedHashMap<>());

				groupAttrs.computeIfAbsent(
						attrName,
						name -> SchemaAttributeDTO.builder()
								.attributeId(row.getAttributeId())
								.name(name)
								.label(SchemaMapperUtils.resolveLabel(row.getAttributeLabel(), name))
								.dataType(row.getDataType())
								.required(row.isAttributeRequired())
								.position(row.getAttributePosition())
								.build());
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
	@Cacheable(cacheNames = "topic-schema", key = "#topicId")
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

	@Cacheable(cacheNames = "topic-tree", key = "#collectionId + ':parent:' + (#parentId != null ? #parentId : 'root') + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize")
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
	@Caching(evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)})
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
		Map<String, Object> created = new LinkedHashMap<>();
		created.put("name", saved.getName());
		created.put("translation", saved.getTranslation());
		created.put("description", saved.getDescription());
		created.put("schema", schema.getName());
		created.put("template", activeTemplate == null ? null : activeTemplate.getName());
		auditTopic(AuditAction.TOPIC_CREATED, saved, () -> AuditDiff.of(objectMapper, null, created, TOPIC_CREATE_FIELDS));

		return topicMapper.toDto(saved);
	}

	/**
	 * Admin audit row for system content or an admin editing a learner's topic; a learner on their own collection is
	 * not audited (admin_audit_log_backend.md §4.4.4).
	 */
	private void auditTopic(AuditAction action, Topic topic, Supplier<List<AuditChange>> diff) {
		if (topic.getCollection() == null || !CollectionService.auditable(topic.getCollection())) {
			return;
		}
		List<AuditChange> changes = diff.get();
		if (changes.isEmpty()) {
			return;
		}
		List<Long> parents = new ArrayList<>(List.of(topic.getCollection().getId()));
		if (topic.getParent() != null) {
			Topic root = topic.getParent();
			while (root.getParent() != null) {
				root = root.getParent();
			}
			parents.add(root.getId());
		}
		audit.record(action, new AuditTarget(AuditTargetType.TOPIC, String.valueOf(topic.getId()), topic.getName(), null,
				parents), changes, null, null, null);
	}

	private List<AuditChange> renamed(String field, String before, String after) {
		return Objects.equals(before, after) ? List.of()
				: List.of(AuditDiff.change(objectMapper, field, before, after));
	}

	private static String nameOf(Schema schema) {
		return schema == null ? null : schema.getName();
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

	/** The current user when {@code topic} is in their own USER collection, else null (admin or system edits). */
	private static Long ownUserCollectionLearner(Topic topic) {
		Collection collection = topic.getCollection();
		if (collection == null || collection.getType() != CollectionType.USER || collection.getOwner() == null) {
			return null;
		}
		Long ownerId = collection.getOwner().getId();
		return SecurityUtils.getCurrentUserId().filter(ownerId::equals).orElse(null);
	}

	public void validateTopicWriteAccess(Topic topic) {
		Collection collection = topic.getCollection();
		if (collection == null) {
			return;
		}
		if (collection.getType() == CollectionType.SYSTEM) {
			if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
				throw new AccessDeniedException(
						"Only administrators can modify topics in system collections");
			}
		} else {
			Long currentUserId = SecurityUtils.getCurrentUserId()
					.or(() -> SecurityUtils.getCurrentUserLogin()
							.flatMap(userRepository::findOneByEmailIgnoreCase).map(User::getId))
					.orElse(null);

			if (currentUserId == null) {
				throw new AccessDeniedException("Authentication required to modify this topic");
			}
			boolean isOwner =
					collection.getOwner() != null && collection.getOwner().getId().equals(currentUserId);
			boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
			if (!isOwner && !isAdmin) {
				throw new AccessDeniedException("You do not have permission to modify this topic");
			}
		}
	}

	@Transactional
	@CacheEvict(cacheNames = "topic-schema", key = "#topicId")
	public TopicSchemaDTO createTopicSchema(Long topicId,
			CreateTopicSchemaDTO createTopicSchemaDTO) {
		LOG.info("Creating schema for topicId: {}", topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

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

		String previous = nameOf(topic.getSchema());
		topic.setSchema(savedSchema);
		this.topicRepository.save(topic);
		auditTopic(AuditAction.TOPIC_SCHEMA_CHANGED, topic, () -> renamed("schema", previous, savedSchema.getName()));

		return getTopicSchema(topicId);
	}

	@Transactional
	@CacheEvict(cacheNames = "topic-schema", key = "#topicId")
	public TopicSchemaDTO assignSchemaToTopic(Long topicId, Long schemaId) {
		LOG.info("Assigning schemaId {} to topicId {}", schemaId, topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

		Schema schema = this.schemaRepository.findById(schemaId)
				.orElseThrow(() -> new ResourceNotFoundException("Schema not found with id: " + schemaId));

		Schema previous = topic.getSchema();
		topic.setSchema(schema);
		this.topicRepository.save(topic);
		if (previous == null || !previous.getId().equals(schema.getId())) {
			auditTopic(AuditAction.TOPIC_SCHEMA_CHANGED, topic,
					() -> List.of(AuditDiff.change(objectMapper, "schema", nameOf(previous), schema.getName())));
		}

		return getTopicSchema(topicId);
	}

	@Transactional
	@CacheEvict(cacheNames = "topic-schema", key = "#topicId")
	public TopicSchemaDTO forkTopicSchema(Long topicId) {
		LOG.info("Forking schema for topicId: {}", topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

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
					var newAttr = vn.ptit.snapvocab.vocab.domain.SchemaAttribute.builder()
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
								? oldAttrToNewAttr.get(oldAttr.getId())
								: null;

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
								.hidden(oldField.isHidden())
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
		auditTopic(AuditAction.TOPIC_SCHEMA_CHANGED, topic,
				() -> List.of(AuditDiff.change(objectMapper, "schema", originalSchema.getName(), savedForkedSchema.getName())));

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
	@Caching(evict = {@CacheEvict(cacheNames = "topic-tree", allEntries = true)})
	public TopicDTO setActiveTemplate(Long topicId, Long templateId) {
		LOG.info("Setting active template {} for topicId {}", templateId, topicId);

		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

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

		Template previous = topic.getActiveTemplate();
		topic.setActiveTemplate(template);
		Topic saved = this.topicRepository.save(topic);
		if (previous == null || !previous.getId().equals(template.getId())) {
			auditTopic(AuditAction.TOPIC_TEMPLATE_CHANGED, saved, () -> List.of(AuditDiff.change(objectMapper, "template",
					previous == null ? null : previous.getName(), template.getName())));
		}
		return this.topicMapper.toDto(saved);
	}

	public TemplateDTO createTopicTemplate() {
		return null;
	}

	/**
	 * READ_COMMITTED like the other Daily Mission producers: the topic is read before the users lock is taken, and
	 * under REPEATABLE READ that early snapshot would hide a mission set committed while this call waited for the lock.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@CacheEvict(cacheNames = "topic-items", allEntries = true)
	public TopicItemData addTopicItem(Long topicId, CreateTopicItemDTO createTopicItemDTO) {
		return addTopicItem(topicId, createTopicItemDTO, null);
	}

	/**
	 * {@code scanRequestId}: the scan the word was saved from. It feeds the SCAN_WORD_SAVED mission and the Admin scan
	 * funnel ({@code source_scan_id}, admin_analytics_backend.md §7); an unknown, foreign or unfinished scan, or a word
	 * the scan did not detect, still saves the word (daily_mission.md §6.1).
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@CacheEvict(cacheNames = "topic-items", allEntries = true)
	public TopicItemData addTopicItem(Long topicId, CreateTopicItemDTO createTopicItemDTO, String scanRequestId) {
		Topic topic = this.topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));

		validateTopicWriteAccess(topic);
		// Daily Mission counts only words a learner adds to their own USER collection (daily_mission.md §5.5).
		Long learnerId = ownUserCollectionLearner(topic);
		if (learnerId != null) {
			Economy.lockUser(userRepository, learnerId);
		}

		// kiểm tra schema của topic
		if (topic.getSchema() == null) {
			throw new BadRequestAlertException("Topic has no assigned schema");
		}

		List<SchemaAttributeGroup> schemaGroups = schemaAttributeGroupRepository.findBySchemaIdWithAttributes(
				topic.getSchema().getId());

		Map<String, Object> inputGroups =
				createTopicItemDTO.getGroups() != null ? createTopicItemDTO.getGroups()
						: Collections.emptyMap();

		TopicItem topicItem = TopicItem.builder()
				.topic(topic)
				.groupInstances(new ArrayList<>())
				.build();

		Map<String, Object> responseGroups = new LinkedHashMap<>();

		// xử lý mapping và validate từng SchemaAttributeGroup
		for (var groupDef : schemaGroups) {
			String groupName = groupDef.getName();
			Object rawGroupData = inputGroups.get(groupName);

			if (!groupDef.isMultiple()) {
				// group đơn
				Map<String, Object> attrData = Collections.emptyMap();
				if (rawGroupData != null) {
					if (!(rawGroupData instanceof Map<?, ?> map)) {
						throw new BadRequestAlertException("Group '" + groupName + "' must be a JSON object");
					}
					@SuppressWarnings("unchecked")
					Map<String, Object> casted = (Map<String, Object>) map;
					attrData = casted;
				}

				TopicItemAttributeGroup groupInstance = TopicItemAttributeGroup.builder()
						.item(topicItem)
						.groupDefinition(groupDef)
						.position((short) 1)
						.values(new ArrayList<>())
						.build();

				// Validate & add values
				Map<String, Object> savedAttrs = buildAndValidateValues(groupInstance,
						groupDef.getAttributes(), attrData);
				if (!groupInstance.getValues().isEmpty()) {
					topicItem.getGroupInstances().add(groupInstance);
				}
				if (!savedAttrs.isEmpty()) {
					responseGroups.put(groupName, savedAttrs);
				}

			} else {
				// group lặp
				if (rawGroupData != null) {
					if (!(rawGroupData instanceof List<?> list)) {
						throw new BadRequestAlertException("Group '" + groupName + "' must be a JSON array");
					}

					short pos = 1;
					List<Map<String, Object>> groupInstanceResponses = new ArrayList<>();
					for (Object itemObj : list) {
						if (!(itemObj instanceof Map<?, ?> map)) {
							throw new BadRequestAlertException(
									"Item in group '" + groupName + "' must be a JSON object");
						}

						@SuppressWarnings("unchecked")
						Map<String, Object> attrData = (Map<String, Object>) map;

						TopicItemAttributeGroup groupInstance = TopicItemAttributeGroup.builder()
								.item(topicItem)
								.groupDefinition(groupDef)
								.position(pos++)
								.values(new ArrayList<>())
								.build();

						Map<String, Object> savedAttrs = buildAndValidateValues(groupInstance,
								groupDef.getAttributes(), attrData);
						if (!groupInstance.getValues().isEmpty()) {
							topicItem.getGroupInstances().add(groupInstance);
						}
						if (!savedAttrs.isEmpty()) {
							groupInstanceResponses.add(savedAttrs);
						}
					}
					if (!groupInstanceResponses.isEmpty()) {
						responseGroups.put(groupName, groupInstanceResponses);
					}
				}
			}
		}

		if (learnerId != null) {
			rejectDuplicateWord(topic, topicItem);
		}

		Instant actionInstant = Economy.now();
		LocalDate missionDate = MissionTracker.missionDate(actionInstant);
		if (learnerId != null) {
			missionTracker.ensureAssigned(learnerId, missionDate, actionInstant);
		}
		ScanRequest sourceScan = scanSource(learnerId, topic, scanRequestId);
		topicItem.setSourceScanId(sourceScan == null ? null : sourceScan.getId());
		TopicItem savedItem = this.topicItemRepository.save(topicItem);
		if (learnerId != null) {
			byte[] wordHash = quizContentResolver.resolveQuizReady(topic, savedItem.getId())
					.map(content -> MissionTracker.wordHash(content.targetWord()))
					.orElse(null);
			missionTracker.apply(learnerId, missionDate, MissionTriggerEvent.TOPIC_ITEM_CREATED,
					"TOPIC_ITEM:" + savedItem.getId(), MissionFacts.topicItem(wordHash), actionInstant);
			if (wordHash != null) {
				badgeService.recordWordCredit(learnerId, wordHash, actionInstant);
			}
			if (wordHash != null && sourceScan != null && scanDetected(sourceScan, wordHash)) {
				// One credit per word per scan: saving it again, or into another topic, does not count twice.
				String sourceKey = "SCAN_SAVE:" + scanRequestId + ":" + HexFormat.of().formatHex(wordHash);
				missionTracker.apply(learnerId, missionDate, MissionTriggerEvent.SCAN_WORD_SAVED, sourceKey,
						MissionFacts.NONE, actionInstant);
				// Saving a scanned word is a learning activity (PO 2026-10-22): streak and KEEP_DAILY_STREAK.
				progressService.record(learnerId, LearningEventType.WORD_SCANNED_AND_SAVED, sourceKey,
						Map.of("topicItemId", savedItem.getId(), "scanRequestId", scanRequestId), actionInstant);
			}
		}
		if (learnerId == null) {
			auditTopic(AuditAction.TOPIC_ITEM_ADDED, topic, () -> List.of(AuditDiff.change(objectMapper, "item", null,
					itemWord(topic, savedItem.getId(), inputGroups))));
		}
		return TopicItemData.builder()
				.topicItemId(savedItem.getId())
				.createdAt(savedItem.getCreatedAt())
				.groups(responseGroups)
				.build();
	}

	/** Error code of a word saved twice into one topic (buss_mainflow.md BF-07 AF-07.1). */
	public static final String WORD_ALREADY_IN_TOPIC = "WORD_ALREADY_IN_TOPIC";

	/**
	 * AF-07.1: one learner does not save the same word twice into one topic. The word is whatever the active template
	 * binds to TARGET_WORD, compared trimmed and case-insensitively; a template with no such binding cannot tell, so
	 * it saves as before.
	 */
	void rejectDuplicateWord(Topic topic, TopicItem newItem) {
		if (topic.getActiveTemplate() == null) {
			return;
		}
		Set<Long> targetAttributeIds = templateFieldRepository.findByTemplateId(topic.getActiveTemplate().getId())
				.stream()
				.filter(field -> field.getSemanticRole() == SemanticRole.TARGET_WORD && field.getSchemaAttribute() != null)
				.map(field -> field.getSchemaAttribute().getId())
				.collect(Collectors.toSet());
		if (targetAttributeIds.isEmpty()) {
			return;
		}
		Set<String> newWords = newItem.getGroupInstances().stream()
				.flatMap(group -> group.getValues().stream())
				.filter(value -> value.getAttribute() != null && targetAttributeIds.contains(value.getAttribute().getId()))
				.map(value -> normalizeWord(value.getValue()))
				.filter(word -> !word.isEmpty())
				.collect(Collectors.toSet());
		if (newWords.isEmpty()) {
			return;
		}
		boolean duplicate = topicItemAttributeValueRepository.findItemValues(List.of(topic.getId()), targetAttributeIds)
				.stream()
				.anyMatch(row -> newWords.contains(normalizeWord(row.value())));
		if (duplicate) {
			throw new ResourceAlreadyExistException(WORD_ALREADY_IN_TOPIC);
		}
	}

	private static String normalizeWord(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	/** The word the audit row shows: the Quiz target, else main.word as sent, else "#id" (§4.4.4). */
	private String itemWord(Topic topic, Long topicItemId, Map<String, Object> inputGroups) {
		return quizContentResolver.resolveQuizReady(topic, topicItemId)
				.map(content -> content.targetWord())
				.or(() -> inputGroups.get("main") instanceof Map<?, ?> main && main.get("word") instanceof String word
						&& !word.isBlank() ? Optional.of(word) : Optional.empty())
				.orElse("#" + topicItemId);
	}

	/**
	 * The caller's own finished scan, only for a word saved to their own USER collection; anything else is dropped
	 * with a WARN, so saving never fails over stats (admin_analytics_backend.md §7, G3).
	 */
	private ScanRequest scanSource(Long learnerId, Topic topic, String scanRequestId) {
		if (scanRequestId == null || scanRequestId.isBlank()) {
			return null;
		}
		ScanRequest scan = learnerId == null ? null : scanRequestRepository.findByIdAndUser_Id(scanRequestId, learnerId)
				.filter(s -> s.getStatus() == ScanStatus.DONE)
				.orElse(null);
		if (scan == null) {
			LOG.warn("Ignored scanRequestId={} for topic {}", scanRequestId, topic.getId());
		}
		return scan;
	}

	private static boolean scanDetected(ScanRequest scan, byte[] wordHash) {
		return ScanDetectedWords.of(scan.getResultJson()).stream()
				.anyMatch(word -> Arrays.equals(MissionTracker.wordHash(word), wordHash));
	}

	@Transactional
	@CacheEvict(cacheNames = "topic-items", allEntries = true)
	public TopicItemData updateTopicItem(Long topicId, Long itemId,
			CreateTopicItemDTO updateTopicItemDTO) {
		Topic topic = topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

		TopicItem topicItem = topicItemRepository.findByIdAndTopicId(itemId, topicId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Topic item not found with id: " + itemId + " in topic: " + topicId));

		if (topic.getSchema() == null) {
			throw new BadRequestAlertException("Topic has no assigned schema");
		}

		List<SchemaAttributeGroup> schemaGroups = schemaAttributeGroupRepository.findBySchemaIdWithAttributes(
				topic.getSchema().getId());
		Map<String, Object> inputGroups = updateTopicItemDTO.getGroups() != null
				? updateTopicItemDTO.getGroups()
				: Collections.emptyMap();

		// Replace all EAV values while retaining the TopicItem row and its ID (and FSRS link).
		topicItem.getGroupInstances().clear();
		// Delete old group rows before inserting replacements with the same unique positions.
		topicItemRepository.flush();
		Map<String, Object> responseGroups = replaceItemGroups(topicItem, schemaGroups, inputGroups);

		TopicItem savedItem = topicItemRepository.save(topicItem);
		return TopicItemData.builder()
				.topicItemId(savedItem.getId())
				.createdAt(savedItem.getCreatedAt())
				.groups(responseGroups)
				.build();
	}

	@Transactional
	@CacheEvict(cacheNames = "topic-items", allEntries = true)
	public void deleteTopicItem(Long topicId, Long itemId) {
		Topic topic = topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
		validateTopicWriteAccess(topic);

		TopicItem topicItem = topicItemRepository.findByIdAndTopicId(itemId, topicId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Topic item not found with id: " + itemId + " in topic: " + topicId));

		studyQueueRepository.deleteAllByTopicItemId(itemId);
		studyDailyCardRepository.deleteAllByTopicItemId(itemId);
		topicItemRepository.delete(topicItem);
	}

	private Map<String, Object> replaceItemGroups(TopicItem topicItem,
			List<SchemaAttributeGroup> schemaGroups, Map<String, Object> inputGroups) {
		Map<String, Object> responseGroups = new LinkedHashMap<>();
		for (SchemaAttributeGroup groupDef : schemaGroups) {
			String groupName = groupDef.getName();
			Object rawGroupData = inputGroups.get(groupName);
			if (groupDef.isMultiple()) {
				replaceMultipleGroup(topicItem, groupDef, rawGroupData, responseGroups);
			} else {
				replaceSingleGroup(topicItem, groupDef, rawGroupData, responseGroups);
			}
		}
		return responseGroups;
	}

	private void replaceSingleGroup(TopicItem topicItem, SchemaAttributeGroup groupDef,
			Object rawGroupData, Map<String, Object> responseGroups) {
		Map<String, Object> attrData = readSingleGroupInput(groupDef.getName(), rawGroupData);
		TopicItemAttributeGroup groupInstance = newTopicItemAttributeGroup(topicItem, groupDef,
				(short) 1);
		Map<String, Object> savedAttrs = buildAndValidateValues(groupInstance,
				groupDef.getAttributes(), attrData);
		if (!groupInstance.getValues().isEmpty()) {
			topicItem.getGroupInstances().add(groupInstance);
		}
		if (!savedAttrs.isEmpty()) {
			responseGroups.put(groupDef.getName(), savedAttrs);
		}
	}

	private void replaceMultipleGroup(TopicItem topicItem, SchemaAttributeGroup groupDef,
			Object rawGroupData, Map<String, Object> responseGroups) {
		if (rawGroupData == null) {
			return;
		}
		if (!(rawGroupData instanceof List<?> instances)) {
			throw new BadRequestAlertException(
					"Group '" + groupDef.getName() + "' must be a JSON array");
		}

		List<Map<String, Object>> instanceResponses = new ArrayList<>();
		short position = 1;
		for (Object instanceData : instances) {
			Map<String, Object> attrData = readGroupInstanceInput(groupDef.getName(), instanceData);
			TopicItemAttributeGroup groupInstance = newTopicItemAttributeGroup(topicItem, groupDef,
					position++);
			Map<String, Object> savedAttrs = buildAndValidateValues(groupInstance,
					groupDef.getAttributes(), attrData);
			if (!groupInstance.getValues().isEmpty()) {
				topicItem.getGroupInstances().add(groupInstance);
			}
			if (!savedAttrs.isEmpty()) {
				instanceResponses.add(savedAttrs);
			}
		}
		if (!instanceResponses.isEmpty()) {
			responseGroups.put(groupDef.getName(), instanceResponses);
		}
	}

	private TopicItemAttributeGroup newTopicItemAttributeGroup(TopicItem topicItem,
			SchemaAttributeGroup groupDef, short position) {
		return TopicItemAttributeGroup.builder()
				.item(topicItem)
				.groupDefinition(groupDef)
				.position(position)
				.values(new ArrayList<>())
				.build();
	}

	private Map<String, Object> readGroupInstanceInput(String groupName, Object instanceData) {
		if (!(instanceData instanceof Map<?, ?> map)) {
			throw new BadRequestAlertException(
					"Item in group '" + groupName + "' must be a JSON object");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> attrData = (Map<String, Object>) map;
		return attrData;
	}

	private Map<String, Object> readSingleGroupInput(String groupName, Object rawGroupData) {
		if (rawGroupData == null) {
			return Collections.emptyMap();
		}
		if (!(rawGroupData instanceof Map<?, ?> map)) {
			throw new BadRequestAlertException("Group '" + groupName + "' must be a JSON object");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> attrData = (Map<String, Object>) map;
		return attrData;
	}

	private Map<String, Object> buildAndValidateValues(TopicItemAttributeGroup groupInstance,
			List<SchemaAttribute> attributes, Map<String, Object> attrData) {
		Map<String, Object> savedValues = new LinkedHashMap<>();
		if (attributes == null) {
			return savedValues;
		}

		for (SchemaAttribute attr : attributes) {
			String attrName = attr.getName();
			Object rawVal = attrData.get(attrName);

			// Kiểm tra bắt buộc (required)
			if (attr.isRequired() && (rawVal == null || rawVal.toString().trim().isEmpty())) {
				throw new BadRequestAlertException("Field '" + attrName + "' in group '"
						+ groupInstance.getGroupDefinition().getName() + "' is required");
			}

			if (rawVal != null) {
				String strVal = rawVal.toString().trim();
				if (!strVal.isEmpty()) {
					// Validate datatype
					validateDataType(attr, strVal);

					TopicItemAttributeValue valEntity = TopicItemAttributeValue.builder()
							.groupInstance(groupInstance)
							.attribute(attr)
							.value(strVal)
							.build();

					groupInstance.getValues().add(valEntity);
					savedValues.put(attrName, strVal);
				}
			}
		}
		return savedValues;
	}

	private void validateDataType(SchemaAttribute attr, String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		if (attr.getDataType() == null) {
			return;
		}
		try {
			switch (attr.getDataType()) {
				case NUMBER -> Double.parseDouble(value);
				case BOOLEAN -> {
					if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
						throw new IllegalArgumentException();
					}
				}
				case DATE -> LocalDate.parse(value);
				case TEXT, AUDIO, IMAGE -> {
					// kiểm tra độ dài tối đa hoặc regex URL nếu cần
				}
			}
		} catch (Exception e) {
			throw new BadRequestAlertException(
					"Invalid value '" + value + "' for type " + attr.getDataType() + " in field '"
							+ attr.getName() + "'");
		}
	}

	public CursorPaginationResponse<List<TopicItemData>> getTopicItemsByCursor(Long topicId,
			Long cursor, Long limit) {
		validateTopicAccess(topicId);

		// lấy limit + 1 để lấy về kết quả hasMore
		long fetchSize = limit + 1;
		List<Long> topicItemIds =
				(cursor == null) ? this.topicItemRepository.findTopIdsByTopicId(topicId, fetchSize)
						: this.topicItemRepository.findIdsByTopicIdAndCursor(topicId, cursor, fetchSize);

		boolean hasMore = !topicItemIds.isEmpty() && topicItemIds.size() > limit;
		List<Long> targetIds = hasMore ? topicItemIds.subList(0, limit.intValue()) : topicItemIds;

		// Lấy nextCursor
		Long nextCursor = targetIds.isEmpty() ? null : targetIds.get(targetIds.size() - 1);

		// The first page also says how many items the topic holds; later pages skip the count.
		Long total = cursor == null ? this.topicItemRepository.countByTopicIdIn(List.of(topicId)) : null;

		if (targetIds.isEmpty()) {
			return CursorPaginationResponse.<List<TopicItemData>>builder()
					.data(Collections.emptyList())
					.nextCursor(null)
					.hasNext(false)
					.total(total)
					.build();
		}

		return CursorPaginationResponse.<List<TopicItemData>>builder()
				.hasNext(hasMore)
				.nextCursor(nextCursor)
				.data(this.buildTopicItemDataList(targetIds))
				.total(total)
				.build();
	}
}
