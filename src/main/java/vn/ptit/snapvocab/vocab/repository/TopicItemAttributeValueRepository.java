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
}

