package vn.ptit.snapvocab.service;

import vn.ptit.snapvocab.domain.dto.storage.PresignedUploadResponse;
import vn.ptit.snapvocab.domain.enumeration.UploadPurpose;

public interface StorageService {

	PresignedUploadResponse createUploadUrl(String fileName);

	String createDownloadUrl(String objectName);

	boolean exists(String objectName);

	void delete(String objectName);

	String confirmUpload(String objectName, UploadPurpose purpose);
}
