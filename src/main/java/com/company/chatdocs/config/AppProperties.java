package com.company.chatdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/**
 * App-specific settings from the {@code app.*} properties.
 *
 * @param storageDir    folder where uploaded files are saved
 * @param maxUploadSize largest file a user may upload
 * @param ingestion     how uploaded files are split into chunks
 */
@ConfigurationProperties("app")
public record AppProperties(
		@DefaultValue("./data/uploads") Path storageDir,
		@DefaultValue("20MB") DataSize maxUploadSize,
		@DefaultValue Ingestion ingestion) {

	/**
	 * @param chunkSize target chunk size in tokens
	 */
	public record Ingestion(@DefaultValue("500") int chunkSize) {
	}

}
