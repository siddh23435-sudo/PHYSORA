package edu.physics.ai;

import java.util.Locale;

/**
 * Operating modes governing provider routing and fallback behavior.
 */
public enum ProviderMode {
    /** Gemini (when configured) -> Ollama (only when enabled) -> LocalAIProvider. */
    AUTO,
    /** Gemini only; never falls back silently to Ollama or LocalAIProvider. */
    CLOUD_ONLY,
    /** Ollama (when enabled) -> LocalAIProvider; never contacts Gemini. */
    LOCAL_AI,
    /** Deterministic rule-based LocalAIProvider only; makes zero network calls. */
    OFFLINE;

    public static ProviderMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "AUTO" -> AUTO;
            case "CLOUD_ONLY", "CLOUD", "GEMINI" -> CLOUD_ONLY;
            case "LOCAL_AI", "LOCAL_LLM", "OLLAMA" -> LOCAL_AI;
            case "OFFLINE", "LOCAL", "RULE_BASED" -> OFFLINE;
            default -> throw new IllegalArgumentException("Unsupported provider mode: " + raw);
        };
    }
}

