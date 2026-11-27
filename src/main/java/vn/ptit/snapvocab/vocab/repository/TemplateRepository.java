package vn.ptit.snapvocab.vocab.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

	List<Template> findBySchemaId(Long schemaId);

	/** Templates the learner built in Template Builder ({@code TPL_} codes) on schemas they own, newest first. */
	@Query("""
			SELECT t FROM Template t
			WHERE t.schema.user.id = :userId AND t.schema.isSystem = false AND t.code LIKE 'TPL!_%' ESCAPE '!'
			ORDER BY t.updatedAt DESC, t.id DESC
			""")
	List<Template> findLibraryTemplates(@Param("userId") Long userId);

	Optional<Template> findBySchemaIdAndIsDefaultTrue(Long schemaId);

	Optional<Template> findBySchemaIdAndCode(Long schemaId, String code);

	/** Rewriting only a template's elements does not make the template itself dirty, so its time is moved by hand. */
	@Modifying
	@Query("UPDATE Template t SET t.updatedAt = :now WHERE t.id = :id")
	void touch(@Param("id") Long id, @Param("now") Instant now);
}

