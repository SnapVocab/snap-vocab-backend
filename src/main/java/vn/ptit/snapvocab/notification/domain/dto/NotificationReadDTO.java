package vn.ptit.snapvocab.notification.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** A3. */
public record NotificationReadDTO(Long id, @JsonProperty("isRead") Boolean isRead, Instant readAt) {
}
