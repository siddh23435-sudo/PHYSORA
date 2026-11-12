package edu.physics.ai.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.ProviderResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Google GenAI Java SDK implementation; no JavaFX types or physics logic are used here. */
public final class GoogleGenAiTransport implements GeminiTransport {
    private final Client client;
    private final ExecutorService executor;

    public GoogleGenAiTransport(GeminiProviderConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("Gemini configuration is required");
        int timeoutMillis = Math.toIntExact(configuration.timeout().toMillis());
        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(timeoutMillis)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(configuration.maxAttempts())
                        .httpStatusCodes(408, 429, 500, 502, 503, 504)
                        .build())
                .build();
        client = Client.builder().httpOptions(httpOptions).build();
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "physora-gemini-transport");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletionStage<Response> generateJson(String model, String systemInstruction, String userPrompt,
                                                  Map<String, Object> jsonSchema, int maxOutputTokens) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .responseMimeType("application/json")
                .candidateCount(1)
                .maxOutputTokens(maxOutputTokens)
                .responseJsonSchema(jsonSchema)
                .build();
        long start = System.currentTimeMillis();
        System.out.println("[PHYSORA AI] Sending request to Gemini (" + model + ")...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                com.google.genai.types.GenerateContentResponse response =
                        client.models.generateContent(model, userPrompt, config);
                long duration = System.currentTimeMillis() - start;
                System.out.println("[PHYSORA AI] Response received from Gemini in " + duration + " ms");
                String text = response.text();
                if (text == null || text.isBlank()) {
                    throw new AIProviderException(AIProviderException.Kind.INVALID_RESPONSE, false,
                            "Gemini returned an empty structured response");
                }
                ProviderResult.Usage usage = response.usageMetadata().map(metadata -> {
                    long inputTokens = metadata.promptTokenCount().map(Number::longValue).orElse(0L);
                    long outputTokens = metadata.candidatesTokenCount().map(Number::longValue).orElse(0L);
                    return new ProviderResult.Usage(inputTokens, outputTokens);
                }).orElse(null);
                return new Response(text, response.responseId().orElse(null),
                        response.modelVersion().orElse(model), usage);
            } catch (Throwable failure) {
                long duration = System.currentTimeMillis() - start;
                System.err.println("[PHYSORA Transport] Call failed after " + duration + " ms: " + failure);
                if (failure instanceof RuntimeException runtimeException) throw runtimeException;
                throw new CompletionException(failure);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        client.close();
    }
}
