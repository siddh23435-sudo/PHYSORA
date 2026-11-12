package edu.physics.ai.gemini;

import edu.physics.ai.ProviderResult;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Narrow adapter seam around the Google SDK for deterministic transport tests. */
public interface GeminiTransport extends AutoCloseable {
    CompletionStage<Response> generateJson(String model, String systemInstruction, String userPrompt,
                                           Map<String, Object> jsonSchema, int maxOutputTokens);

    record Response(String text, String requestId, String model, ProviderResult.Usage usage) {}

    @Override
    default void close() {}
}
