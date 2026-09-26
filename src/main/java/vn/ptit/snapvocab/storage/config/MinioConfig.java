package vn.ptit.snapvocab.storage.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(MinioProperties.class)
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

	private final MinioProperties minioProperties;

	@Bean
	public MinioClient minioClient() {
		return MinioClient.builder()
				.endpoint(minioProperties.getEndpoint())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.build();
	}
}

