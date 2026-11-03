package vn.ptit.snapvocab.notification.domain.dto;

import java.time.Instant;
import vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform;

/** A5. */
public record DeviceDTO(DevicePlatform platform, Instant lastSeenAt) {
}
