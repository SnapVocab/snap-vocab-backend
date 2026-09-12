package vn.ptit.snapvocab.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {

	private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

	public void success(String event, Long userId) {
		AUDIT_LOG.atInfo()
				.addKeyValue("event", event)
				.addKeyValue("outcome", "SUCCESS")
				.addKeyValue("userId", userId)
				.log("Security event");
	}

	public void failure(String event, String subject, String reason) {
		AUDIT_LOG.atWarn()
				.addKeyValue("event", event)
				.addKeyValue("outcome", "FAILURE")
				.addKeyValue("subjectHash", hashSubject(subject))
				.addKeyValue("reason", reason)
				.log("Security event");
	}

	public void unauthorizedAccess(String method, String path, String remoteAddress) {
		AUDIT_LOG.atWarn()
				.addKeyValue("event", "UNAUTHORIZED_ACCESS")
				.addKeyValue("outcome", "FAILURE")
				.addKeyValue("method", method)
				.addKeyValue("path", path)
				.addKeyValue("remoteAddress", remoteAddress)
				.addKeyValue("reason", "AUTHENTICATION_REQUIRED_OR_INVALID")
				.log("Security event");
	}

	private String hashSubject(String subject) {
		if (subject == null || subject.isBlank()) {
			return "unknown";
		}

		try {
			String normalizedSubject = subject.trim().toLowerCase(Locale.ROOT);
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(normalizedSubject.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
