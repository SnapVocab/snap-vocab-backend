package vn.ptit.snapvocab.mission.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;

@Repository
public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {

	/** Candidate order is fixed by id; only the weighted draw is random (daily_mission.md §4.2). */
	List<MissionTemplate> findByActiveTrueOrderById();

	Optional<MissionTemplate> findByCode(String code);

	List<MissionTemplate> findAllByOrderById();

	/**
	 * Locks the whole catalog (daily_mission.md §8.3): every Admin writer takes this first, so the invariant is
	 * checked against the latest state. Must be the transaction's first read of templates, otherwise Hibernate
	 * hands back stale managed instances.
	 * ponytail: on an EMPTY table InnoDB only takes gap locks, so two concurrent first inserts may deadlock (one gets
	 * a 500, nothing is written); harmless because a create is always inactive. Use a dedicated lock row if it matters.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from MissionTemplate t order by t.id")
	List<MissionTemplate> lockCatalog();
}
