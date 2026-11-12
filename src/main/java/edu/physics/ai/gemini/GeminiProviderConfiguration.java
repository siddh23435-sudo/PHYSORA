package edu.physics.ai.gemini;

import java.time.Duration;
import java.util.Map;

/** Non-secret Gemini configuration sourced from environment variables. */
public record GeminiProviderConfiguration(String model, Duration timeout, int maxAttempts,
                                          int maxQuestionCharacters, int maxOutputTokens) {
    public GeminiProviderConfiguration {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Gemini model is required");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
        if (maxAttempts < 1 || maxAttempts > 5) throw new IllegalArgumentException("Maximum attempts must be from 1 to 5");
        if (maxQuestionCharacters < 1 || maxQuestionCharacters > 100_000) throw new IllegalArgumentException("Question limit is out of range");
        if (maxOutputTokens < 128 || maxOutputTokens > 16_384) throw new IllegalArgumentException("Output token limit is out of range");
    }

    public static GeminiProviderConfiguration fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static GeminiProviderConfiguration fromEnvironment(Map<String, String> environment) {
        String provider = environment.getOrDefault("PHYSORA_AI_PROVIDER", "gemini");
        if (!"gemini".equalsIgnoreCase(provider)) throw new IllegalStateException("PHYSORA_AI_PROVIDER must be set to gemini");
        String model = environment.getOrDefault("PHYSORA_GEMINI_MODEL", "gemini-3.1-flash-lite");
        Duration timeout = Duration.ofSeconds(integer(environment, "PHYSORA_AI_TIMEOUT_SECONDS", 45));
        int attempts = integer(environment, "PHYSORA_AI_MAX_ATTEMPTS", 3);
        int maxQuestionCharacters = integer(environment, "PHYSORA_AI_MAX_QUESTION_CHARS", 12_000);
        int outputTokens = integer(environment, "PHYSORA_AI_MAX_OUTPUT_TOKENS", 3_000);
        return new GeminiProviderConfiguration(model, timeout, attempts, maxQuestionCharacters, outputTokens);
    }

    public static boolean apiKeyConfigured(Map<String, String> environment) {
        String key = environment.get("GOOGLE_API_KEY");
        return key != null && !key.isBlank();
    }

    private static int integer(Map<String, String> environment, String name, int fallback) {
        String raw = environment.get(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }
}
