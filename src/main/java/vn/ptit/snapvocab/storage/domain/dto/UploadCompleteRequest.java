package vn.ptit.snapvocab.storage.domain.dto;

import vn.ptit.snapvocab.storage.domain.enumeration.UploadPurpose;

public record UploadCompleteRequest(String objectName, UploadPurpose purpose) {

}

