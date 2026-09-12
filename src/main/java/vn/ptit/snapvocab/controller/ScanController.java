package vn.ptit.snapvocab.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;
import vn.ptit.snapvocab.service.ScanService;

@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

	private final ScanService scanService;

	@PostMapping
	public ResponseEntity<ScanResultDTO> scan(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(scanService.scanImage(file));
	}
}
