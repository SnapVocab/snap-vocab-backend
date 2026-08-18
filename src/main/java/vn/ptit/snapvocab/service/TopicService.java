package vn.ptit.snapvocab.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.domain.Topic;
import vn.ptit.snapvocab.domain.dto.common.Meta;
import vn.ptit.snapvocab.domain.dto.common.PaginationObject;
import vn.ptit.snapvocab.domain.dto.topic.TopicAttributeSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicGroupSchemaDTO;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemAttributeRow;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.domain.mapper.TopicMapper;
import vn.ptit.snapvocab.repository.TopicItemRepository;
import vn.ptit.snapvocab.repository.TopicRepository;

@Service
@RequiredArgsConstructor
public class TopicService {

	private final TopicItemRepository topicItemRepository;
	private final TopicRepository topicRepository;
	private final TopicMapper topicMapper;
	private final Logger LOG = LoggerFactory.getLogger(TopicService.class);

	/**
	 * Retrieves paginated vocabulary items for a given topic with EAV attributes aggregated
	 * into a clean, client-friendly structure, including the topic schema definition.
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
	 * @return A {@link PaginationObject} containing the aggregated items, pagination meta, and schema metadata.
	 */
	@Cacheable(
			cacheNames = "topic-items",
			key = "#topicId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize"
	)
	public PaginationObject<List<TopicItemData>> getTopicItems(Long topicId, Pageable pageable) {
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

		// Step 3: Aggregate rows by single vs multiple groups, and build schema definition
		Map<Long, Map<String, Object>> singleGroupsByItem = new LinkedHashMap<>();
		Map<Long, Map<String, Map<Long, Map<String, Object>>>> multiGroupsByItem = new LinkedHashMap<>();
		Map<String, TopicGroupSchemaDTO> schema = new LinkedHashMap<>();

		for (TopicItemAttributeRow row : rows) {
			Long itemId = row.getTopicItemId();
			String groupName = row.getGroupName();
			boolean multiple = row.isGroupMultiple();
			Long instanceId = row.getGroupInstanceId();
			String attrName = row.getAttributeName();
			String value = row.getValue();

			// Build dynamic schema metadata
			TopicGroupSchemaDTO groupSchema = schema.computeIfAbsent(
					groupName,
					name -> TopicGroupSchemaDTO.builder()
							.groupId(row.getGroupId())
							.name(name)
							.label(resolveLabel(row.getGroupLabel(), name))
							.multiple(multiple)
							.position(row.getGroupInstancePosition())
							.build()
			);

			if (attrName != null) {
				groupSchema.getAttributes().computeIfAbsent(
						attrName,
						name -> TopicAttributeSchemaDTO.builder()
								.attributeId(row.getAttributeId())
								.name(name)
								.label(resolveLabel(row.getAttributeLabel(), name))
								.dataType(row.getDataType())
								.required(row.isAttributeRequired())
								.position(row.getAttributePosition())
								.build()
				);
			}

			// Aggregate item data values
			if (!multiple) {
				// Single group: flatten directly into a Map<attribute, value>
				Map<String, Object> itemGroups = singleGroupsByItem.computeIfAbsent(itemId, k -> new LinkedHashMap<>());
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

		// Step 4: Assemble TopicItemData list in original pagination order
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
	 * Resolves a human-readable English label from database label, falling back to Title Case of snake_case name.
	 */
	private String resolveLabel(String dbLabel, String name) {
		if (dbLabel != null && !dbLabel.isBlank()) {
			return dbLabel.trim();
		}
		if (name == null || name.isBlank()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("_")) {
			if (!part.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(Character.toUpperCase(part.charAt(0)));
				if (part.length() > 1) {
					sb.append(part.substring(1));
				}
			}
		}
		return sb.toString();
	}

	@Cacheable(
			cacheNames = "topic-tree",
			key = "#collectionId"
	)
	public List<TopicDTO> getTopicsByCollection(Long collectionId) {
		List<Topic> allTopicsByCollection = this.topicRepository.findByCollectionId(collectionId);

		return buildTopicsTree(allTopicsByCollection);
	}

	private List<TopicDTO> buildTopicsTree(List<Topic> topics) {
		// Implementation for building topics tree
		Map<Long, TopicDTO> topicMap = new HashMap<>();

		//create DTO for all nodes
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
}
