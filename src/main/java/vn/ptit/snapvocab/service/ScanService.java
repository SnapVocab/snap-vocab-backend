package vn.ptit.snapvocab.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.ScanItemDTO;

public interface ScanService {

	List<ScanItemDTO> scanImage(MultipartFile image);
}
