package vn.ptit.snapvocab.storage.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.domain.dto.UploadCompleteRequest;
import vn.ptit.snapvocab.storage.domain.dto.UploadUrlRequest;
import vn.ptit.snapvocab.storage.service.StorageService;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUploadResponse> createUploadUrl(@RequestBody UploadUrlRequest request) {
        PresignedUploadResponse response = storageService.createUploadUrl(request.fileName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-complete")
    public ResponseEntity<?> confirmUpload(@RequestBody UploadCompleteRequest request) {
        String url = storageService.confirmUpload(request.objectName(), request.purpose());
        return ResponseEntity.ok(Map.of("status", "ok", "url", url));
    }
}

