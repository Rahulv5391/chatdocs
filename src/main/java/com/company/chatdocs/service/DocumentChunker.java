package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a document's pages into chunks for embedding. On top of {@link TokenTextSplitter} it:
 * <ul>
 * <li>repeats the end of the previous chunk of the same page at the start of the next one ({@code chunk-overlap}),
 * so a sentence cut at a chunk boundary is still found whole;</li>
 * <li>labels each chunk with the heading of the section it belongs to ({@link ChunkMetadata#SECTION}), carried
 * across pages, and puts that heading on the chunk's first line. Gemini embeds only the chunk text, so this is
 * how the embedding learns which section a chunk is about.</li>
 * </ul>
 * Headings are found with simple rules (Markdown {@code #}, numbered, ALL CAPS, or a short capitalized line that
 * starts a block), which work for the plain text that the PDF and Tika readers produce.
 */
@Component
public class DocumentChunker {

	private static final int MAX_HEADING_LENGTH = 80;

	private static final int MAX_HEADING_WORDS = 10;

	/** Headings like "Travel policy" are short; longer lines without a capital on every word are sentences. */
	private static final int MAX_SENTENCE_CASE_WORDS = 5;

	private static final Pattern TABLE_GAP = Pattern.compile("\\S\\s{8,}\\S");

	private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*$");

	private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(\\.\\d+)*\\.?|[IVX]+\\.)\\s+\\p{Lu}.*");

	/** Words that stay lower case in a Title Case heading. */
	private static final Set<String> MINOR_WORDS = Set.of("a", "an", "and", "as", "at", "by", "for", "in", "of",
			"on", "or", "the", "to", "with", "&", "-", "–");

	private final TokenTextSplitter splitter;
	private final int overlap;

	DocumentChunker(AppProperties properties) {
		this.splitter = TokenTextSplitter.builder().withChunkSize(properties.ingestion().chunkSize()).build();
		this.overlap = properties.ingestion().chunkOverlap();
	}

	/**
	 * @param pages the document's text, page by page, in order
	 * @return the chunks in order, each with its page's metadata plus {@link ChunkMetadata#SECTION} when known
	 */
	public List<Document> split(List<Document> pages) {
		List<Document> chunks = new ArrayList<>();
		String section = null;
		for (Document page : pages) {
			if (page.getText() == null || page.getText().isBlank()) {
				continue;
			}
			String previous = null;
			for (Document piece : splitter.split(page)) {
				String text = piece.getText();
				if (text == null || text.isBlank()) {
					continue;
				}
				List<String> lines = text.lines().toList();
				String label = sectionOf(lines, section);
				section = lastHeading(lines, section);

				StringBuilder chunk = new StringBuilder();
				if (label != null && !label.equals(firstLineHeading(lines))) {
					chunk.append(label).append("\n\n");
				}
				if (previous != null && overlap > 0) {
					chunk.append(tail(previous, overlap)).append(' ');
				}
				chunk.append(text);

				Map<String, Object> metadata = new HashMap<>(piece.getMetadata());
				if (label != null) {
					metadata.put(ChunkMetadata.SECTION, label);
				}
				chunks.add(new Document(chunk.toString(), metadata));
				previous = text;
			}
		}
		return chunks;
	}

	/**
	 * The chunk's section: the heading it starts with; otherwise the section in effect before it (carried from
	 * earlier chunks and pages); otherwise the first heading inside it.
	 */
	private static String sectionOf(List<String> lines, String current) {
		String first = firstLineHeading(lines);
		if (first != null) {
			return first;
		}
		if (current != null) {
			return current;
		}
		for (int i = 0; i < lines.size(); i++) {
			String heading = heading(lines, i);
			if (heading != null) {
				return heading;
			}
		}
		return null;
	}

	/** The section in effect after this chunk, for the chunks that follow. */
	private static String lastHeading(List<String> lines, String current) {
		String last = current;
		for (int i = 0; i < lines.size(); i++) {
			String heading = heading(lines, i);
			if (heading != null) {
				last = heading;
			}
		}
		return last;
	}

	private static String firstLineHeading(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).isBlank()) {
				return heading(lines, i);
			}
		}
		return null;
	}

	/** The heading text if line {@code i} looks like a heading, otherwise null. */
	static String heading(List<String> lines, int i) {
		String raw = lines.get(i).strip();
		Matcher markdown = MARKDOWN_HEADING.matcher(raw);
		if (markdown.matches()) {
			return markdown.group(1).strip();
		}
		// PDF layout text pads words with a few spaces; very wide gaps are table columns, not headings.
		String line = raw.replaceAll("\\s+", " ");
		if (line.length() < 3 || line.length() > MAX_HEADING_LENGTH || TABLE_GAP.matcher(raw).find()
				|| line.chars().noneMatch(Character::isLetter) || ".,;:".indexOf(line.charAt(line.length() - 1)) >= 0) {
			return null;
		}
		String[] words = line.split(" ");
		if (words.length > MAX_HEADING_WORDS) {
			return null;
		}
		if (NUMBERED_HEADING.matcher(line).matches() || isAllCaps(line)) {
			return line;
		}
		return isStandalone(lines, i) && (isTitleCase(words) || isShortSentenceCase(words)) ? line : null;
	}

	/**
	 * A line that starts a block: the first line, a line after a blank one, or (in PDF layout text, where there are
	 * no blank lines) a line less indented than the text below it.
	 */
	private static boolean isStandalone(List<String> lines, int i) {
		if (i == 0 || lines.get(i - 1).isBlank()) {
			return true;
		}
		for (int next = i + 1; next < lines.size(); next++) {
			if (!lines.get(next).isBlank()) {
				return indent(lines.get(i)) < indent(lines.get(next));
			}
		}
		return false;
	}

	private static int indent(String line) {
		int count = 0;
		while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
			count++;
		}
		return count;
	}

	/** "Travel policy": a short line starting with a capital letter. */
	private static boolean isShortSentenceCase(String[] words) {
		return words.length <= MAX_SENTENCE_CASE_WORDS && Character.isUpperCase(words[0].codePointAt(0))
				&& (words.length > 1 || words[0].length() >= 5);
	}

	private static boolean isAllCaps(String line) {
		long letters = line.chars().filter(Character::isLetter).count();
		return letters >= 3 && line.chars().filter(Character::isLetter).allMatch(Character::isUpperCase);
	}

	private static boolean isTitleCase(String[] words) {
		if (!Character.isUpperCase(words[0].codePointAt(0))) {
			return false;
		}
		for (String word : words) {
			if (!MINOR_WORDS.contains(word.toLowerCase()) && !Character.isUpperCase(word.codePointAt(0))
					&& !Character.isDigit(word.codePointAt(0))) {
				return false;
			}
		}
		// A single capitalized word is a heading only if it isn't a short word like "Yes" or "Name".
		return words.length > 1 || words[0].length() >= 5;
	}

	/** About the last {@code length} characters of the text, starting at a sentence or word boundary. */
	static String tail(String text, int length) {
		if (text.length() <= length) {
			return text.strip();
		}
		String tail = text.substring(text.length() - length);
		Matcher sentence = Pattern.compile("[.!?]\\s+").matcher(tail);
		if (sentence.find() && sentence.end() < tail.length() / 2) {
			return tail.substring(sentence.end()).strip();
		}
		int space = tail.indexOf(' ');
		return (space < 0 ? tail : tail.substring(space + 1)).strip();
	}

}
