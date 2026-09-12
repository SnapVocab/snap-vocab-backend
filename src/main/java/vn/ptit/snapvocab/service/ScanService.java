package vn.ptit.snapvocab.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.LiteScanResponseDTO;
import vn.ptit.snapvocab.domain.dto.ScanResultDTO;

public interface ScanService {

	ScanResultDTO scanImage(MultipartFile image);

	/**
	 * Lite scan: every detection box is returned, but dictionary data is looked up
	 * once per distinct label rather than repeated on each box.
	 */
	LiteScanResponseDTO scanImageLite(MultipartFile image);
}
