package com.company.chatdocs;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces Gemini embeddings in tests, so the normal test run is fast, works offline and uses no quota.
 * Each word is hashed into one of 768 slots, so texts that share words get similar vectors.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingModelConfiguration {

	public static final int DIMENSIONS = 768;

	@Bean
	@Primary
	FakeEmbeddingModel fakeEmbeddingModel() {
		return new FakeEmbeddingModel();
	}

	public static class FakeEmbeddingModel implements EmbeddingModel {

		/** How many embedding calls were made, one per batch. */
		public final AtomicInteger calls = new AtomicInteger();

		/** If set, every embedding call fails with this (e.g. to simulate a Gemini quota error). */
		public volatile RuntimeException failure;

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			calls.incrementAndGet();
			if (failure != null) {
				throw failure;
			}
			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				embeddings.add(new Embedding(vector(request.getInstructions().get(i)), i));
			}
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			if (failure != null) {
				throw failure;
			}
			return vector(document.getText());
		}

		@Override
		public int dimensions() {
			return DIMENSIONS;
		}

		static float[] vector(String text) {
			float[] vector = new float[DIMENSIONS];
			for (String word : text.toLowerCase(Locale.ROOT).split("\\W+")) {
				if (!word.isEmpty()) {
					vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
				}
			}
			double norm = 0;
			for (float v : vector) {
				norm += v * v;
			}
			if (norm == 0) {
				vector[0] = 1;
				return vector;
			}
			for (int i = 0; i < DIMENSIONS; i++) {
				vector[i] /= (float) Math.sqrt(norm);
			}
			return vector;
		}

	}

}
