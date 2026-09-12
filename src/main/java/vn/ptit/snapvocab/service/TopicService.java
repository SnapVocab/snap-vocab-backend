package vn.ptit.snapvocab.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import vn.ptit.snapvocab.domain.SchemaAttribute;
import vn.ptit.snapvocab.domain.SchemaAttributeGroup;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.TopicSchema;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.common.Meta;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateSchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.CreateTopicSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeDTO;
import vn.ptit.snapvocab.domain.dto.topic.SchemaAttributeGroupDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemAttributeRow;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.domain.dto.topic.TopicSchemaDTO;
import vn.ptit.snapvocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.domain.mapper.SchemaMapperUtils;
import vn.ptit.snapvocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.domain.mapper.TopicSchemaMapper;
import vn.ptit.snapvocab.repository.CollectionRepository;
import vn.ptit.snapvocab.repository.SchemaAttributeGroupRepository;
import vn.ptit.snapvocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.repository.TopicRepository;
import vn.ptit.snapvocab.repository.TopicSchemaRepository;
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
	private final TopicSchemaRepository topicSchemaRepository;
	private final SchemaAttributeGroupRepository schemaAttributeGroupRepository;
	private final UserRepository userRepository;
	private final TopicMapper topicMapper;
	private final TopicSchemaMapper topicSchemaMapper;
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
					.schema(getTopicSchema(topicId))
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
				.schema(schema)
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
		TopicSchema schema = this.topicSchemaRepository.findByTopicId(topicId)
				.orElse(null);

		if (schema == null) {
			return TopicSchemaDTO.builder()
					.topicId(topicId)
					.groups(new ArrayList<>())
					.build();
		}

		List<SchemaAttributeGroup> groups = this.schemaAttributeGroupRepository.findByTopicIdWithAttributes(topicId);
		return this.topicSchemaMapper.toDto(schema, groups);
	}


	@Cacheable(
			cacheNames = "topic-tree",
			key = "#collectionId"
	)
	public List<TopicDTO> getTopicsByCollection(Long collectionId) {
		Collection collection = this.collectionRepository.findById(collectionId)
				.orElseThrow(
						() -> new ResourceNotFoundException("Collection not found with id: " + collectionId));

		validateCollectionAccess(collection);

		List<Topic> allTopicsByCollection = this.topicRepository.findByCollectionId(collectionId);

		return buildTopicsTree(allTopicsByCollection);
	}

	private List<TopicDTO> buildTopicsTree(List<Topic> topics) {
		Map<Long, TopicDTO> topicMap = new HashMap<>();

		// create DTO for all nodes
		for (Topic topic : topics) {
			TopicDTO dto = topicMapper.toDto(topic);
			dto.setChildren(new ArrayList<>());
			topicMap.put(topic.getId(), dto);
		}

		List<TopicDTO> roots = new ArrayList<>();

		for (Topic topic : topics) {
			TopicDTO current = topicMap.get(topic.getId());

			Long parentId = current.getParentId();
			if (parentId == null) {
				roots.add(current);
				continue;
			}

			TopicDTO parent = topicMap.get(parentId);
			if (parent != null) {
				parent.getChildren().add(current);
			}
		}

		return roots;
	}

	@Transactional
	@Caching(
			evict = {@CacheEvict(cacheNames = "topic-tree", key = "#collectionId")}
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

		Topic topic = Topic.builder()
				.collection(collection)
				.parent(createTopicDTO.getParentId() != null ? topicRepository.findById(
						createTopicDTO.getParentId()).orElse(null) : null)
				.children(new ArrayList<>())
				.name(createTopicDTO.getName())
				.translation(createTopicDTO.getTranslation())
				.description(createTopicDTO.getDescription())
				.descriptionTranslation(createTopicDTO.getDescriptionTranslation())
				.items(new ArrayList<>())
				.build();

		Topic saved = topicRepository.save(topic);

		// Each Topic has One Schema
		TopicSchema defaultSchema = TopicSchema.builder()
				.topic(saved)
				.attributeGroups(new LinkedHashSet<>())
				.build();
		topicSchemaRepository.save(defaultSchema);
		saved.setSchema(defaultSchema);

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

		TopicSchema existingSchema = this.topicSchemaRepository.findByTopicId(topicId).orElse(null);
		List<SchemaAttributeGroup> existingGroups = this.schemaAttributeGroupRepository.findByTopicIdWithAttributes(topicId);
		if (!existingGroups.isEmpty()) {
			throw new ResourceAlreadyExistException("Topic already has a schema with groups for topicId: " + topicId);
		}

		TopicSchema schema;
		if (existingSchema != null) {
			schema = existingSchema;
			this.topicSchemaMapper.updateEntityFromDto(createTopicSchemaDTO, schema);
		} else {
			schema = this.topicSchemaMapper.toEntity(createTopicSchemaDTO);
			schema.setTopic(topic);
		}

		TopicSchema saved = this.topicSchemaRepository.save(schema);
		return getTopicSchema(saved.getTopic().getId());
	}
}
