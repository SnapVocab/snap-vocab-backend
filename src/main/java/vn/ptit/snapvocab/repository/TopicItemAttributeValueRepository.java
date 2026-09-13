package vn.ptit.snapvocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TopicItemAttributeValue;

@Repository
public interface TopicItemAttributeValueRepository extends JpaRepository<TopicItemAttributeValue, Long> {

	long countByAttributeId(Long attributeId);
}
