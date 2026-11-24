package vn.ptit.snapvocab.webauthn.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.webauthn.config.WebAuthnProperties;

@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class WellKnownController {

	private final WebAuthnProperties properties;

	/** Android Digital Asset Links file required by Credential Manager for native Passkeys. */
	@GetMapping(value = "/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Map<String, Object>>> assetLinks() {
		List<String> fingerprints = properties.getAndroidSha256Fingerprints().stream()
				.filter(fp -> fp != null && !fp.isBlank())
				.map(String::trim)
				.toList();

		if (fingerprints.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}

		Map<String, Object> statement = Map.of(
				"relation", List.of(
						"delegate_permission/common.handle_all_urls",
						"delegate_permission/common.get_login_creds"
				),
				"target", Map.of(
						"namespace", "android_app",
						"package_name", properties.getAndroidPackage(),
						"sha256_cert_fingerprints", fingerprints
				)
		);
		return ResponseEntity.ok(List.of(statement));
	}

	/** iOS Associated Domains file required for native Passkeys. */
	@GetMapping(value = "/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
		String iosAppId = properties.getIosAppId();
		List<String> apps = (iosAppId == null || iosAppId.isBlank())
				? List.of()
				: List.of(iosAppId.trim());

		return ResponseEntity.ok(Map.of("webcredentials", Map.of("apps", apps)));
	}
}
