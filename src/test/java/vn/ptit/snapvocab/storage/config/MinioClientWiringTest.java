package vn.ptit.snapvocab.storage.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import vn.ptit.snapvocab.storage.service.impl.MinioStorageService;
import vn.ptit.snapvocab.user.repository.UserRepository;

@SpringJUnitConfig({MinioConfig.class, MinioStorageService.class})
@TestPropertySource(properties = {
		"minio.endpoint=https://storage.example.com", "minio.access-key=test-access",
		"minio.secret-key=test-secret", "minio.bucket=snapvocab"
})
class MinioClientWiringTest {

	@MockitoBean MinioClient minioClient;
	@MockitoBean UserRepository userRepository;
	@Autowired MinioStorageService service;
	@Autowired @Qualifier("presigningMinioClient") MinioClient signer;

	@Test
	void existingSingleClientMocksStillCoverTheSignerWithoutAnInternalEndpoint() throws Exception {
		when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://public/upload");
		assertThat(signer).isSameAs(minioClient);
		assertThat(service.createUploadUrl("avatar.png").getUploadUrl()).isEqualTo("https://public/upload");
	}
}
