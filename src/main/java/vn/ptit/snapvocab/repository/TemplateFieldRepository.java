package vn.ptit.snapvocab.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TemplateField;

@Repository
public interface TemplateFieldRepository extends JpaRepository<TemplateField, Long> {

	@Query("""
			SELECT tf
			FROM TemplateField tf
			WHERE tf.element.template.schema.id = :schemaId
			""")
	List<TemplateField> findBySchemaId(@Param("schemaId") Long schemaId);

	@Query("""
			SELECT tf
			FROM TemplateField tf
			WHERE tf.element.template.id = :templateId
			""")
	List<TemplateField> findByTemplateId(@Param("templateId") Long templateId);

	long countBySchemaAttributeId(Long schemaAttributeId);
}
