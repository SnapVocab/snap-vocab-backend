package vn.ptit.snapvocab.notification.domain.dto;

/** A6. No validation: anything that is not the caller's token just answers {@code removed: false}. */
public record DeviceUnregisterRequest(String token) {
}
