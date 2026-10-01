package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.exception.UploadRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import java.net.InetAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The server must not fetch addresses inside its own network for users (SSRF). No Spring context needed. */
class UrlFetcherTests {

	private final UrlFetcher fetcher = new UrlFetcher(new AppProperties(DataSize.ofMegabytes(20),
			new AppProperties.Ingestion(500, 200, 20, Duration.ZERO, 20, true, 60_000),
			new AppProperties.Rag(5, 0.55, 6, 2000),
			new AppProperties.UrlImport(Duration.ofSeconds(5), false)));

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:8080/actuator/health", "http://127.0.0.1/", "http://10.1.2.3/",
			"http://192.168.0.10/", "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://0.0.0.0/" })
	void blocksPrivateAndLocalAddresses(String url) {
		assertThatThrownBy(() -> fetcher.fetch(url))
				.isInstanceOf(UploadRejectedException.class)
				.hasMessage("That address points to a private network and can't be imported.");
	}

	@Test
	void classifiesAddresses() throws Exception {
		assertThat(UrlFetcher.isPrivate(InetAddress.getByName("100.64.1.1"))).as("carrier-grade NAT").isTrue();
		assertThat(UrlFetcher.isPrivate(InetAddress.getByName("fd00::1"))).as("IPv6 unique local").isTrue();
		assertThat(UrlFetcher.isPrivate(InetAddress.getByName("172.16.5.4"))).isTrue();
		assertThat(UrlFetcher.isPrivate(InetAddress.getByName("8.8.8.8"))).isFalse();
		assertThat(UrlFetcher.isPrivate(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
	}

}
