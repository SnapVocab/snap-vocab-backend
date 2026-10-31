package vn.ptit.snapvocab.progress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ptit.snapvocab.mission.service.MissionTracker;
import vn.ptit.snapvocab.progress.domain.LearningEvent;
import vn.ptit.snapvocab.progress.domain.enumeration.LearningEventType;
import vn.ptit.snapvocab.progress.repository.LearningEventRepository;
import vn.ptit.snapvocab.quiz.repository.QuizRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.repository.FsrsRecordRepository;
import vn.ptit.snapvocab.vocab.repository.TopicItemRepository;

@ExtendWith(MockitoExtension.class)
class ProgressRecordingTest {
	@Mock private UserRepository userRepository;
	@Mock private LearningEventRepository eventRepository;
	@Mock private FsrsRecordRepository fsrsRepository;
	@Mock private TopicItemRepository itemRepository;
	@Mock private QuizRepository quizRepository;
	@Mock private MissionTracker missionTracker;
	@InjectMocks private ProgressService service;
	private User user;
	private final Set<String> recorded = new HashSet<>();

	@BeforeEach
	void setUp() {
		user = new User();
		user.setId(7L);
		when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
		when(eventRepository.existsBySourceKey(any())).thenAnswer(call -> recorded.contains(call.getArgument(0)));
		when(eventRepository.save(any())).thenAnswer(call -> {
			LearningEvent event = call.getArgument(0);
			recorded.add(event.getSourceKey());
			ReflectionTestUtils.setField(event, "id", (long) recorded.size());
			return event;
		});
	}

	@Test
	void quizAndSrsOnTheSameDayAdvanceStreakOnlyOnceAndReplaysAddNothing() {
		user.setStreakDays(4);
		user.setLongestStreakDays(12);
		user.setLastStudiedAt(Instant.parse("2026-10-30T12:00:00Z"));
		service.record(7L, LearningEventType.QUIZ_COMPLETED, "QUIZ_COMPLETED:9", Map.of(),
				Instant.parse("2026-10-31T12:00:00Z"));
		service.record(7L, LearningEventType.SRS_REVIEW_COMPLETED, "SRS_REVIEW_COMPLETED:7:21:1", Map.of(),
				Instant.parse("2026-10-31T13:00:00Z"));
		service.record(7L, LearningEventType.SRS_REVIEW_COMPLETED, "SRS_REVIEW_COMPLETED:7:21:1", Map.of(),
				Instant.parse("2026-10-31T13:00:00Z"));
		assertThat(user.getStreakDays()).isEqualTo(5);
		assertThat(user.getLongestStreakDays()).isEqualTo(12);
		verify(eventRepository, times(2)).save(any());
	}

	@Test
	void srsAfterAMissedDayRestartsAtOneAndPreservesTheRecord() {
		user.setStreakDays(8);
		user.setLongestStreakDays(12);
		user.setLastStudiedAt(Instant.parse("2026-10-28T12:00:00Z"));
		service.record(7L, LearningEventType.SRS_REVIEW_COMPLETED, "SRS_REVIEW_COMPLETED:7:21:1", Map.of(),
				Instant.parse("2026-10-31T17:30:00Z"));
		assertThat(user.getStreakDays()).isEqualTo(1);
		assertThat(user.getLongestStreakDays()).isEqualTo(12);
		assertThat(user.getLastStudiedAt()).isEqualTo(Instant.parse("2026-10-31T17:30:00Z"));
	}
}
