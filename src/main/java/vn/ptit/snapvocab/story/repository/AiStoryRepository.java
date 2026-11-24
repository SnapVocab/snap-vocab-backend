package vn.ptit.snapvocab.story.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.ptit.snapvocab.story.domain.AiStory;

public interface AiStoryRepository extends JpaRepository<AiStory, Long> {

	Page<AiStory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Optional<AiStory> findByIdAndUserId(Long id, Long userId);

	long countByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end);
}

