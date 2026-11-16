package vn.ptit.snapvocab.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.scan.domain.ScanDetection;
import vn.ptit.snapvocab.scan.domain.ScanRequest;
import vn.ptit.snapvocab.scan.domain.dto.scan.DetectionDTO;
import vn.ptit.snapvocab.scan.repository.ScanDetectionRepository;
import vn.ptit.snapvocab.user.domain.User;

/** admin_overview_backend.md G2-01: one row per label, has_word by label then headword, scan's created_at. */
@ExtendWith(MockitoExtension.class)
class ScanDetectionRecorderTest {

	private static final Instant CREATED = Instant.parse("2026-10-20T03:00:00Z");

	@Mock
	private WordRepository wordRepository;
	@Mock
	private ScanDetectionRepository scanDetectionRepository;

	@Test
	@SuppressWarnings("unchecked")
	void writesOneRowPerLabelWithHasWordByLabelThenHeadword() {
		ScanDetectionRecorder recorder = new ScanDetectionRecorder(wordRepository, scanDetectionRepository);
		List<DetectionDTO> labels = List.of(label("Cup", "cup"), label("coffee mug", "mug"),
				label("thermos", "thermos"));
		when(wordRepository.findExistingEnglish(any())).thenReturn(List.of("cup", "mug"));
		User user = new User();
		user.setId(7L);
		ScanRequest scan = ScanRequest.builder().id("scan-1").user(user).build();
		ReflectionTestUtils.setField(scan, "createdAt", CREATED);

		Set<String> known = recorder.knownWords(labels);
		recorder.save(scan, labels, known);

		ArgumentCaptor<List<ScanDetection>> rows = ArgumentCaptor.forClass(List.class);
		verify(scanDetectionRepository).saveAll(rows.capture());
		assertThat(rows.getValue()).extracting(ScanDetection::getLabel).containsExactly("cup", "coffee mug", "thermos");
		assertThat(rows.getValue()).extracting(ScanDetection::isHasWord).containsExactly(true, true, false);
		assertThat(rows.getValue()).allSatisfy(r -> {
			assertThat(r.getScanRequestId()).isEqualTo("scan-1");
			assertThat(r.getUserId()).isEqualTo(7L);
			assertThat(r.getCreatedAt()).isEqualTo(CREATED);
		});
	}

	@Test
	void noLabelsSkipsTheDictionary() {
		assertThat(new ScanDetectionRecorder(wordRepository, scanDetectionRepository).knownWords(List.of())).isEmpty();
		verifyNoInteractions(wordRepository);
	}

	private static DetectionDTO label(String label, String headword) {
		return new DetectionDTO(label, headword, 0.9, "od", "HIGH", null);
	}
}
