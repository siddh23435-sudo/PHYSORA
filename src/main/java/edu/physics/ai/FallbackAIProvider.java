package edu.physics.ai;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decorator that tries a primary provider (e.g. Gemini) and transparently falls back
 * to a local provider if the primary provider hits quota rate limits (HTTP 429), network outages,
 * or authentication problems.
 */
public final class FallbackAIProvider implements AIProvider {
    private final AIProvider primary;
    private final AIProvider fallback;

    public FallbackAIProvider(AIProvider primary, AIProvider fallback) {
        if (primary == null || fallback == null) {
            throw new IllegalArgumentException("Primary and fallback providers are required");
        }
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
        return primary.interpret(request).handle((result, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(result);
            if (shouldFallback(failure)) {
                System.out.println("[PHYSORA AI] Primary provider unavailable (" + failure.getMessage()
                        + "). Engaging local fallback provider for interpretation...");
                return fallback.interpret(request);
            }
            return CompletableFuture.<ProviderResult<InterpretationDraft>>failedFuture(failure);
        }).thenCompose(stage -> stage);
    }

    @Override
    public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
        return primary.explain(request).handle((result, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(result);
            if (shouldFallback(failure)) {
                System.out.println("[PHYSORA AI] Primary provider unavailable (" + failure.getMessage()
                        + "). Engaging local fallback provider for explanation...");
                return fallback.explain(request);
            }
            return CompletableFuture.<ProviderResult<ExplanationDraft>>failedFuture(failure);
        }).thenCompose(stage -> stage);
    }

    private static boolean shouldFallback(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AIProviderException providerEx) {
            return providerEx.kind() == AIProviderException.Kind.RATE_LIMITED
                    || providerEx.kind() == AIProviderException.Kind.NETWORK
                    || providerEx.kind() == AIProviderException.Kind.AUTHENTICATION
                    || providerEx.kind() == AIProviderException.Kind.PROVIDER_FAILURE
                    || providerEx.kind() == AIProviderException.Kind.TIMEOUT;
        }
        return true;
    }

    @Override
    public void close() {
        try {
            primary.close();
        } catch (Exception ignored) {
        } finally {
            try {
                fallback.close();
            } catch (Exception ignored) {}
        }
    }
}

