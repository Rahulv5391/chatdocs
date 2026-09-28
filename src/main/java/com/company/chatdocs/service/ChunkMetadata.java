package com.company.chatdocs.service;

/**
 * Metadata keys stored with every chunk in the vector store. Ingestion writes them, retrieval filters on
 * {@link #USER_ID} and {@link #DOCUMENT_ID}, and citations show {@link #FILE_NAME} and {@link #PAGE}.
 */
public final class ChunkMetadata {

	public static final String DOCUMENT_ID = "document_id";

	public static final String USER_ID = "user_id";

	public static final String FILE_NAME = "file_name";

	public static final String PAGE = "page";

	public static final String CHUNK_INDEX = "chunk_index";

	private ChunkMetadata() {
	}

}
