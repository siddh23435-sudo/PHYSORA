package edu.physics.ai.ollama;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Transport abstraction for Ollama HTTP communication.
 * Allows decoupling the network transport from OllamaProvider for isolated unit testing.
 */
public interface OllamaTransport extends AutoCloseable {

    record Response(int statusCode, String body) {
        public Response {
            if (body == null) body = "";
        }
    }

    /**
     * Performs a non-blocking HTTP GET to the specified relative path.
     */
    CompletableFuture<Response> get(String relativePath, Duration timeout);

    /**
     * Performs a non-blocking HTTP POST with a JSON body to the specified relative path.
     */
    CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout);

    @Override
    default void close() {}
}

