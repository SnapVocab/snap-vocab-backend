package vn.ptit.snapvocab.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@EnableConfigurationProperties(AiServiceProperties.class)
@Configuration
@RequiredArgsConstructor
public class AiServiceConfig {

	private final AiServiceProperties properties;

	@Bean
	public RestClient aiServiceRestClient() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.getConnectTimeoutMs());
		factory.setReadTimeout(properties.getReadTimeoutMs());

		return RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.requestFactory(factory)
				.build();
	}
}
