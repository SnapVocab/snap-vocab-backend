package vn.ptit.snapvocab.scan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-service")
@Data
public class AiServiceProperties {

	private String baseUrl;
	private int connectTimeoutMs = 5000;
	private int readTimeoutMs = 60000;

	/** Sent as X-Service-Token; must match the AI service's SERVICE_TOKEN. Blank = not sent. */
	private String serviceToken;

	/** Rejected before calling the AI service; keep equal to its MAX_UPLOAD_MB. */
	private int maxUploadMb = 10;
}

