package vn.ptit.snapvocab.storage.service.impl;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import vn.ptit.snapvocab.storage.config.MinioProperties;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.domain.enumeration.UploadPurpose;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.common.exception.BadRequestAlertException;

@Service
public class MinioStorageService implements StorageService {

	private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
	private static final String AVATAR_PREFIX = "public/avatars/";

	private final MinioClient minioClient;
	private final MinioClient presigningMinioClient;
	private final MinioProperties properties;
	private final UserRepository userRepository;

	public MinioStorageService(@Qualifier("minioClient") MinioClient minioClient,
			@Qualifier("presigningMinioClient") MinioClient presigningMinioClient,
			MinioProperties properties, UserRepository userRepository) {
		this.minioClient = minioClient;
		this.presigningMinioClient = presigningMinioClient;
		this.properties = properties;
		this.userRepository = userRepository;
	}

	@Override
	public PresignedUploadResponse createUploadUrl(String fileName) {
		if (!StringUtils.hasText(fileName)) {
			throw new BadRequestAlertException("Avatar file name is required");
		}
		String contentType = resolveContentType(fileName);
		if (!contentType.startsWith("image/")) {
			throw new BadRequestAlertException("Avatar must be an image");
		}
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return createUploadUrl(AVATAR_PREFIX + UUID.randomUUID() + "." + extension, contentType);
	}

	@Override
	public PresignedUploadResponse createUploadUrl(String objectName, String contentType) {
		long expirySeconds = 300;
		long expiresAt = System.currentTimeMillis() + expirySeconds * 1000;

		try {
			Map<String, String> queryParams = Map.of("Content-Type", contentType);

			String uploadUrl = presigningMinioClient.getPresignedObjectUrl(
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
		String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
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
		if (objectName.startsWith("public/")) {
			UriComponentsBuilder url = StringUtils.hasText(properties.getPublicBaseUrl())
					? UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
					: UriComponentsBuilder.fromUriString(properties.getEndpoint()).pathSegment(properties.getBucket());
			return url.pathSegment(objectName.split("/")).build().encode().toUriString();
		}
		try {
			return presigningMinioClient.getPresignedObjectUrl(
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
		return sizeOf(objectName).isPresent();
	}

	/** BF-04: an avatar must be an image of at most 5MB, checked server side. */
	private void validateAvatar(String objectName) {
		if (!objectName.startsWith(AVATAR_PREFIX)) {
			throw new BadRequestAlertException("Avatar must be uploaded under " + AVATAR_PREFIX);
		}
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
			throw new BadRequestAlertException("Uploaded object not found in storage: " + objectName);
		}

		String downloadUrl = createDownloadUrl(objectName);

		switch (purpose) {
			case AVATAR -> {
				validateAvatar(objectName);
				String email = SecurityUtils.getCurrentUserLogin()
						.orElseThrow(() -> new RuntimeException("Not authenticated"));
				User user = userRepository.findOneByEmailIgnoreCase(email)
						.orElseThrow(() -> new RuntimeException("User not found"));
				// Keep the key so changing the public/CDN base URL does not require DB updates.
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

