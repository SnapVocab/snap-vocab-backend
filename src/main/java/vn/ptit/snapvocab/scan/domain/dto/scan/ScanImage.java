package vn.ptit.snapvocab.scan.domain.dto.scan;

/**
 * Image bytes to send to the AI service. Decoupled from {@code MultipartFile}
 * so the same call works for uploads and for images read back from storage.
 */
public record ScanImage(byte[] bytes, String filename, String contentType) {
}

