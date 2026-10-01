package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.exception.AiServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Picks the right Spring AI reader for an uploaded file. PDFs are read page by page so each chunk keeps its page
 * number (metadata key {@code page_number}); every other type goes through Apache Tika.
 * <p>
 * PDF pages without a text layer (scans) that contain an image are rendered and read by {@link OcrService}.
 */
@Component
public class DocumentReaderFactory {

	private static final Logger log = LoggerFactory.getLogger(DocumentReaderFactory.class);

	/** A page with less text than this (e.g. only a page number) is treated as having no text layer. */
	private static final int MIN_PAGE_TEXT_CHARS = 20;

	/** Enough for Gemini to read normal-sized print, while keeping the image small. */
	private static final int OCR_DPI = 150;

	/** Parts of a web page that aren't its content (the class names are common MediaWiki/CSS conventions). */
	private static final String HTML_BOILERPLATE = "script, style, noscript, template, svg, nav, header, footer, "
			+ "aside, form, [role=navigation], [role=banner], [role=contentinfo], [role=search], [aria-hidden=true], "
			+ ".navbox, .reflist, .references, .mw-editsection, .noprint, .sr-only, .visually-hidden";

	private final OcrService ocr;
	private final int maxOcrPages;

	DocumentReaderFactory(OcrService ocr, AppProperties properties) {
		this.ocr = ocr;
		this.maxOcrPages = properties.ingestion().maxOcrPages();
	}

	public List<Document> read(com.company.chatdocs.entity.Document document, byte[] content) {
		if ("text/html".equals(document.getContentType())) {
			content = mainContent(content);
		}
		Resource resource = new NamedByteArrayResource(content, document.getFileName());
		if (!"application/pdf".equals(document.getContentType())) {
			return new TikaDocumentReader(resource).get();
		}
		List<Document> textPages = new PagePdfDocumentReader(resource).get();
		return withScannedPages(document.getFileName(), content, textPages);
	}

	/** Adds the OCR text of every scanned page to the pages that have a text layer, in page order. */
	private List<Document> withScannedPages(String fileName, byte[] content, List<Document> textPages) {
		Map<Integer, Document> pages = new TreeMap<>();
		for (Document page : textPages) {
			if (page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER) instanceof Integer number) {
				pages.put(number, page);
			}
		}
		try (PDDocument pdf = Loader.loadPDF(content)) {
			List<Integer> scanned = new ArrayList<>();
			for (int number = 1; number <= pdf.getNumberOfPages(); number++) {
				Document page = pages.get(number);
				if (!hasText(page) && hasImage(pdf.getPage(number - 1).getResources(), 0)) {
					scanned.add(number);
				}
			}
			if (scanned.isEmpty()) {
				return textPages;
			}
			if (scanned.size() > maxOcrPages) {
				throw new IngestionException("This PDF has " + scanned.size()
						+ " scanned pages without a text layer. At most " + maxOcrPages + " can be read per document.");
			}
			PDFRenderer renderer = new PDFRenderer(pdf);
			for (int number : scanned) {
				String text = readScannedPage(renderer, number);
				if (!text.isBlank()) {
					pages.put(number, new Document(text,
							Map.of(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, number)));
				}
			}
			log.info("Read {} scanned page(s) of '{}' with OCR", scanned.size(), fileName);
			return List.copyOf(pages.values());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private String readScannedPage(PDFRenderer renderer, int number) throws IOException {
		BufferedImage image = renderer.renderImageWithDPI(number - 1, OCR_DPI, ImageType.GRAY);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		try {
			return ocr.readPage(png.toByteArray());
		}
		catch (RuntimeException e) {
			if (AiServiceException.isRateLimited(e)) {
				throw new IngestionException("Gemini quota exceeded while reading scanned pages. Try again later.", e);
			}
			throw e;
		}
	}

	/**
	 * Keeps only the main content of a web page: menus, headers, footers, sidebars and scripts would otherwise be
	 * indexed as chunks and compete with the real text in retrieval. Uses the page's {@code <main>} or
	 * {@code <article>} when it has one. (jsoup comes with Vaadin's flow-server, so it adds no dependency.)
	 */
	static byte[] mainContent(byte[] html) {
		org.jsoup.nodes.Document page;
		try {
			page = Jsoup.parse(new ByteArrayInputStream(html), null, "");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		page.select(HTML_BOILERPLATE).remove();
		Element main = page.selectFirst("main, article, [role=main]");
		Element root = main != null ? main : page.body();
		return ("<html><body>" + root.html() + "</body></html>").getBytes(StandardCharsets.UTF_8);
	}

	private static boolean hasText(Document page) {
		return page != null && page.getText() != null
				&& page.getText().replaceAll("\\s", "").length() >= MIN_PAGE_TEXT_CHARS;
	}

	/** True if the page draws an image, directly or inside a form XObject. Blank pages aren't sent to OCR. */
	private static boolean hasImage(PDResources resources, int depth) throws IOException {
		if (resources == null || depth > 3) {
			return false;
		}
		for (COSName name : resources.getXObjectNames()) {
			PDXObject xObject = resources.getXObject(name);
			if (xObject instanceof PDImageXObject) {
				return true;
			}
			if (xObject instanceof PDFormXObject form && hasImage(form.getResources(), depth + 1)) {
				return true;
			}
		}
		return false;
	}

	/** The readers put the file name into their metadata, which must not be null. */
	private static class NamedByteArrayResource extends ByteArrayResource {

		private final String fileName;

		NamedByteArrayResource(byte[] content, String fileName) {
			super(content);
			this.fileName = fileName;
		}

		@Override
		public String getFilename() {
			return fileName;
		}

	}

}
