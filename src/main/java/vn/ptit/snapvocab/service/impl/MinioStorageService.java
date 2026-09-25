package vn.ptit.snapvocab.service.impl;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.config.MinioProperties;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.domain.dto.storage.PresignedUploadResponse;
import vn.ptit.snapvocab.domain.enumeration.UploadPurpose;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.service.StorageService;
import vn.ptit.snapvocab.util.exception.BadRequestAlertException;

@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

	private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

	private final MinioClient minioClient;
	private final MinioProperties properties;
	private final UserRepository userRepository;

	@Override
	public PresignedUploadResponse createUploadUrl(String fileName) {
		return createUploadUrl(UUID.randomUUID() + "-" + fileName, resolveContentType(fileName));
	}

	@Override
	public PresignedUploadResponse createUploadUrl(String objectName, String contentType) {
		long expirySeconds = 300;
		long expiresAt = System.currentTimeMillis() + expirySeconds * 1000;

		try {
			Map<String, String> queryParams = Map.of("Content-Type", contentType);

			String uploadUrl = minioClient.getPresignedObjectUrl(
					GetPresignedObjectUrlArgs.builder()
							.method(Method.PUT)
							.bucket(properties.getBucket())
							.object(objectName)
							.extraQueryParams(queryParams)
							.expiry((int) expirySeconds, TimeUnit.SECONDS)
							.build());

			return new PresignedUploadResponse(uploadUrl, objectName, contentType, expiresAt);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create upload URL", e);
		}
	}

	private String resolveContentType(String fileName) {
		String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
		return switch (ext) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			case "svg" -> "image/svg+xml";
			case "mp4" -> "video/mp4";
			case "mp3" -> "audio/mpeg";
			case "pdf" -> "application/pdf";
			default -> "application/octet-stream";
		};
	}

	@Override
	public String createDownloadUrl(String objectName) {
		try {
			return minioClient.getPresignedObjectUrl(
					GetPresignedObjectUrlArgs.builder()
							.method(Method.GET)
							.bucket(properties.getBucket())
							.object(objectName)
							.expiry(30, TimeUnit.MINUTES)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create download URL", e);
		}
	}

	@Override
	public boolean exists(String objectName) {
		try {
			minioClient.statObject(
					StatObjectArgs.builder()
							.bucket(properties.getBucket())
							.object(objectName)
							.build());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** BF-04: an avatar must be an image of at most 5MB, checked server side. */
	private void validateAvatar(String objectName) {
		if (!resolveContentType(objectName).startsWith("image/")) {
			throw new BadRequestAlertException("Avatar must be an image");
		}
		long size = sizeOf(objectName).orElse(0L);
		if (size > MAX_AVATAR_BYTES) {
			throw new BadRequestAlertException("Avatar exceeds " + (MAX_AVATAR_BYTES / 1024 / 1024) + "MB");
		}
	}

	@Override
	public Optional<Long> sizeOf(String objectName) {
		try {
			StatObjectResponse stat = minioClient.statObject(
					StatObjectArgs.builder()
							.bucket(properties.getBucket())
							.object(objectName)
							.build());
			return Optional.of(stat.size());
		} catch (ErrorResponseException e) {
			if ("NoSuchKey".equals(e.errorResponse().code())) {
				return Optional.empty();
			}
			throw new RuntimeException("Failed to stat object", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to stat object", e);
		}
	}

	@Override
	public byte[] download(String objectName) {
		try (GetObjectResponse object = minioClient.getObject(
				GetObjectArgs.builder()
						.bucket(properties.getBucket())
						.object(objectName)
						.build())) {
			return object.readAllBytes();
		} catch (Exception e) {
			throw new RuntimeException("Failed to download object", e);
		}
	}

	@Override
	@Transactional
	public String confirmUpload(String objectName, UploadPurpose purpose) {
		if (!exists(objectName)) {
			throw new RuntimeException("Object not found in storage: " + objectName);
		}

		String downloadUrl = createDownloadUrl(objectName);

		switch (purpose) {
			case AVATAR -> {
				validateAvatar(objectName);
				String email = SecurityUtils.getCurrentUserLogin()
						.orElseThrow(() -> new RuntimeException("Not authenticated"));
				User user = userRepository.findOneByEmailIgnoreCase(email)
						.orElseThrow(() -> new RuntimeException("User not found"));
				// Store the object key, not the presigned URL: that URL expires
				// (BF-04: TTL <= 15 min) and a stored copy stops loading.
				user.setAvatarUrl(objectName);
				userRepository.save(user);
			}
		}

		return downloadUrl;
	}

	@Override
	public void delete(String objectName) {
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(properties.getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete object", e);
		}
	}
}
