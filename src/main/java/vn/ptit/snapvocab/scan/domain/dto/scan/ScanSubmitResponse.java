package vn.ptit.snapvocab.scan.domain.dto.scan;

import vn.ptit.snapvocab.scan.domain.enumeration.ScanStatus;

public record ScanSubmitResponse(String requestId, ScanStatus status) {
}

