package vn.ptit.snapvocab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarUrlServiceTest {

	@Mock
	private StorageService storageService;

	@InjectMocks
	private AvatarUrlService service;

	@Test
	void signsTheStoredObjectKey() {
		when(storageService.createDownloadUrl("avatars/7.png")).thenReturn("https://storage/signed");

		assertThat(service.resolve("avatars/7.png")).isEqualTo("https://storage/signed");
	}

	@Test
	void passesLegacyUrlsThrough() {
		assertThat(service.resolve("https://storage/old?X-Amz=1")).isEqualTo("https://storage/old?X-Amz=1");
		verifyNoInteractions(storageService);
	}

	@Test
	void missingAvatarStaysNull() {
		assertThat(service.resolve(null)).isNull();
		assertThat(service.resolve("  ")).isNull();
		verifyNoInteractions(storageService);
	}

	@Test
	void storageFailureDoesNotBreakTheProfile() {
		when(storageService.createDownloadUrl("gone.png")).thenThrow(new RuntimeException("no bucket"));

		assertThat(service.resolve("gone.png")).isNull();
	}
}
