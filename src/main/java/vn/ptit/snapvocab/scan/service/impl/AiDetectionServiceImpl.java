package vn.ptit.snapvocab.scan.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.ptit.snapvocab.scan.config.AiServiceProperties;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiDetectResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.AiErrorResponseDTO;
import vn.ptit.snapvocab.scan.domain.dto.scan.ScanImage;
import vn.ptit.snapvocab.scan.domain.enumeration.ScanErrorCode;
import vn.ptit.snapvocab.scan.service.AiDetectionService;
import vn.ptit.snapvocab.scan.service.ScanImageDownscaler;
import vn.ptit.snapvocab.scan.exception.ScanException;

/**
 * Calls the AI service and turns every failure into a {@link ScanException}
 * with
 * a code the app can act on, instead of a bare 500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDetectionServiceImpl implements AiDetectionService {

	private static final ObjectMapper ERROR_READER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final RestClient aiServiceRestClient;
	private final AiServiceProperties properties;

	@Override
	public AiDetectResponseDTO detect(ScanImage original, String requestId) {
		ScanImage image = ScanImageDownscaler.fit(original, properties.getMaxInputPx());
		AiDetectResponseDTO response = sendWithRetries(image, requestId);
		log.info("scan request_id={} model={} objects={} ai_time_ms={} sent_bytes={} original_bytes={}",
				requestId, response.modelVersion(), response.detections().size(), response.processingTimeMs(),
				image.bytes().length, original.bytes().length);
		return response;
	}

	/**
	 * A scaled-to-zero endpoint can fail a request while its worker is still booting,
	 * so "not there yet" is retried; anything the AI service itself decided is final.
	 */
	private AiDetectResponseDTO sendWithRetries(ScanImage image, String requestId) {
		for (int attempt = 0;; attempt++) {
			try {
				return send(image, requestId);
			} catch (ScanException e) {
				boolean notThereYet = e.getCode() == ScanErrorCode.AI_UNAVAILABLE
						|| e.getCode() == ScanErrorCode.AI_TIMEOUT;
				if (!notThereYet || attempt >= properties.getColdStartRetries()) {
					throw e;
				}
				log.warn("Scan {} attempt {} failed with {}, retrying", requestId, attempt + 1, e.getCode());
				try {
					Thread.sleep(properties.getRetryDelayMs());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private AiDetectResponseDTO send(ScanImage image, String requestId) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		// Set the part type explicitly: inferring it from the filename yields
		// application/octet-stream for names without an extension, which the AI
		// rejects.
		body.part("file", new ByteArrayResource(image.bytes()))
				.filename(image.filename())
				.contentType(MediaType.parseMediaType(image.contentType()));

		AiDetectResponseDTO response;
		try {
			response = aiServiceRestClient.post()
					.uri("/api/v1/detect")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.headers(h -> {
						h.set("X-Request-Id", requestId);
						if (StringUtils.hasText(properties.getServiceToken())) {
							h.set("X-Service-Token", properties.getServiceToken());
						}
						if (StringUtils.hasText(properties.getApiKey())) {
							h.setBearerAuth(properties.getApiKey());
						}
					})
					.body(body.build())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (req, res) -> {
						throw toScanException(res, requestId);
					})
					.body(AiDetectResponseDTO.class);
		} catch (ResourceAccessException e) {
			throw isReadTimeout(e)
					? new ScanException(ScanErrorCode.AI_TIMEOUT,
							"AI service timed out for request " + requestId, e)
					: new ScanException(ScanErrorCode.AI_UNAVAILABLE,
							"AI service unreachable for request " + requestId, e);
		} catch (RestClientException e) {
			throw new ScanException(ScanErrorCode.AI_ERROR,
					"Unreadable AI response for request " + requestId, e);
		}

		if (response == null || response.detections() == null) {
			throw new ScanException(ScanErrorCode.AI_ERROR, "Empty AI response for request " + requestId);
		}
		return response;
	}

	private ScanException toScanException(ClientHttpResponse res, String requestId) throws IOException {
		HttpStatusCode status = res.getStatusCode();
		AiErrorResponseDTO.Detail detail = readError(res);
		String aiCode = detail != null ? detail.code() : null;
		String message = "AI service returned " + status.value() + " " + aiCode
				+ (detail != null ? " (" + detail.message() + ")" : "") + " for request " + requestId;

		ScanErrorCode code;
		if ("INVALID_IMAGE".equals(aiCode)) {
			code = ScanErrorCode.INVALID_IMAGE;
		} else if ("MODEL_NOT_READY".equals(aiCode) || status.value() == 503 || isGatewayError(status, detail)) {
			code = ScanErrorCode.AI_UNAVAILABLE;
		} else {
			// MODEL_ERROR, or our own fault: INVALID_REQUEST (malformed call) and
			// UNAUTHORIZED (token mismatch) are misconfiguration, not the user's image.
			code = ScanErrorCode.AI_ERROR;
		}
		return new ScanException(code, message);
	}

	/**
	 * Not the AI service's JSON shape, so something in front of it answered (e.g. RunPod's
	 * "no workers available"). A rejected key stays AI_ERROR: retrying cannot fix it.
	 */
	private boolean isGatewayError(HttpStatusCode status, AiErrorResponseDTO.Detail detail) {
		return detail == null && status.value() != 401 && status.value() != 403;
	}

	private AiErrorResponseDTO.Detail readError(ClientHttpResponse res) {
		try {
			AiErrorResponseDTO body = ERROR_READER.readValue(res.getBody(), AiErrorResponseDTO.class);
			return body != null ? body.error() : null;
		} catch (IOException | RuntimeException e) {
			return null; // not our JSON shape (e.g. a proxy's HTML page); fall back to the status
		}
	}

	/**
	 * Connect timeouts mean the service is down; only read timeouts mean it is too
	 * slow.
	 */
	private boolean isReadTimeout(ResourceAccessException e) {
		if (!(e.getCause() instanceof SocketTimeoutException timeout)) {
			return false;
		}
		String message = timeout.getMessage();
		return message == null || !message.toLowerCase(Locale.ROOT).contains("connect");
	}
}

