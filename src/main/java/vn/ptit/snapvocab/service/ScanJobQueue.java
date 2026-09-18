package vn.ptit.snapvocab.service;

import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.config.ScanProperties;

/**
 * Bounded, in-process recognition queue (BF-06 rule 5).
 *
 * <p>The executor is deliberately NOT a Spring bean: registering any Executor
 * bean makes Spring Boot drop its default task executor, and {@code @Async}
 * mail would then run on this one-thread queue.
 *
 * <p>Jobs live in memory only: a restart drops them, and {@link ScanRequestSweeper}
 * marks them INTERRUPTED on startup. This assumes a single backend instance.
 */
@Component
public class ScanJobQueue implements DisposableBean {

	private final ThreadPoolTaskExecutor executor;

	public ScanJobQueue(ScanProperties properties) {
		executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.getWorkers());
		executor.setMaxPoolSize(properties.getWorkers());
		executor.setQueueCapacity(properties.getQueueCapacity());
		executor.setThreadNamePrefix("scan-");
		executor.initialize();
	}

	/** @throws RejectedExecutionException when the queue is full */
	public void submit(Runnable job) {
		executor.execute(job);
	}

	@Override
	public void destroy() {
		executor.shutdown();
	}
}
