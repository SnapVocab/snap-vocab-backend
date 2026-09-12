package vn.ptit.snapvocab.service.impl;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import vn.ptit.snapvocab.domain.dto.AiDetectResponseDTO;
import vn.ptit.snapvocab.domain.dto.AiLiteDetectResponseDTO;
import vn.ptit.snapvocab.service.AiDetectionService;

@Service
@RequiredArgsConstructor
public class AiDetectionServiceImpl implements AiDetectionService {

	private final RestClient aiServiceRestClient;

	@Override
	public AiDetectResponseDTO detect(MultipartFile image) {
		return post("/api/v1/detect", image, AiDetectResponseDTO.class);
	}

	@Override
	public AiLiteDetectResponseDTO detectLite(MultipartFile image) {
		return post("/api/v1/detect/lite", image, AiLiteDetectResponseDTO.class);
	}

	private <T> T post(String uri, MultipartFile image, Class<T> responseType) {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", toResource(image));

		try {
			return aiServiceRestClient.post()
					.uri(uri)
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(body)
					.retrieve()
					.body(responseType);
		} catch (Exception e) {
			throw new RuntimeException("AI service detection failed", e);
		}
	}

	private ByteArrayResource toResource(MultipartFile image) {
		byte[] bytes;
		try {
			bytes = image.getBytes();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read uploaded image", e);
		}
		String filename = image.getOriginalFilename() != null ? image.getOriginalFilename() : "photo.jpg";
		return new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
