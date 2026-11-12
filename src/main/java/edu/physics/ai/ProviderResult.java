package edu.physics.ai;

import java.util.List;
import java.util.Locale;

/** Provider output and non-secret request diagnostics shared across adapters. */
public record ProviderResult<T>(T content,
                                String requestId,
                                String model,
                                Usage usage,
                                ProviderAttribution attribution) {
    public ProviderResult {
        if (content == null) throw new IllegalArgumentException("Provider content is required");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("Request identifier is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Model identifier is required");
        if (attribution == null) {
            attribution = new ProviderAttribution(inferProvider(model), model, false, null, null, List.of());
        }
    }

    public ProviderResult(T content, String requestId, String model, Usage usage) {
        this(content, requestId, model, usage, null);
    }

    public ProviderResult<T> withAttribution(ProviderAttribution updatedAttribution) {
        if (updatedAttribution == null) throw new IllegalArgumentException("Provider attribution is required");
        return new ProviderResult<>(content, requestId, model, usage, updatedAttribution);
    }

    public enum ProviderId {
        GEMINI,
        OLLAMA,
        LOCAL_RULE_ENGINE,
        UNKNOWN
    }

    public record FallbackHop(ProviderId provider,
                              AIProviderException.Kind failureKind,
                              boolean retryableOnSameProvider,
                              String reason) {
        public FallbackHop {
            if (provider == null) throw new IllegalArgumentException("Provider identifier is required");
            if (failureKind == null) throw new IllegalArgumentException("Failure kind is required");
        }
    }

    public record ProviderAttribution(ProviderId provider,
                                      String model,
                                      boolean fallbackUsed,
                                      ProviderId previousProvider,
                                      String fallbackReason,
                                      List<FallbackHop> fallbackTrail) {
        public ProviderAttribution {
            if (provider == null) throw new IllegalArgumentException("Provider identifier is required");
            if (model == null || model.isBlank()) throw new IllegalArgumentException("Model identifier is required");
            fallbackTrail = fallbackTrail == null ? List.of() : List.copyOf(fallbackTrail);
        }

        public ProviderAttribution(ProviderId provider,
                                   String model,
                                   boolean fallbackUsed,
                                   ProviderId previousProvider,
                                   String fallbackReason) {
            this(provider, model, fallbackUsed, previousProvider, fallbackReason, List.of());
        }
    }

    public record Usage(long inputTokens, long outputTokens) {
        public Usage {
            if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Token counts cannot be negative");
        }
    }

    private static ProviderId inferProvider(String model) {
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("gemini")) return ProviderId.GEMINI;
        if ("local-physics-engine".equals(normalized)) return ProviderId.LOCAL_RULE_ENGINE;
        if (normalized.contains("qwen") || normalized.contains("ollama")) return ProviderId.OLLAMA;
        return ProviderId.UNKNOWN;
    }
}
