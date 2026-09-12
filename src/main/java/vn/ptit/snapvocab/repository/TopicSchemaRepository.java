package vn.ptit.snapvocab.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TopicSchema;

@Repository
public interface TopicSchemaRepository extends JpaRepository<TopicSchema, Long> {

	Optional<TopicSchema> findByTopicId(Long topicId);
}
