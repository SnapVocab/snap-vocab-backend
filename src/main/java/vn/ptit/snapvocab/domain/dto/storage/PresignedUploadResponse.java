package vn.ptit.snapvocab.domain.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    private String uploadUrl;
    private String objectName;
    private String contentType;
    private long expiredAt;
}
