package vn.ptit.snapvocab.vocab.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {

	List<Collection> findByType(CollectionType type);

	List<Collection> findByOwnerId(Long ownerId);

	@Query("SELECT c FROM Collection c WHERE c.type = :type OR c.owner.id = :ownerId")
	List<Collection> findByTypeOrOwnerId(@Param("type") CollectionType type, @Param("ownerId") Long ownerId);

	boolean existsByNameAndType(String name, CollectionType type);

	boolean existsByNameAndOwnerId(String name, Long ownerId);
}

