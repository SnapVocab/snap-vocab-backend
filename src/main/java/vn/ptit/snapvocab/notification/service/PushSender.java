package vn.ptit.snapvocab.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Metrics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.ptit.snapvocab.notification.repository.DeviceTokenRepository;

/**
 * J2 (notification.md §4.4): Expo Push API, called after the in-app notification committed. A failed push never
 * touches the in-app copy, and is not re-sent on a later scan (the dedupe key already exists).
 * ponytail: sequential and on the scheduler thread, retries included (≤ ~40 s); MVP does not read push receipts.
 */
@Slf4j
@Component
public class PushSender {

	static final int BATCH = 100;
	/** SRS_REMINDER is the only pushed type (§2): its channel, priority and 4-hour TTL. */
	static final String CHANNEL = "srs-reminder";
	static final int TTL_SECONDS = 14_400;

	/** {@code badge}: iOS app icon number, null leaves it unchanged. */
	public record PushMessage(String to, String title, String body, Map<String, Object> data, Integer badge) {
	}

	private final String url;
	private final RestClient client;
	private final DeviceTokenRepository deviceTokenRepository;
	private final TransactionTemplate transaction;
	private final long[] backoffMs;

	public PushSender(@Value("${notification.push.url:https://exp.host/--/api/v2/push/send}") String url,
			@Value("${notification.push.access-token:}") String accessToken,
			@Value("${notification.push.backoff-ms:2000,8000,30000}") long[] backoffMs,
			DeviceTokenRepository deviceTokenRepository, PlatformTransactionManager transactionManager) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(10_000);
		this.url = url;
		RestClient.Builder builder = RestClient.builder().requestFactory(factory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		if (!accessToken.isBlank()) { // "Enhanced push security" on EAS; EXPO_ACCESS_TOKEN in a secret env
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		this.client = builder.build();
		this.deviceTokenRepository = deviceTokenRepository;
		this.transaction = new TransactionTemplate(transactionManager);
		this.backoffMs = backoffMs;
	}

	/** Batches of at most 100, one after another (well under Expo's 600 pushes/second). */
	public void send(List<PushMessage> messages) {
		for (int i = 0; i < messages.size(); i += BATCH) {
			deliver(messages.subList(i, Math.min(messages.size(), i + BATCH)));
		}
	}

	private void deliver(List<PushMessage> batch) {
		List<PushMessage> pending = batch;
		for (int attempt = 0; ; attempt++) {
			List<PushMessage> retry;
			try {
				JsonNode response = client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
						.body(pending.stream().map(PushSender::payload).toList())
						.retrieve().body(JsonNode.class);
				retry = tickets(pending, response == null ? null : response.path("data"));
			} catch (RestClientResponseException e) {
				int status = e.getStatusCode().value();
				if (status != 429 && status < 500) {
					failed(pending.size(), "http_" + status);
					log.error("Expo push rejected the batch: HTTP {}", status);
					return;
				}
				retry = pending;
			} catch (ResourceAccessException e) { // timeout, network
				retry = pending;
			}
			if (retry.isEmpty()) {
				return;
			}
			if (attempt >= backoffMs.length) {
				failed(retry.size(), "retries_exhausted");
				log.warn("Expo push gave up on {} messages after {} retries", retry.size(), backoffMs.length);
				return;
			}
			if (!sleep(backoffMs[attempt])) {
				failed(retry.size(), "interrupted");
				return;
			}
			pending = retry;
		}
	}

	/** One ticket per message, in order. Returns the messages to retry (MessageRateExceeded). */
	private List<PushMessage> tickets(List<PushMessage> sent, JsonNode tickets) {
		if (tickets == null || !tickets.isArray() || tickets.size() != sent.size()) {
			failed(sent.size(), "bad_response");
			log.error("Expo push answered without one ticket per message");
			return List.of();
		}
		List<PushMessage> retry = new ArrayList<>();
		List<String> dead = new ArrayList<>();
		for (int i = 0; i < sent.size(); i++) {
			JsonNode ticket = tickets.get(i);
			if ("ok".equals(ticket.path("status").asText())) {
				Metrics.counter("push.sent").increment();
				continue;
			}
			String error = ticket.path("details").path("error").asText("Unknown");
			String token = sent.get(i).to();
			switch (error) {
				case "DeviceNotRegistered" -> dead.add(token);
				case "MessageRateExceeded" -> retry.add(sent.get(i));
				case "MessageTooBig" -> log.error("Push too big for …{}: template bug", DeviceTokenService.tail(token));
				case "InvalidCredentials" -> log.error("Expo push InvalidCredentials: check FCM/APNs keys on EAS");
				default -> log.warn("Expo push error {} for …{}", error, DeviceTokenService.tail(token));
			}
			if (!error.equals("MessageRateExceeded")) {
				failed(1, error);
			}
		}
		if (!dead.isEmpty()) {
			transaction.executeWithoutResult(s -> deviceTokenRepository.deleteByTokens(dead));
			Metrics.counter("device_token.removed", "reason", "not_registered").increment(dead.size());
			log.info("Removed {} unregistered device tokens", dead.size());
		}
		return retry;
	}

	private static Map<String, Object> payload(PushMessage m) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("to", m.to());
		p.put("title", m.title());
		p.put("body", m.body());
		p.put("data", m.data());
		if (m.badge() != null) {
			p.put("badge", m.badge());
		}
		p.put("sound", "default");
		p.put("channelId", CHANNEL);
		p.put("priority", "default");
		p.put("ttl", TTL_SECONDS);
		return p;
	}

	private static void failed(int count, String reason) {
		Metrics.counter("push.failed", "reason", reason).increment(count);
	}

	/** False when interrupted (shutdown): stop retrying, keep the interrupt flag. */
	private static boolean sleep(long ms) {
		try {
			Thread.sleep(ms);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
