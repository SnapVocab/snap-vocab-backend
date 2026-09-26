package vn.ptit.snapvocab.scan.domain.dto.scan;

import jakarta.validation.constraints.NotBlank;

/** @param objectKey the {@code objectName} returned by POST /api/scan/upload-url */
public record ScanSubmitRequest(@NotBlank String objectKey) {
}

