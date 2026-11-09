package vn.ptit.snapvocab.storage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.storage.config.MinioProperties;
import vn.ptit.snapvocab.storage.domain.enumeration.UploadPurpose;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;

class MinioStorageServiceTest {

	private final MinioClient client = mock(MinioClient.class);
	private final MinioProperties properties = new MinioProperties();
	private final UserRepository users = mock(UserRepository.class);
	private final MinioStorageService service = new MinioStorageService(client, properties, users);

	@Test
	void avatarUploadGeneratesPublicKeyAndSignsOnlyThePutRequest() throws Exception {
		properties.setBucket("snapvocab");
		when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://storage/upload");

		var upload = service.createUploadUrl("../../profile.PnG");

		assertThat(upload.getObjectName()).matches("public/avatars/[0-9a-f-]{36}\\.png");
		assertThat(upload.getContentType()).isEqualTo("image/png");
		assertThat(upload.getUploadUrl()).isEqualTo("https://storage/upload");
		var args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
		verify(client).getPresignedObjectUrl(args.capture());
		assertThat(args.getValue().method()).isEqualTo(Method.PUT);
		assertThat(args.getValue().object()).isEqualTo(upload.getObjectName());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "file.txt", "file.mp4", "file"})
	void invalidAvatarNamesAreRejectedBeforeAnUploadUrlIsIssued(String name) {
		assertThatThrownBy(() -> service.createUploadUrl(name)).isInstanceOf(BadRequestAlertException.class);
		verifyNoInteractions(client);
	}

	@Test
	void confirmingAvatarStoresPublicKeyAndReturnsStableUrl() throws Exception {
		properties.setEndpoint("https://storage.example.com");
		properties.setBucket("snapvocab");
		String key = "public/avatars/avatar.png";
		var stat = mock(StatObjectResponse.class);
		when(stat.size()).thenReturn(1024L);
		when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
		var user = new User();
		when(users.findOneByEmailIgnoreCase("avatar@example.com")).thenReturn(Optional.of(user));

		try (var security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of("avatar@example.com"));
			assertThat(service.confirmUpload(key, UploadPurpose.AVATAR))
					.isEqualTo("https://storage.example.com/snapvocab/" + key);
		}
		assertThat(user.getAvatarUrl()).isEqualTo(key);
		verify(users).save(user);
		verify(client, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
	}

	@Test
	void oversizedAvatarIsNotSaved() throws Exception {
		properties.setEndpoint("https://storage.example.com");
		properties.setBucket("snapvocab");
		var stat = mock(StatObjectResponse.class);
		when(stat.size()).thenReturn(5L * 1024 * 1024 + 1);
		when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);

		assertThatThrownBy(() -> service.confirmUpload("public/avatars/large.png", UploadPurpose.AVATAR))
				.isInstanceOf(BadRequestAlertException.class).hasMessageContaining("5MB");
		verifyNoInteractions(users);
	}

	@Test
	void shopAssetsCannotBeConfirmedAsAvatars() throws Exception {
		properties.setEndpoint("https://storage.example.com");
		properties.setBucket("snapvocab");
		when(client.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));

		assertThatThrownBy(() -> service.confirmUpload("public/shop/frame.png", UploadPurpose.AVATAR))
				.isInstanceOf(BadRequestAlertException.class).hasMessageContaining("public/avatars/");
		verifyNoInteractions(users);
	}

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
