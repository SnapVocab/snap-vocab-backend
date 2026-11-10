package vn.ptit.snapvocab.storage.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@EnableConfigurationProperties(MinioProperties.class)
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

	private final MinioProperties minioProperties;

	@Bean
	@Primary
	public MinioClient minioClient() {
		return client(StringUtils.hasText(minioProperties.getInternalEndpoint())
				? minioProperties.getInternalEndpoint() : minioProperties.getEndpoint());
	}

	@Bean
	public MinioClient presigningMinioClient(@Qualifier("minioClient") MinioClient internalClient) {
		return StringUtils.hasText(minioProperties.getInternalEndpoint())
				? client(minioProperties.getEndpoint()) : internalClient;
	}

	private MinioClient client(String endpoint) {
		return MinioClient.builder()
				.endpoint(endpoint)
				.region(minioProperties.getRegion())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.build();
	}
}

