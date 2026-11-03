package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
				CASE WHEN EXISTS (SELECT 1 FROM Topic c WHERE c.parent.id = t.id) THEN TRUE ELSE FALSE END,
				t.createdAt,
				t.updatedAt
			)
			FROM Topic t
			LEFT JOIN t.parent p
			LEFT JOIN t.activeTemplate at
			WHERE t.collection.id = :collectionId AND t.parent IS NULL
			""",
			countQuery = "SELECT COUNT(t) FROM Topic t WHERE t.collection.id = :collectionId AND t.parent IS NULL")
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
				CASE WHEN EXISTS (SELECT 1 FROM Topic c WHERE c.parent.id = t.id) THEN TRUE ELSE FALSE END,
				t.createdAt,
				t.updatedAt
			)
			FROM Topic t
			LEFT JOIN t.parent p
			LEFT JOIN t.activeTemplate at
			WHERE t.collection.id = :collectionId AND t.parent.id = :parentId
			""",
			countQuery = "SELECT COUNT(t) FROM Topic t WHERE t.collection.id = :collectionId AND t.parent.id = :parentId")
	Page<TopicDTO> findByCollectionIdAndParentId(@Param("collectionId") Long collectionId,
			@Param("parentId") Long parentId, Pageable pageable);
}

