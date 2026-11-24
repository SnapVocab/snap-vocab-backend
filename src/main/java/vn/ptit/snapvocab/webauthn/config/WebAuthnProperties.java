package vn.ptit.snapvocab.webauthn.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "webauthn")
public class WebAuthnProperties {

	/** Relying Party ID (effective domain, e.g. "localhost" or "snapvocab.vn"). */
	private String rpId = "localhost";

	/** Human-readable Relying Party display name shown in OS biometric prompts. */
	private String rpName = "SnapVocab";

	/** Origins allowed to submit WebAuthn ceremonies (scheme + host + optional port). */
	private List<String> allowedOrigins = new ArrayList<>(List.of(
			"http://localhost:3000",
			"http://localhost:5173",
			"http://localhost:8080"
	));

	/** How long a single-use challenge stays valid in Redis. */
	private long challengeTtlSeconds = 300L;

	/** Browser ceremony timeout in milliseconds. */
	private long timeoutMs = 60000L;

	/** Require biometric/PIN verification (UV flag) rather than presence-only tap. */
	private boolean requireUserVerification = true;

	/** Android applicationId for /.well-known/assetlinks.json (Digital Asset Links). */
	private String androidPackage = "com.snapvocab";

	/** Colon-separated SHA-256 signing cert fingerprints for /.well-known/assetlinks.json. */
	private List<String> androidSha256Fingerprints = new ArrayList<>();

	/** iOS App ID (<TEAM_ID>.<BUNDLE_ID>) for /.well-known/apple-app-site-association. */
	private String iosAppId = "";
}

