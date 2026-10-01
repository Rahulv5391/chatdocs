package com.company.chatdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * App-specific settings from the {@code app.*} properties.
 *
 * @param maxUploadSize largest file a user may upload (also the limit for a page imported from a URL)
 * @param ingestion     how uploaded files are split into chunks
 * @param rag           how chunks are retrieved for a question
 * @param urlImport     how documents are fetched from a web address
 */
@ConfigurationProperties("app")
public record AppProperties(
		@DefaultValue("20MB") DataSize maxUploadSize,
		@DefaultValue Ingestion ingestion,
		@DefaultValue Rag rag,
		@DefaultValue UrlImport urlImport) {

	/**
	 * @param topK                how many chunks to retrieve per question
	 * @param similarityThreshold minimum cosine similarity (0..1) for a chunk to count as relevant
	 * @param historyMessages     how many earlier messages of the chat are sent along with a question
	 * @param maxQuestionLength   longest question accepted, in characters
	 */
	public record Rag(
			@DefaultValue("5") int topK,
			@DefaultValue("0.55") double similarityThreshold,
			@DefaultValue("6") int historyMessages,
			@DefaultValue("2000") int maxQuestionLength) {
	}

	/**
	 * @param chunkSize           target chunk size in tokens
	 * @param chunkOverlap        characters from the end of the previous chunk repeated at the start of the next
	 * @param batchSize           chunks embedded per Gemini request
	 * @param pauseBetweenBatches wait between batches to stay under the free-tier tokens-per-minute limit
	 * @param maxOcrPages         most PDF pages without a text layer read by Gemini (one request per page)
	 * @param summarize           whether to write a summary of each document (one request per document)
	 * @param summaryInputChars   how much of the document's text the summary is based on
	 */
	public record Ingestion(
			@DefaultValue("500") int chunkSize,
			@DefaultValue("200") int chunkOverlap,
			@DefaultValue("20") int batchSize,
			@DefaultValue("20s") Duration pauseBetweenBatches,
			@DefaultValue("20") int maxOcrPages,
			@DefaultValue("true") boolean summarize,
			@DefaultValue("60000") int summaryInputChars) {
	}

	/**
	 * @param timeout            longest a single request to the web address may take
	 * @param allowPrivateHosts  allow addresses inside the server's own network (localhost, 10.x, 192.168.x, ...).
	 *                           Keep false outside tests: otherwise users could make the server fetch internal pages.
	 */
	public record UrlImport(
			@DefaultValue("20s") Duration timeout,
			@DefaultValue("false") boolean allowPrivateHosts) {
	}

}
