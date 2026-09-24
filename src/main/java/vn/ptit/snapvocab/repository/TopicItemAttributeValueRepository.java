package vn.ptit.snapvocab.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TopicItemAttributeValue;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemValueRow;

@Repository
public interface TopicItemAttributeValueRepository extends JpaRepository<TopicItemAttributeValue, Long> {

	long countByAttributeId(Long attributeId);

	@Query("""
			SELECT new vn.ptit.snapvocab.domain.dto.topic.TopicItemValueRow(
			    ti.id, v.attribute.id, v.value)
			FROM TopicItemAttributeValue v
			JOIN v.groupInstance gi
			JOIN gi.item ti
			WHERE ti.topic.id = :topicId AND v.attribute.id IN :attributeIds
			ORDER BY ti.id, gi.position, v.id
			""")
	List<TopicItemValueRow> findItemValues(@Param("topicId") Long topicId,
			@Param("attributeIds") Collection<Long> attributeIds);
}
