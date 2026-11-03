package vn.ptit.snapvocab.notification.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A8. {@code route} is checked against the whitelist by the service ({@code ROUTE_NOT_ALLOWED}). */
public record SystemNotificationRequest(
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 1000) String content,
		String route,
		@NotNull @Pattern(regexp = "[a-z0-9-]{3,100}") String dedupeKey) {

	/** Trimmed before validation, so the length limits apply to what is stored. */
	public SystemNotificationRequest {
		title = title == null ? null : title.strip();
		content = content == null ? null : content.strip();
	}
}
