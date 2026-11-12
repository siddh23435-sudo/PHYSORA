package edu.physics.ai.ollama;

import java.time.Duration;
import java.util.Map;

/**
 * Non-secret configuration for the optional local Ollama provider.
 */
public record OllamaConfiguration(String baseUrl, String model, Duration probeTimeout, Duration requestTimeout) {
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:11434";
    public static final String DEFAULT_MODEL = "qwen-physics-test";
    public static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofMillis(500);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(45);

    public OllamaConfiguration {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("Ollama baseUrl is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Ollama model is required");
        if (probeTimeout == null || probeTimeout.isNegative() || probeTimeout.isZero()) {
            throw new IllegalArgumentException("probeTimeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    public static OllamaConfiguration defaultConfiguration() {
        return new OllamaConfiguration(DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_PROBE_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public static OllamaConfiguration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static OllamaConfiguration fromEnvironment(Map<String, String> environment) {
        String baseUrl = environment.getOrDefault("PHYSORA_OLLAMA_URL", DEFAULT_BASE_URL).trim();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String model = environment.getOrDefault("PHYSORA_OLLAMA_MODEL", DEFAULT_MODEL).trim();
        long timeoutSec = integer(environment, "PHYSORA_OLLAMA_TIMEOUT_SECONDS", 45);
        return new OllamaConfiguration(baseUrl, model, DEFAULT_PROBE_TIMEOUT, Duration.ofSeconds(timeoutSec));
    }

    private static long integer(Map<String, String> env, String key, long fallback) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

