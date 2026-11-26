package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

	/** Returns the selected topic and all descendants (MariaDB recursive CTE). */
	@Query(value = """
			WITH RECURSIVE topic_tree AS (
				SELECT id, collection_id FROM topics WHERE id = :rootTopicId
				UNION ALL
				SELECT child.id, child.collection_id
				FROM topics child
				JOIN topic_tree parent ON child.parent_id = parent.id
				WHERE child.collection_id = parent.collection_id
			)
			SELECT id FROM topic_tree
			""", nativeQuery = true)
	List<Long> findDescendantIdsIncludingSelf(@Param("rootTopicId") Long rootTopicId);

	@Query("SELECT t.id FROM Topic t WHERE t.collection.id = :collectionId")
	List<Long> findIdsByCollectionId(@Param("collectionId") Long collectionId);

	@Query("SELECT t.id FROM Topic t WHERE t.collection.id IN :collectionIds")
	List<Long> findIdsByCollectionIdIn(@Param("collectionIds") List<Long> collectionIds);

	@Query("SELECT t FROM Topic t "
			+ "LEFT JOIN FETCH t.parent "
			+ "LEFT JOIN FETCH t.activeTemplate "
			+ "WHERE t.collection.id = :collectionId")
	List<Topic> findByCollectionId(@Param("collectionId") Long collectionId);

	long countBySchemaId(Long schemaId);

	@Query(value = """
			SELECT new vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO(
				t.id,
				t.name,
				t.translation,
				t.description,
				p.id,
				t.descriptionTranslation,
				at.id,
				at.name,
				at.code,
				CASE WHEN EXISTS (SELECT 1 FROM Topic c WHERE c.parent.id = t.id AND c.hiddenAt IS NULL) THEN TRUE ELSE FALSE END,
				t.createdAt,
				t.updatedAt
			)
			FROM Topic t
			LEFT JOIN t.parent p
			LEFT JOIN t.activeTemplate at
			WHERE t.collection.id = :collectionId AND t.parent IS NULL AND t.hiddenAt IS NULL
			""",
			countQuery = "SELECT COUNT(t) FROM Topic t WHERE t.collection.id = :collectionId AND t.parent IS NULL AND t.hiddenAt IS NULL")
	Page<TopicDTO> findByCollectionIdAndParentIsNull(@Param("collectionId") Long collectionId, Pageable pageable);

	@Query(value = """
			SELECT new vn.ptit.snapvocab.vocab.domain.dto.topic.TopicDTO(
				t.id,
				t.name,
				t.translation,
				t.description,
				p.id,
				t.descriptionTranslation,
				at.id,
				at.name,
				at.code,
				CASE WHEN EXISTS (SELECT 1 FROM Topic c WHERE c.parent.id = t.id AND c.hiddenAt IS NULL) THEN TRUE ELSE FALSE END,
				t.createdAt,
				t.updatedAt
			)
			FROM Topic t
			LEFT JOIN t.parent p
			LEFT JOIN t.activeTemplate at
			WHERE t.collection.id = :collectionId AND t.parent.id = :parentId AND t.hiddenAt IS NULL
			""",
			countQuery = "SELECT COUNT(t) FROM Topic t WHERE t.collection.id = :collectionId AND t.parent.id = :parentId AND t.hiddenAt IS NULL")
	Page<TopicDTO> findByCollectionIdAndParentId(@Param("collectionId") Long collectionId,
			@Param("parentId") Long parentId, Pageable pageable);

	/** Admin topic delete: the topic row lock serializes it with item adds (their foreign key check reads the row). */
	@Query(value = "SELECT id FROM topics WHERE id = :topicId FOR UPDATE", nativeQuery = true)
	List<Long> lockForDelete(@Param("topicId") Long topicId);

	long countByParentId(Long parentId);

	@Modifying
	@Query(value = "DELETE FROM topics WHERE id = :topicId", nativeQuery = true)
	int deleteRow(@Param("topicId") Long topicId);

	/** OQ-CNT-01: hide a topic and its subtopics with one timestamp, so restoring the topic brings back exactly them. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE topics SET hidden_at = :at, hidden_by = :by, hidden_reason = :reason
			WHERE id IN (:ids) AND hidden_at IS NULL
			""", nativeQuery = true)
	int hide(@Param("ids") java.util.Collection<Long> ids, @Param("at") java.time.Instant at, @Param("by") String by,
			@Param("reason") String reason);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE topics SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL
			WHERE id IN (:ids) AND hidden_at = :at
			""", nativeQuery = true)
	int restore(@Param("ids") java.util.Collection<Long> ids, @Param("at") java.time.Instant at);

	@Query("""
			SELECT t FROM Topic t LEFT JOIN FETCH t.parent
			WHERE t.collection.id = :collectionId AND t.hiddenAt IS NOT NULL
			ORDER BY t.hiddenAt DESC, t.id
			""")
	List<Topic> findHidden(@Param("collectionId") Long collectionId);
}
