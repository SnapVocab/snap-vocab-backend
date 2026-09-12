package vn.ptit.snapvocab.domain.dto.storage;

import vn.ptit.snapvocab.domain.enumeration.UploadPurpose;

public record UploadCompleteRequest(String objectName, UploadPurpose purpose) {

}
