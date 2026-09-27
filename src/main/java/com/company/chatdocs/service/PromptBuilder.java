package com.company.chatdocs.service;

import com.company.chatdocs.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
			int index = i + 1;
			String fileName = String.valueOf(chunk.getMetadata().get("file_name"));
			Integer page = chunk.getMetadata().get("page") instanceof Number number ? number.intValue() : null;
			String content = String.valueOf(chunk.getText()).strip();

			text.append('[').append(index).append("] (").append(fileName);
			if (page != null) {
				text.append(", page ").append(page);
			}
			text.append(")\n").append(content).append("\n\n");

			citations.add(new Citation(index, UUID.fromString(String.valueOf(chunk.getMetadata().get("document_id"))),
					fileName, page, snippet(content)));
		}
		return new Context(text.toString().strip(), citations);
	}

	private static String snippet(String content) {
		String singleLine = content.replaceAll("\\s+", " ");
		return singleLine.length() <= SNIPPET_LENGTH ? singleLine : singleLine.substring(0, SNIPPET_LENGTH) + "…";
	}

}
