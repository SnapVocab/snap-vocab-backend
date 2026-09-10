package vn.ptit.snapvocab.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TopicAttributeGroup;

@Repository
public interface TopicAttributeGroupRepository extends JpaRepository<TopicAttributeGroup, Long> {

	@Query("""
			SELECT DISTINCT tag
			FROM TopicAttributeGroup tag
			LEFT JOIN FETCH tag.attributes ta
			WHERE tag.topic.id = :topicId
			ORDER BY tag.position ASC, ta.position ASC
			""")
	List<TopicAttributeGroup> findByTopicIdWithAttributes(@Param("topicId") Long topicId);
}