package vn.ptit.snapvocab.domain.dto.scan;

import vn.ptit.snapvocab.domain.enumeration.ScanStatus;

public record ScanSubmitResponse(String requestId, ScanStatus status) {
}
