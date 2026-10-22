package vn.ptit.snapvocab.notification.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import vn.ptit.snapvocab.notification.domain.enumeration.NotificationType;

/** A1. {@code nextCursor} is null when the page is not full. */
public record NotificationPageDTO(List<Item> items, Long nextCursor) {

	/** {@code id} is the user_notifications id: the one A3 takes and the cursor pages by. */
	public record Item(Long id, NotificationType type, String title, String content, String route,
			@JsonProperty("isRead") Boolean isRead, Instant readAt, Instant createdAt) {
	}
}
