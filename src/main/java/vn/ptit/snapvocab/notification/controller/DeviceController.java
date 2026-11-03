package vn.ptit.snapvocab.notification.controller;

import static vn.ptit.snapvocab.notification.controller.NotificationController.currentUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.notification.domain.dto.DeviceDTO;
import vn.ptit.snapvocab.notification.domain.dto.DeviceRegisterRequest;
import vn.ptit.snapvocab.notification.domain.dto.DeviceUnregisterRequest;
import vn.ptit.snapvocab.notification.domain.dto.DeviceUnregisterResultDTO;
import vn.ptit.snapvocab.notification.domain.enumeration.DevicePlatform;
import vn.ptit.snapvocab.notification.service.DeviceTokenService;

/** Expo push tokens (notification.md §4.3 A5/A6, §3.9). */
@RestController
@RequestMapping("/api/me/devices")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class DeviceController {

	private final DeviceTokenService deviceTokenService;

	@PostMapping
	@Operation(summary = "Register or refresh this device's push token; moves it if another account owned it")
	public ResponseEntity<DeviceDTO> register(@Valid @RequestBody DeviceRegisterRequest request) {
		return ResponseEntity.ok(deviceTokenService.register(currentUserId(), request.token(),
				DevicePlatform.valueOf(request.platform())));
	}

	@PostMapping("/unregister")
	@Operation(summary = "Drop this device's token before logout; always 200")
	public ResponseEntity<DeviceUnregisterResultDTO> unregister(@RequestBody DeviceUnregisterRequest request) {
		return ResponseEntity.ok(deviceTokenService.unregister(currentUserId(), request.token()));
	}
}
