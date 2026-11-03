package vn.ptit.snapvocab.notification.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A5. {@code platform} is a String so a wrong value is {@code Validation failed}, not a malformed body.
 * {@code token} is checked by the service, which answers {@code INVALID_DEVICE_TOKEN}.
 */
public record DeviceRegisterRequest(String token, @NotNull @Pattern(regexp = "IOS|ANDROID") String platform) {
}
