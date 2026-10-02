package vn.ptit.snapvocab.vocab.repository;

import vn.ptit.snapvocab.vocab.domain.Topic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.SchemaAttributeGroup;

@Repository
public interface SchemaAttributeGroupRepository extends JpaRepository<SchemaAttributeGroup, Long> {

	@Query("""
			SELECT DISTINCT sag
			FROM Topic t
			JOIN t.schema s
			JOIN s.attributeGroups sag
			LEFT JOIN FETCH sag.attributes sa
			WHERE t.id = :topicId
			ORDER BY sag.position ASC, sa.position ASC
			""")
	List<SchemaAttributeGroup> findByTopicIdWithAttributes(@Param("topicId") Long topicId);


	@Query("""
			SELECT DISTINCT sag
			FROM SchemaAttributeGroup sag
			LEFT JOIN FETCH sag.attributes sa
			WHERE sag.schema.id = :schemaId
			ORDER BY sag.position ASC, sa.position ASC
			""")
	List<SchemaAttributeGroup> findBySchemaIdWithAttributes(@Param("schemaId") Long schemaId);
}

