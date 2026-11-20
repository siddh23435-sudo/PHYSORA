package edu.physics.ai.ollama;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of {@link OllamaTransport} using Java 17's standard {@link HttpClient}.
 */
public final class HttpOllamaTransport implements OllamaTransport {
    private final String baseUrl;
    private final HttpClient httpClient;

    public HttpOllamaTransport(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public HttpOllamaTransport(String baseUrl, HttpClient httpClient) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL is required");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient is required");
        }
        String cleanUrl = baseUrl.trim();
        while (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        this.baseUrl = cleanUrl;
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Response> get(String relativePath, Duration timeout) {
        String fullUrl = baseUrl + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(timeout)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(res -> new Response(res.statusCode(), res.body()));
    }

    @Override
    public CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout) {
        String fullUrl = baseUrl + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(res -> new Response(res.statusCode(), res.body()));
    }

    @Override
    public void close() {
        // Java 17 HttpClient does not require explicit closing
    }
}

