package vn.ptit.snapvocab.story.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ptit.snapvocab.story.domain.AiProviderConfig;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {

	List<AiProviderConfig> findByEnabledTrueOrderByPriorityAscIdAsc();

	List<AiProviderConfig> findAllByOrderByPriorityAscIdAsc();
}

