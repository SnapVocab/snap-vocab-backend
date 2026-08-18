package vn.ptit.snapvocab.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.scan.ScanResultDTO;

public interface ScanService {

	ScanResultDTO scanImage(MultipartFile image);
}
