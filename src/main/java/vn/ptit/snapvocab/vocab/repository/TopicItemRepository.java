package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	// WI-04 / WI-QZ-01: the item row is the one lock every writer of data about the item goes through. Inserting an
	// FSRS card or a daily card takes a shared lock on it (InnoDB foreign key check), and quiz creation takes one
	// explicitly (quiz_questions has no foreign key). Holding it exclusively keeps all of them out until the delete
	// ends; the learner reads below run after the lock in READ_COMMITTED, so they see everything committed before.

	@Query(value = "SELECT id FROM topic_items WHERE id = :itemId FOR UPDATE", nativeQuery = true)
	List<Long> lockForDelete(@Param("itemId") Long itemId);

	@Query(value = "SELECT id FROM topic_items WHERE topic_id = :topicId FOR UPDATE", nativeQuery = true)
	List<Long> lockTopicItemsForDelete(@Param("topicId") Long topicId);

	/**
	 * LOCK IN SHARE MODE, not FOR SHARE: MariaDB 10.11 (production) only knows the former. FORCE INDEX (PRIMARY): a
	 * shared read served from a covering secondary index locks only that index, which the delete's lock on the row
	 * itself does not conflict with (seen on MySQL 9.1, 2026-11-20).
	 */
	@Query(value = "SELECT id FROM topic_items FORCE INDEX (PRIMARY) WHERE id IN (:ids) LOCK IN SHARE MODE", nativeQuery = true)
	List<Long> lockForQuiz(@Param("ids") java.util.Collection<Long> ids);

	/** Learners with study data on one item: SRS card, daily card, quiz question. */
	@Query(value = """
			SELECT user_id FROM fsrs_records WHERE topic_item_id = :itemId
			UNION SELECT user_id FROM study_daily_cards WHERE topic_item_id = :itemId
			UNION SELECT q.user_id FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id WHERE qq.topic_item_id = :itemId
			""", nativeQuery = true)
	List<Long> findItemLearners(@Param("itemId") Long itemId);

	/** Learners with study data on a topic: the item rows above, plus quizzes and AI stories made on the topic. */
	@Query(value = """
			SELECT f.user_id FROM fsrs_records f JOIN topic_items ti ON ti.id = f.topic_item_id WHERE ti.topic_id = :topicId
			UNION SELECT d.user_id FROM study_daily_cards d JOIN topic_items ti ON ti.id = d.topic_item_id
				WHERE ti.topic_id = :topicId
			UNION SELECT q.user_id FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id
				JOIN topic_items ti ON ti.id = qq.topic_item_id WHERE ti.topic_id = :topicId
			UNION SELECT q.user_id FROM quizzes q WHERE q.topic_id = :topicId
			UNION SELECT s.user_id FROM ai_stories s WHERE s.topic_id = :topicId
			""", nativeQuery = true)
	List<Long> findTopicLearners(@Param("topicId") Long topicId);

	/** Bulk delete of a topic's EAV rows and items, children first (no cascade in the schema). */
	@Modifying
	@Query(value = """
			DELETE v FROM topic_item_attribute_values v
			JOIN topic_item_attribute_groups g ON g.id = v.group_instance_id
			JOIN topic_items ti ON ti.id = g.topic_item_id
			WHERE ti.topic_id = :topicId
			""", nativeQuery = true)
	int deleteValuesOfTopic(@Param("topicId") Long topicId);

	@Modifying
	@Query(value = """
			DELETE g FROM topic_item_attribute_groups g
			JOIN topic_items ti ON ti.id = g.topic_item_id
			WHERE ti.topic_id = :topicId
			""", nativeQuery = true)
	int deleteGroupsOfTopic(@Param("topicId") Long topicId);

	@Modifying
	@Query(value = "DELETE FROM topic_items WHERE topic_id = :topicId", nativeQuery = true)
	int deleteItemsOfTopic(@Param("topicId") Long topicId);
}
