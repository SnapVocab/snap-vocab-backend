package vn.ptit.snapvocab.domain.dto;

import vn.ptit.snapvocab.domain.enums.UploadPurpose;

public record UploadCompleteRequest(String objectName, UploadPurpose purpose) {}
