package vn.ptit.snapvocab.scan.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(ScanProperties.class)
@Configuration
public class ScanConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}

