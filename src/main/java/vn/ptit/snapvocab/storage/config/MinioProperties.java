package vn.ptit.snapvocab.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

	private String endpoint;
	/** Optional backend-only S3 endpoint; public URLs continue to use endpoint. */
	private String internalEndpoint;
	private String region = "us-east-1";
	private String accessKey;
	private String secretKey;
	private String bucket;

	/** Public bucket root, e.g. https://cdn.example.com/snapvocab; defaults to endpoint/bucket. */
	private String publicBaseUrl;
}

