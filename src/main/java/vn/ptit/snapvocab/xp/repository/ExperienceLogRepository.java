package vn.ptit.snapvocab.xp.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.xp.domain.ExperienceLog;

@Repository
public interface ExperienceLogRepository extends JpaRepository<ExperienceLog, String> {

	Optional<ExperienceLog> findByEventKey(String eventKey);
}
