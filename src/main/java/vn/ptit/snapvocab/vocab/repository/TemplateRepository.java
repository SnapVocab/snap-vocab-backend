package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

	List<Template> findBySchemaId(Long schemaId);

	Optional<Template> findBySchemaIdAndIsDefaultTrue(Long schemaId);

	Optional<Template> findBySchemaIdAndCode(Long schemaId, String code);
}

