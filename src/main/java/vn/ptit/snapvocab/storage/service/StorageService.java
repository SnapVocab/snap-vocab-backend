package vn.ptit.snapvocab.storage.service;

import java.util.Optional;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.domain.enumeration.UploadPurpose;

public interface StorageService {

	PresignedUploadResponse createUploadUrl(String fileName);

	/** Presigned PUT for a caller-chosen key; the client must upload with exactly {@code contentType}. */
	PresignedUploadResponse createUploadUrl(String objectName, String contentType);

	String createDownloadUrl(String objectName);

	boolean exists(String objectName);

	/** Object size in bytes, or empty when the object does not exist. */
	Optional<Long> sizeOf(String objectName);

	byte[] download(String objectName);

	void delete(String objectName);

	String confirmUpload(String objectName, UploadPurpose purpose);
}

