package com.company.chatdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

/**
 * App-specific settings from the {@code app.*} properties.
 *
 * @param storageDir    folder where uploaded files are saved
 * @param maxUploadSize largest file a user may upload
 * @param ingestion     how uploaded files are split into chunks
 * @param rag           how chunks are retrieved for a question
 */
@ConfigurationProperties("app")
public record AppProperties(
		@DefaultValue("./data/uploads") Path storageDir,
		@DefaultValue("20MB") DataSize maxUploadSize,
		@DefaultValue Ingestion ingestion,
		@DefaultValue Rag rag) {

	/**
	 * @param topK                how many chunks to retrieve per question
	 * @param similarityThreshold minimum cosine similarity (0..1) for a chunk to count as relevant
	 * @param historyMessages     how many earlier messages of the chat are sent along with a question
	 */
	public record Rag(
			@DefaultValue("5") int topK,
			@DefaultValue("0.55") double similarityThreshold,
			@DefaultValue("6") int historyMessages) {
	}

	/**
	 * @param chunkSize           target chunk size in tokens
	 * @param batchSize           chunks embedded per Gemini request
	 * @param pauseBetweenBatches wait between batches to stay under the free-tier tokens-per-minute limit
	 */
	public record Ingestion(
			@DefaultValue("500") int chunkSize,
			@DefaultValue("20") int batchSize,
			@DefaultValue("20s") Duration pauseBetweenBatches) {
	}

}
