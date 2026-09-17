package vn.ptit.snapvocab.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.Topic;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

	@Query("SELECT t FROM Topic t "
			+ "LEFT JOIN FETCH t.parent "
			+ "LEFT JOIN FETCH t.activeTemplate "
			+ "WHERE t.collection.id = :collectionId")
	List<Topic> findByCollectionId(@Param("collectionId") Long collectionId);

	long countBySchemaId(Long schemaId);
}


