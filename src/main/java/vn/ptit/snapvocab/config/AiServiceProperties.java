package vn.ptit.snapvocab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-service")
@Data
public class AiServiceProperties {

	private String baseUrl;
	private int connectTimeoutMs = 5000;
	private int readTimeoutMs = 60000;
}
