package edu.physics.ai;

import edu.physics.ai.local.LocalAIProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class FallbackAIProviderTest {
    @Test
    void delegatesToPrimaryWhenSuccessful() {
        LocalAIProvider primary = new LocalAIProvider();
        LocalAIProvider fallback = new LocalAIProvider();
        FallbackAIProvider compound = new FallbackAIProvider(primary, fallback);

        var request = new InterpretationRequest("A ball at 20 m/s at 45 degrees with g = 10 m/s^2. Find range.");
        var result = compound.interpret(request).toCompletableFuture().join();

        assertNotNull(result);
        assertEquals("local-physics-engine", result.model());
    }

    @Test
    void transparentlyFallsBackOnRateLimit() {
        AIProvider failingPrimary = new AIProvider() {
            @Override
            public CompletableFuture<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.RATE_LIMITED, true, "Rate limit reached (429)"));
            }

            @Override
            public CompletableFuture<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.RATE_LIMITED, true, "Rate limit reached (429)"));
            }

            @Override
            public void close() {}
        };

        LocalAIProvider fallback = new LocalAIProvider();
        FallbackAIProvider compound = new FallbackAIProvider(failingPrimary, fallback);

        var request = new InterpretationRequest("A ball at 20 m/s at 45 degrees with g = 10 m/s^2. Find range.");
        var result = compound.interpret(request).toCompletableFuture().join();

        assertNotNull(result, "Fallback provider should have fulfilled the request");
        assertFalse(result.content().parts().isEmpty());
    }

    @Test
    void transparentlyFallsBackOnNetworkFailure() {
        AIProvider failingPrimary = new AIProvider() {
            @Override
            public CompletableFuture<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.NETWORK, true, "Could not reach host"));
            }

            @Override
            public CompletableFuture<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.NETWORK, true, "Could not reach host"));
            }

            @Override
            public void close() {}
        };

        LocalAIProvider fallback = new LocalAIProvider();
        FallbackAIProvider compound = new FallbackAIProvider(failingPrimary, fallback);

        var request = new InterpretationRequest("A ball at 20 m/s at 45 degrees with g = 10 m/s^2. Find range.");
        var result = compound.interpret(request).toCompletableFuture().join();

        assertNotNull(result);
        assertFalse(result.content().parts().isEmpty());
    }
}

