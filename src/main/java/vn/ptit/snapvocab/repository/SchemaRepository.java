package vn.ptit.snapvocab.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Schema;

@Repository
public interface SchemaRepository extends JpaRepository<Schema, Long> {

	Optional<Schema> findByNameIgnoreCase(String name);

	Optional<Schema> findByCode(String code);

	boolean existsByCode(String code);

	java.util.List<Schema> findByIsSystemTrue();
}
