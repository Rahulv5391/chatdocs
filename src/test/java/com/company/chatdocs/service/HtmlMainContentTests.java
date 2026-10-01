package com.company.chatdocs.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests for {@link DocumentReaderFactory#mainContent}: no Spring context. */
class HtmlMainContentTests {

	@Test
	void keepsOnlyTheMainContent() {
		String html = """
				<html><head><title>Guide</title><script>var tracking = 1;</script></head><body>
				<header>Site logo</header><nav><a href="/">Main menu</a></nav>
				<main><h1>Leave policy</h1><p>Employees get 24 days.</p>
				  <span class="mw-editsection">[edit]</span><div class="navbox">Related links</div></main>
				<aside>Popular posts</aside><footer>Copyright</footer>
				</body></html>""";

		String content = clean(html);

		assertThat(content).contains("Leave policy", "Employees get 24 days.")
				.doesNotContain("tracking", "Site logo", "Main menu", "[edit]", "Related links", "Popular posts",
						"Copyright");
	}

	@Test
	void pageWithoutMainKeepsTheBodyWithoutBoilerplate() {
		String content = clean("<html><body><nav>Menu</nav><p>Office opens at 9 am.</p></body></html>");

		assertThat(content).contains("Office opens at 9 am.").doesNotContain("Menu");
	}

	private static String clean(String html) {
		return new String(DocumentReaderFactory.mainContent(html.getBytes(StandardCharsets.UTF_8)),
				StandardCharsets.UTF_8);
	}

}
