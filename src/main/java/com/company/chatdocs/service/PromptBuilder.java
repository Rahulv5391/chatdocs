package com.company.chatdocs.service;

import com.company.chatdocs.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Numbers the retrieved chunks [1]..[n] for the prompt, and keeps a matching {@link Citation} for each,
 * so the [n] markers in the answer can be traced back to a file and page.
 */
@Component
public class PromptBuilder {

	private static final int SNIPPET_LENGTH = 300;

	public record Context(String text, List<Citation> citations) {
	}

	public Context build(List<Document> chunks) {
		StringBuilder text = new StringBuilder();
		List<Citation> citations = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			Document chunk = chunks.get(i);
			Map<String, Object> metadata = chunk.getMetadata();
			int index = i + 1;
			UUID documentId = UUID.fromString(String.valueOf(metadata.get(ChunkMetadata.DOCUMENT_ID)));
			String fileName = String.valueOf(metadata.get(ChunkMetadata.FILE_NAME));
			Integer page = metadata.get(ChunkMetadata.PAGE) instanceof Number number ? number.intValue() : null;
			String content = String.valueOf(chunk.getText()).strip();

			text.append('[').append(index).append("] (").append(fileName);
			if (page != null) {
				text.append(", page ").append(page);
			}
			text.append(")\n").append(content).append("\n\n");

			citations.add(new Citation(index, documentId, fileName, page,
					TextUtils.abbreviate(content, SNIPPET_LENGTH)));
		}
		return new Context(text.toString().strip(), citations);
	}

}
