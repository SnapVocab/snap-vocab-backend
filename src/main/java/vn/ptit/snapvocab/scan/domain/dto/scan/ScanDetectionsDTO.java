package vn.ptit.snapvocab.scan.domain.dto.scan;

import java.util.List;

/**
 * What a finished scan stores ({@code scan_requests.result_json}): the AI's boxes
 * and the coordinate space they live in, without dictionary data.
 */
public record ScanDetectionsDTO(int imageWidth, int imageHeight, List<DetectionDTO> detections) {
}

