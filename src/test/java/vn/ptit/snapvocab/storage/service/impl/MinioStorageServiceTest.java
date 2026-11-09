package vn.ptit.snapvocab.storage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.storage.config.MinioProperties;
import vn.ptit.snapvocab.user.repository.UserRepository;

class MinioStorageServiceTest {

	private final MinioClient client = mock(MinioClient.class);
	private final MinioProperties properties = new MinioProperties();
	private final MinioStorageService service = new MinioStorageService(client, properties, mock(UserRepository.class));

	@Test
	void publicAssetDefaultsToBucketUrlAndEncodesTheKey() {
		properties.setEndpoint("https://storage.example.com/");
		properties.setBucket("snapvocab");

		assertThat(service.createDownloadUrl("public/shop/frame 1.png"))
				.isEqualTo("https://storage.example.com/snapvocab/public/shop/frame%201.png");
		verifyNoInteractions(client);
	}

	@Test
	void publicAssetUsesConfiguredCdnRootWithoutAddingBucketAgain() {
		properties.setPublicBaseUrl("https://cdn.example.com/assets/");
		properties.setBucket("snapvocab");

		assertThat(service.createDownloadUrl("public/shop/frame.png"))
				.isEqualTo("https://cdn.example.com/assets/public/shop/frame.png");
		verifyNoInteractions(client);
	}

	@Test
	void privateAssetStillUsesPresignedUrlEvenWithCdnConfigured() throws Exception {
		properties.setPublicBaseUrl("https://cdn.example.com");
		properties.setBucket("snapvocab");
		when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://storage/signed");

		assertThat(service.createDownloadUrl("avatars/7.png")).isEqualTo("https://storage/signed");
	}
}
