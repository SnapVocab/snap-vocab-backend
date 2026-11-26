package vn.ptit.snapvocab.vocab.repository;


import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemValueRow;

@Repository
public interface TopicItemAttributeValueRepository extends JpaRepository<TopicItemAttributeValue, Long> {

	long countByAttributeId(Long attributeId);

	/**
	 * [lowercased word, topicId, topicName, collectionId, collectionName] for each of the learner's own topics that
	 * holds one of {@code words} as its TARGET_WORD (bound by the topic's active template).
	 */
	@Query("""
			SELECT DISTINCT LOWER(TRIM(v.value)), t.id, t.name, c.id, c.name
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			JOIN ti.topic t
			JOIN t.collection c
			JOIN TemplateField f ON f.schemaAttribute.id = v.attribute.id
			JOIN f.element e
			WHERE c.owner.id = :userId AND c.type = :userType
			  AND e.template.id = t.activeTemplate.id AND f.semanticRole = :role
			  AND LOWER(TRIM(v.value)) IN :words
			""")
	List<Object[]> findOwnTopicsContaining(@Param("userId") Long userId,
			@Param("userType") vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType userType,
			@Param("role") vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole role,
			@Param("words") Collection<String> words);

	@Query("""
			SELECT new vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemValueRow(
			    ti.id, v.attribute.id, v.value)
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			WHERE ti.topic.id IN :topicIds AND v.attribute.id IN :attributeIds
			ORDER BY ti.id, gi.position, v.id
			""")
	List<TopicItemValueRow> findItemValues(@Param("topicIds") Collection<Long> topicIds,
			@Param("attributeIds") Collection<Long> attributeIds);

	@Query("""
			SELECT new vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemValueRow(
			    ti.id, v.attribute.id, v.value)
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			WHERE ti.id = :itemId AND v.attribute.id IN :attributeIds
			ORDER BY gi.position, v.id
			""")
	List<TopicItemValueRow> findValuesOfItem(@Param("itemId") Long itemId,
			@Param("attributeIds") Collection<Long> attributeIds);

	@Query("""
			SELECT ti.id, sa.id, sa.name, v.value
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			JOIN v.attribute sa
			WHERE ti.topic.id IN :topicIds
			ORDER BY ti.id, gi.position, sa.position, v.id
			""")
	List<Object[]> findFlatValuesByTopicIds(@Param("topicIds") Collection<Long> topicIds);

	/**
	 * [itemId, semanticRole, value] of {@code itemIds} for the given roles, bound through each item's topic's active
	 * template; repeated groups come in their order (first definition / example first).
	 */
	@Query("""
			SELECT ti.id, f.semanticRole, v.value
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			JOIN ti.topic t
			JOIN TemplateField f ON f.schemaAttribute.id = v.attribute.id
			JOIN f.element e
			WHERE ti.id IN :itemIds AND e.template.id = t.activeTemplate.id AND f.semanticRole IN :roles
			ORDER BY ti.id, gi.position, v.id
			""")
	List<Object[]> findRoleValuesOfItems(@Param("itemIds") Collection<Long> itemIds,
			@Param("roles") Collection<vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole> roles);

	/** [itemId, attribute name, value] of {@code itemIds}; the fallback for topics whose template binds no roles. */
	@Query("""
			SELECT ti.id, sa.name, v.value
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			JOIN v.attribute sa
			WHERE ti.id IN :itemIds
			ORDER BY ti.id, gi.position, sa.position, v.id
			""")
	List<Object[]> findFlatValuesByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
