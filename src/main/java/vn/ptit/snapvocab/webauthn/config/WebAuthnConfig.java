package vn.ptit.snapvocab.webauthn.config;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfig {

	@Bean
	public ObjectConverter webAuthnObjectConverter() {
		return new ObjectConverter();
	}

	@Bean
	public WebAuthnManager webAuthnManager(ObjectConverter webAuthnObjectConverter) {
		return WebAuthnManager.createNonStrictWebAuthnManager(webAuthnObjectConverter);
	}
}

