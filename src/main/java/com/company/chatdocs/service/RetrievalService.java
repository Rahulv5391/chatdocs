package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * RAG step 2 (retrieval): finds the chunks most similar to a question.
 * <p>
 * Every search is filtered by the caller's user id here, in the service layer, so no caller can forget it:
 * a user can never retrieve another user's chunks, whatever the question or document scope.
 */
@Service
public class RetrievalService {

	private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

	private final VectorStore vectorStore;
	private final AppProperties.Rag rag;

	RetrievalService(VectorStore vectorStore, AppProperties properties) {
		this.vectorStore = vectorStore;
		this.rag = properties.rag();
	}

	/**
	 * @param userId   whose chunks may be returned; always applied
	 * @param query    the user's question
	 * @param docScope document ids to search in; empty means all of the user's documents
	 * @return up to {@code app.rag.top-k} chunks above {@code app.rag.similarity-threshold}, most similar first,
	 *         each with its metadata (document_id, file_name, page, chunk_index) and score
	 */
	public List<Document> search(UUID userId, String query, Collection<UUID> docScope) {
		var filter = new FilterExpressionBuilder();
		var ownChunks = filter.eq("user_id", userId.toString());
		var expression = docScope.isEmpty()
				? ownChunks
				: filter.and(ownChunks, filter.in("document_id", docScope.stream().map(id -> (Object) id.toString()).toList()));

		List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.topK(rag.topK())
				.similarityThreshold(rag.similarityThreshold())
				.filterExpression(expression.build())
				.build());

		log.debug("Retrieved {} chunk(s) for user {} (scope: {})", results.size(), userId,
				docScope.isEmpty() ? "all" : docScope.size() + " doc(s)");
		return results;
	}

}
