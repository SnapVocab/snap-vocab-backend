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

	/** Sent as a Bearer token; a RunPod Serverless endpoint requires it. Blank = not sent. */
	private String apiKey;

	/**
	 * Extra attempts when the AI service is unreachable or times out, for a Serverless
	 * endpoint whose worker may still be booting. 0 = a single attempt.
	 */
	private int coldStartRetries = 0;

	private long retryDelayMs = 5000;

	/** Rejected before calling the AI service; keep equal to its MAX_UPLOAD_MB. */
	private int maxUploadMb = 10;

	/**
	 * Photos are shrunk to this long edge before being sent; keep equal to the AI
	 * service's MAX_INPUT_SIZE, which discards anything larger. 0 = send as is.
	 */
	private int maxInputPx = 1400;

	/** Longest one scan can spend calling the AI service, retries included. */
	public long maxDetectTimeMs() {
		return readTimeoutMs * (coldStartRetries + 1L) + retryDelayMs * coldStartRetries;
	}
}

