package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemAttributeRow;

@Repository
public interface TopicItemRepository extends JpaRepository<TopicItem, Long> {

	Optional<TopicItem> findByIdAndTopicId(Long id, Long topicId);

	@Query("""
			SELECT ti
			FROM TopicItem ti
			WHERE ti.id IN :ids
			""")
	List<TopicItem> findItemsByIds(@Param("ids") List<Long> ids);

	/**
	 * Finds paginated TopicItem IDs for a specific topic.
	 * <p>
	 * Used as Step 1 in the 2-step EAV pagination query to avoid Cartesian product and N+1 issues.
	 *
	 * @param topicId  The topic ID to query items for.
	 * @param pageable The pagination information.
	 * @return A page of TopicItem IDs.
	 */
	@Query("""
			SELECT ti.id
			FROM TopicItem ti
			WHERE ti.topic.id = :topicId
			ORDER BY ti.id
			""")
	Page<Long> findIdsByTopicId(
			@Param("topicId") Long topicId,
			Pageable pageable
	);

	interface CollectionWordCount {
		Long getCollectionId();

		Long getTotal();
	}

	@Query("""
			SELECT ti.topic.collection.id AS collectionId, COUNT(ti) AS total FROM TopicItem ti
			WHERE ti.topic.collection.id IN :collectionIds
			GROUP BY ti.topic.collection.id
			""")
	List<CollectionWordCount> countByCollectionIds(@Param("collectionIds") List<Long> collectionIds);

	long countByTopicIdIn(List<Long> topicIds);

	@Query("SELECT COUNT(ti) FROM TopicItem ti WHERE ti.topic.collection.owner.id = :userId")
	long countOwnedBy(@Param("userId") Long userId);

	default boolean existsOwnedBy(Long userId) {
		return countOwnedBy(userId) > 0;
	}

	/**
	 * Words in the learner's study library, the same set as StudyService.libraryCollections: own collections plus
	 * system collections already studied or configured. Every fsrs_record falls inside it, so learned <= total.
	 */
	@Query("""
			SELECT COUNT(ti) FROM TopicItem ti JOIN ti.topic t JOIN t.collection c
			WHERE c.owner.id = :userId
			   OR (c.type = vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType.SYSTEM
			       AND (EXISTS (SELECT 1 FROM FsrsRecord fr WHERE fr.user.id = :userId AND fr.topicItem.topic.collection = c)
			            OR EXISTS (SELECT 1 FROM CollectionStudySettings s WHERE s.user.id = :userId AND s.collection = c)))
			""")
	long countLibraryWords(@Param("userId") Long userId);

	@Query("""
			SELECT DISTINCT ti
			FROM TopicItem ti
			LEFT JOIN FETCH ti.groupInstances gi
			LEFT JOIN FETCH gi.values v
			WHERE ti.topic.id = :topicId
			""")
	List<TopicItem> findByTopicIdWithEav(@Param("topicId") Long topicId);

	/**
	 * Batch fetches flat EAV attribute rows for the given list of TopicItem IDs.
	 * <p>
	 * Joins topic items with their group instances, attribute definitions, and attribute values,
	 * returning ordered rows ready for aggregation into DTO structures.
	 *
	 * @param topicItemIds The batch of TopicItem IDs on the current page.
	 * @return Flat list of attribute rows sorted by group, instance, and attribute position.
	 */
	@Query("""
			SELECT new vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemAttributeRow(
			    ti.id,
			    ti.createdAt,
			    sag.id,
			    sag.name,
			    sag.label,
			    sag.multiple,
			    tiag.id,
			    tiag.position,
			    sa.id,
			    sa.name,
			    sa.label,
			    sa.dataType,
			    sa.required,
			    sa.position,
			    tiav.value
			)
			FROM TopicItem ti
			JOIN ti.groupInstances tiag
			JOIN tiag.groupDefinition sag
			JOIN tiag.values tiav
			JOIN tiav.attribute sa
			WHERE ti.id IN :topicItemIds
			ORDER BY
			    sag.position,
			    tiag.position,
			    sa.position
			""")
	List<TopicItemAttributeRow> findAttributeRows(
			@Param("topicItemIds") List<Long> topicItemIds
	);


	/**
	 * Fetches the top IDs of TopicItems for a given topic, ordered by ID in descending order. Use
	 * this when the cursor is null (for the first fetch).
	 *
	 * @param topicId The ID of the topic.
	 * @param limit   The maximum number of IDs to return.
	 * @return A list of TopicItem IDs.
	 */
	@Query("""
			SELECT ti.id
			FROM TopicItem ti
			WHERE ti.topic.id = :topicId
			ORDER BY ti.id DESC
			LIMIT :limit
			""")
	List<Long> findTopIdsByTopicId(
			@Param("topicId") Long topicId,
			@Param("limit") Long limit
	);

	/**
	 * Fetches list of TopicItem ids based on cursor pagination.
	 *
	 * @param topicId The ID of the topic.
	 * @param cursor  The cursor to get next items
	 * @param limit   The maximum number of IDs to return.
	 * @return A list of TopicItem IDs.
	 */
	@Query("""
			SELECT ti.id
			FROM TopicItem ti
			WHERE ti.topic.id = :topicId AND ti.id < :cursor
			ORDER BY ti.id DESC
			LIMIT :limit
			""")
	List<Long> findIdsByTopicIdAndCursor(
			@Param("topicId") Long topicId,
			@Param("cursor") Long cursor,
			@Param("limit") Long limit
	);


}

