package vn.ptit.snapvocab.domain.dto.scan;

import jakarta.validation.constraints.NotBlank;

/**
 * @param contentType image/jpeg, image/png or image/webp; the app must PUT the
 *                    file with exactly this Content-Type
 */
public record ScanUploadUrlRequest(@NotBlank String contentType) {
}
