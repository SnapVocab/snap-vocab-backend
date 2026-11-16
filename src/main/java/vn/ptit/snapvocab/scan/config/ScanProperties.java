package vn.ptit.snapvocab.scan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
@Data
public class ScanProperties {

	/** Scans per user per day; FAILED scans do not count. */
	private int quotaPerDay = 20;

	/** The quota resets at midnight in this zone. */
	private String zone = "Asia/Ho_Chi_Minh";

	/**
	 * Jobs sent to the AI service at once. Keep at 1 per GPU: the AI service
	 * runs one image at a time anyway, extra workers only queue there instead of here.
	 */
	private int workers = 1;

	/**
	 * Jobs waiting behind the running ones; beyond this, submissions get AI_QUEUE_FULL.
	 * Kept small so the last job still finishes while the app is waiting
	 * (3 x ~20s on GPU fits the app's 90s polling window).
	 */
	private int queueCapacity = 3;

	/** Extra slack before the sweeper declares a job lost, on top of its worst-case run time. */
	private int staleMarginSeconds = 30;

	/** Lowest reliability shown to the user: HIGH, MEDIUM or LOW (= show everything). */
	private String minReliability = "LOW";

	/** Run ScanDetectionBackfill once at startup (admin_overview_backend.md G2); turn off again afterwards. */
	private boolean detectionsBackfill = false;
}

