package vn.ptit.snapvocab.quiz.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.domain.enumeration.SemanticRole;
import vn.ptit.snapvocab.vocab.repository.TopicItemAttributeValueRepository;

/**
 * Wrong answers for listening questions are drawn from the system topics' words: common vocabulary, and never
 * another learner's own words. A few thousand short strings, reloaded every few hours so new system topics count.
 */
@Component
public class ListeningWordPool {

	static final Duration REFRESH_AFTER = Duration.ofHours(6);
	private static final int MAX_LENGTH = 40;

	private final TopicItemAttributeValueRepository valueRepository;
	private final Clock clock;
	private volatile List<String> words;
	private volatile Instant loadedAt;

	@Autowired
	public ListeningWordPool(TopicItemAttributeValueRepository valueRepository) {
		this(valueRepository, Clock.systemUTC());
	}

	ListeningWordPool(TopicItemAttributeValueRepository valueRepository, Clock clock) {
		this.valueRepository = valueRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<String> words() {
		Instant now = clock.instant();
		List<String> current = words;
		if (current == null || loadedAt.plus(REFRESH_AFTER).isBefore(now)) {
			synchronized (this) {
				if (words == null || loadedAt.plus(REFRESH_AFTER).isBefore(now)) {
					words = valueRepository
							.findValuesByCollectionTypeAndRole(CollectionType.SYSTEM, SemanticRole.TARGET_WORD)
							.stream()
							.filter(w -> w != null && !w.isBlank() && w.length() <= MAX_LENGTH)
							.toList();
					loadedAt = now;
				}
				current = words;
			}
		}
		return current;
	}
}
