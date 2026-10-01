package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Embeds and stores chunks in small batches, pausing between batches so a big document stays under Gemini's
 * free-tier limits (~100 requests and ~30k tokens per minute). A batch that still hits a 429 is retried.
 */
@Component
public class EmbeddingThrottler {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingThrottler.class);

	private final VectorStore vectorStore;
	private final int batchSize;
	private final Duration pause;

	EmbeddingThrottler(VectorStore vectorStore, AppProperties properties) {
		this.vectorStore = vectorStore;
		this.batchSize = properties.ingestion().batchSize();
		this.pause = properties.ingestion().pauseBetweenBatches();
	}

	/** Embeds and stores all chunks. Each vectorStore.add call embeds one batch with Gemini, then inserts it. */
	public void addInBatches(List<Document> chunks) {
		for (int start = 0; start < chunks.size(); start += batchSize) {
			if (start > 0) {
				sleep(pause);
			}
			List<Document> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
			addWithRetry(batch);
			log.debug("Embedded chunks {}-{} of {}", start + 1, start + batch.size(), chunks.size());
		}
	}

	private void addWithRetry(List<Document> batch) {
		RateLimitRetry.call(() -> {
			vectorStore.add(batch);
			return null;
		});
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting between embedding batches", e);
		}
	}

}
