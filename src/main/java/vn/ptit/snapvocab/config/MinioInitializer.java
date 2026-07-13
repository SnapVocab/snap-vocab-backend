package vn.ptit.snapvocab.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioInitializer {

	private final MinioClient minioClient;
	private final MinioProperties properties;

	@PostConstruct
	public void init() throws Exception {

		boolean found = minioClient.bucketExists(
				BucketExistsArgs.builder()
						.bucket(properties.getBucket())
						.build());

		if (!found) {
			minioClient.makeBucket(
					MakeBucketArgs.builder()
							.bucket(properties.getBucket())
							.build());
		}
	}
}
