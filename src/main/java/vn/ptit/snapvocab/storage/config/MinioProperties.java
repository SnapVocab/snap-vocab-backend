package vn.ptit.snapvocab.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String bucket;

	/** Public bucket root, e.g. https://cdn.example.com/snapvocab; defaults to endpoint/bucket. */
	private String publicBaseUrl;
}

