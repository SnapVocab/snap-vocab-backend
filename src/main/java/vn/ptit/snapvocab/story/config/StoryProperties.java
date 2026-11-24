package vn.ptit.snapvocab.story.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "story")
public class StoryProperties {

	private int quotaPerDay = 10;
	private int minWords = 3;
	private int maxWords = 20;
	private String zone = "Asia/Ho_Chi_Minh";
	private String encryptionSecret = "snapvocab-ai-provider-default-secret-32b";
	private String fallbackGeminiApiKey = "";
	private String fallbackGeminiModel = "gemini-2.5-flash";
}

