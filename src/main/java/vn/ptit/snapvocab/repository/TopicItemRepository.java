package vn.ptit.snapvocab.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.TopicItem;
import vn.ptit.snapvocab.domain.dto.topic.TopicItemAttributeRow;

@Repository
public interface TopicItemRepository extends JpaRepository<TopicItem, Long> {

	/**
	 * Finds paginated TopicItem IDs for a specific topic.
	 * <p>
	 * Used as Step 1 in the 2-step EAV pagination query to avoid Cartesian product and N+1 issues.
	 *
	 * @param topicId  The topic ID to query items for.
	 * @param pageable The pagination information.
	 * @return A page of TopicItem IDs.
	 */
	@Query("""
			SELECT ti.id
			FROM TopicItem ti
			WHERE ti.topic.id = :topicId
			ORDER BY ti.id
			""")
	Page<Long> findIdsByTopicId(
			@Param("topicId") Long topicId,
			Pageable pageable
	);

	/**
	 * Batch fetches flat EAV attribute rows for the given list of TopicItem IDs.
	 * <p>
	 * Joins topic items with their group instances, attribute definitions, and attribute values,
	 * returning ordered rows ready for aggregation into DTO structures.
	 *
	 * @param topicItemIds The batch of TopicItem IDs on the current page.
	 * @return Flat list of attribute rows sorted by group, instance, and attribute position.
	 */
	@Query("""
			SELECT new vn.ptit.snapvocab.domain.dto.topic.TopicItemAttributeRow(
			    ti.id,
			    tag.id,
			    tag.name,
			    tag.label,
			    tag.multiple,
			    tiag.id,
			    tiag.position,
			    ta.id,
			    ta.name,
			    ta.label,
			    ta.dataType,
			    ta.required,
			    ta.position,
			    tiav.value
			)
			FROM TopicItem ti
			JOIN ti.groupInstances tiag
			JOIN tiag.groupDefinition tag
			JOIN tag.attributes ta
			JOIN tiag.values tiav
			    ON tiav.attribute = ta
			WHERE ti.id IN :topicItemIds
			ORDER BY
			    tag.position,
			    tiag.position,
			    ta.position
			""")
	List<TopicItemAttributeRow> findAttributeRows(
			@Param("topicItemIds") List<Long> topicItemIds
	);
}
