package vn.ptit.snapvocab.service;

import vn.ptit.snapvocab.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.domain.enums.UploadPurpose;

public interface StorageService {

    PresignedUploadResponse createUploadUrl(String fileName);

    String createDownloadUrl(String objectName);

    boolean exists(String objectName);

    void delete(String objectName);

    String confirmUpload(String objectName, UploadPurpose purpose);
}
