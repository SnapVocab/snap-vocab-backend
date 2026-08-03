package vn.ptit.snapvocab.service;

import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.AiDetectResponseDTO;

public interface AiDetectionService {

	AiDetectResponseDTO detect(MultipartFile image);
}
