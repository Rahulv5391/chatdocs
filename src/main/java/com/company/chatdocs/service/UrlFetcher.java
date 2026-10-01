package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import com.company.chatdocs.exception.UploadRejectedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads a document from a web address for {@link DocumentService#importUrl}.
 * <p>
 * The server makes the request, so it must not become a way into its own network: only http(s) is allowed, and
 * every address (including each redirect target) must resolve to a public IP. Redirects are followed by hand so
 * each hop is checked. Downloads are limited in size and time. (The address is resolved again when connecting, so
 * a DNS server that changes its answer in between could still slip through; acceptable for this POC.)
 */
@Component
public class UrlFetcher {

	private static final int MAX_REDIRECTS = 5;

	private static final int MAX_URL_LENGTH = 2000;

	private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

	/** A downloaded document: the final address (after redirects), its Content-Type header and its bytes. */
	public record Page(URI uri, String contentType, byte[] content) {
	}

	private final HttpClient client;
	private final Duration timeout;
	private final long maxBytes;
	private final boolean allowPrivateHosts;

	UrlFetcher(AppProperties properties) {
		this.timeout = properties.urlImport().timeout();
		this.maxBytes = properties.maxUploadSize().toBytes();
		this.allowPrivateHosts = properties.urlImport().allowPrivateHosts();
		this.client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(timeout)
				.build();
	}

	/** @throws UploadRejectedException with a user-friendly message if the address can't be used or downloaded */
	public Page fetch(String address) {
		URI uri = parse(address);
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			checkPublic(uri);
			HttpResponse<InputStream> response = send(uri);
			int status = response.statusCode();
			if (REDIRECTS.contains(status)) {
				close(response.body());
				String location = response.headers().firstValue("Location")
						.orElseThrow(() -> new UploadRejectedException("The page redirected without saying where to."));
				try {
					uri = parse(uri.resolve(location.strip()).toString());
				}
				catch (IllegalArgumentException e) {
					throw new UploadRejectedException("The page redirected to an invalid address.");
				}
				continue;
			}
			if (status < 200 || status > 299) {
				close(response.body());
				throw new UploadRejectedException("The page could not be downloaded (HTTP " + status + ").");
			}
			String contentType = response.headers().firstValue("Content-Type").orElse("");
			return new Page(uri, contentType, readLimited(response.body()));
		}
		throw new UploadRejectedException("The page redirected too many times.");
	}

	private static URI parse(String address) {
		String trimmed = address == null ? "" : address.strip();
		if (trimmed.isEmpty()) {
			throw new UploadRejectedException("Please enter a web address.");
		}
		if (trimmed.length() > MAX_URL_LENGTH) {
			throw new UploadRejectedException("That web address is too long.");
		}
		if (!trimmed.contains("://")) {
			trimmed = "https://" + trimmed;
		}
		URI uri;
		try {
			uri = URI.create(trimmed);
		}
		catch (IllegalArgumentException e) {
			throw new UploadRejectedException("That isn't a valid web address.");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
			throw new UploadRejectedException("Only http:// and https:// web addresses are supported.");
		}
		return uri;
	}

	private void checkPublic(URI uri) {
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(uri.getHost());
		}
		catch (UnknownHostException e) {
			throw new UploadRejectedException("Could not find the website " + uri.getHost() + ".");
		}
		if (allowPrivateHosts) {
			return;
		}
		for (InetAddress address : addresses) {
			if (isPrivate(address)) {
				throw new UploadRejectedException("That address points to a private network and can't be imported.");
			}
		}
	}

	static boolean isPrivate(InetAddress address) {
		byte[] b = address.getAddress();
		return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()
				// 0.0.0.0/8 and carrier-grade NAT 100.64.0.0/10
				|| b.length == 4 && (b[0] == 0 || b[0] == 100 && (b[1] & 0xC0) == 64)
				// IPv6 unique local fc00::/7
				|| b.length == 16 && (b[0] & 0xFE) == 0xFC;
	}

	private HttpResponse<InputStream> send(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(timeout)
				.header("User-Agent", "ChatDocs/1.0 (document import)")
				.header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain;q=0.9,*/*;q=0.5")
				.GET()
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		}
		catch (IOException e) {
			throw new UploadRejectedException("Could not download the page from " + uri.getHost() + ".");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UploadRejectedException("The download was interrupted.");
		}
	}

	private byte[] readLimited(InputStream body) {
		try (body) {
			byte[] content = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
			if (content.length > maxBytes) {
				throw new UploadRejectedException(
						"The page is too large. The limit is " + maxBytes / (1024 * 1024) + " MB.");
			}
			return content;
		}
		catch (IOException e) {
			throw new UploadRejectedException("The download was cut off. Please try again.");
		}
	}

	private static void close(InputStream body) {
		try {
			body.close();
		}
		catch (IOException e) {
			// Nothing to do: the response is discarded anyway.
		}
	}

}
