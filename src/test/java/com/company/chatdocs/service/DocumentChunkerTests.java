package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring context, no database. */
class DocumentChunkerTests {

	private final DocumentChunker chunker = new DocumentChunker(new AppProperties(DataSize.ofMegabytes(20),
			new AppProperties.Ingestion(60, 80, 20, Duration.ZERO, 20, true, 60_000),
			new AppProperties.Rag(5, 0.55, 6, 2000),
			new AppProperties.UrlImport(Duration.ofSeconds(20), false)));

	@Test
	void recognizesCommonHeadingStyles() {
		assertThat(heading("## Leave Policy")).isEqualTo("Leave Policy");
		assertThat(heading("3.2 Annual Leave")).isEqualTo("3.2 Annual Leave");
		assertThat(heading("LEAVE POLICY")).isEqualTo("LEAVE POLICY");
		assertThat(heading("Leave and Travel Policy")).isEqualTo("Leave and Travel Policy");
		assertThat(heading("Introduction")).isEqualTo("Introduction");
	}

	@Test
	void recognizesHeadingsInPdfLayoutText() {
		// Real output of the PDF reader: words padded with spaces, heading at the margin, body indented below it.
		List<String> page = List.of(
				"Travel   policy",
				"            Economy      class   is required    for flights  shorter   than   6 hours.",
				"            Hotel   bookings     must   be  made     through    the  company      travel  agent");

		assertThat(DocumentChunker.heading(page, 0)).isEqualTo("Travel policy");
		assertThat(DocumentChunker.heading(page, 2)).as("indented body line without a period").isNull();
	}

	@Test
	void ignoresSentencesTableRowsAndShortWords() {
		assertThat(heading("Employees get 24 days of leave.")).isNull();
		assertThat(heading("Every employee gets paid annual leave")).isNull();
		assertThat(heading("Name          Department          Salary")).isNull();
		assertThat(heading("Yes")).isNull();
		assertThat(heading("12.5")).isNull();
	}

	@Test
	void titleCaseLineCountsOnlyWhenItStandsAlone() {
		// In the middle of a paragraph, "Human Resources" is a wrapped line, not a heading.
		assertThat(DocumentChunker.heading(List.of("Questions go to the team in", "Human Resources"), 1)).isNull();
		assertThat(DocumentChunker.heading(List.of("", "Human Resources"), 1)).isEqualTo("Human Resources");
	}

	@Test
	void sectionCarriesOverToTheNextPage() {
		List<Document> chunks = chunker.split(List.of(
				page(1, "# Leave Policy\n\nEmployees get 24 days of paid annual leave."),
				page(2, "Unused days can be carried over to the next year.")));

		assertThat(chunks).hasSize(2);
		assertThat(chunks).allSatisfy(chunk ->
				assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.SECTION, "Leave Policy"));
		// The first chunk already starts with its heading; the second gets it added for the embedding.
		assertThat(chunks.get(0).getText()).startsWith("# Leave Policy");
		assertThat(chunks.get(1).getText()).startsWith("Leave Policy\n\nUnused days");
		assertThat(chunks.get(1).getMetadata()).containsEntry("page_number", 2);
	}

	@Test
	void textWithoutHeadingsHasNoSection() {
		List<Document> chunks = chunker.split(List.of(page(1, "just some notes without any structure at all.")));

		assertThat(chunks).singleElement().satisfies(chunk -> {
			assertThat(chunk.getMetadata()).doesNotContainKey(ChunkMetadata.SECTION);
			assertThat(chunk.getText()).isEqualTo("just some notes without any structure at all.");
		});
	}

	@Test
	void nextChunkRepeatsTheEndOfThePreviousOne() {
		String text = IntStream.rangeClosed(1, 40).mapToObj(n -> "Sentence " + n + " is here.")
				.collect(Collectors.joining(" "));

		List<Document> chunks = chunker.split(List.of(page(1, text)));

		assertThat(chunks).hasSizeGreaterThan(1);
		for (int i = 1; i < chunks.size(); i++) {
			String previous = chunks.get(i - 1).getText();
			String firstSentence = chunks.get(i).getText().substring(0, chunks.get(i).getText().indexOf('.') + 1);
			assertThat(previous).as("chunk %d starts with text from chunk %d", i, i - 1).contains(firstSentence);
		}
	}

	@Test
	void overlapStartsAtASentenceOrWordBoundary() {
		assertThat(DocumentChunker.tail("One two three. Four five six seven", 25)).isEqualTo("Four five six seven");
		assertThat(DocumentChunker.tail("alpha beta gamma delta epsilon", 14)).isEqualTo("delta epsilon");
		assertThat(DocumentChunker.tail("short", 50)).isEqualTo("short");
	}

	private static String heading(String line) {
		return DocumentChunker.heading(List.of(line), 0);
	}

	private static Document page(int number, String text) {
		return new Document(text, Map.of("page_number", number));
	}

}
