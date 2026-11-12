package edu.physics.ai;

import edu.physics.ai.ollama.OllamaProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Orchestrates multi-tier AI routing across Gemini (cloud primary), optional Ollama (local LLM),
 * and LocalAIProvider (always-available rule-based fallback) according to {@link ProviderMode}.
 *
 * <p>Tracks independent per-stage attribution on each {@link ProviderResult} and distinguishes
 * same-provider retryability from cross-provider fallback eligibility.</p>
 */
public final class AIProviderManager implements AIProvider {

    /** Controls whether {@link #close()} closes injected providers. */
    public enum ProviderOwnership {
        /** Externally supplied/shared providers are NOT closed by the manager. */
        SHARED,
        /** Owned providers created for this manager ARE closed once on manager close. */
        OWNED
    }

    public record Configuration(
            ProviderMode mode,
            boolean ollamaEnabled,
            Duration ollamaUnavailableCooldown
    ) {
        public static final Duration DEFAULT_OLLAMA_UNAVAILABLE_COOLDOWN = Duration.ofSeconds(10);

        public Configuration {
            if (mode == null) throw new IllegalArgumentException("Provider mode is required");
            if (ollamaUnavailableCooldown == null || ollamaUnavailableCooldown.isNegative()) {
                throw new IllegalArgumentException("ollamaUnavailableCooldown must be non-negative");
            }
        }

        public Configuration(ProviderMode mode, boolean ollamaEnabled) {
            this(mode, ollamaEnabled, DEFAULT_OLLAMA_UNAVAILABLE_COOLDOWN);
        }

        public static Configuration defaultConfiguration() {
            return new Configuration(ProviderMode.AUTO, false, DEFAULT_OLLAMA_UNAVAILABLE_COOLDOWN);
        }
    }

    private final AIProvider geminiProvider;
    private final AIProvider ollamaProvider;
    private final AIProvider localProvider;
    private final Configuration configuration;
    private final ProviderOwnership ownership;
    private final List<AIProvider> ownedProviders;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final LongSupplier nanoClock;
    private final Object cooldownLock = new Object();
    private long ollamaBlockedUntilNanos = Long.MIN_VALUE;

    /** Creates a manager referencing externally injected/shared providers (not closed on {@link #close()}). */
    public AIProviderManager(AIProvider geminiProvider,
                             AIProvider ollamaProvider,
                             AIProvider localProvider,
                             Configuration configuration) {
        this(geminiProvider, ollamaProvider, localProvider, configuration, ProviderOwnership.SHARED);
    }

    /** Creates a manager with explicit provider ownership semantics. */
    public AIProviderManager(AIProvider geminiProvider,
                             AIProvider ollamaProvider,
                             AIProvider localProvider,
                             Configuration configuration,
                             ProviderOwnership ownership) {
        this(geminiProvider, ollamaProvider, localProvider, configuration, ownership, System::nanoTime);
    }

    AIProviderManager(AIProvider geminiProvider,
                      AIProvider ollamaProvider,
                      AIProvider localProvider,
                      Configuration configuration,
                      LongSupplier nanoClock) {
        this(geminiProvider, ollamaProvider, localProvider, configuration, ProviderOwnership.SHARED, nanoClock);
    }

    AIProviderManager(AIProvider geminiProvider,
                      AIProvider ollamaProvider,
                      AIProvider localProvider,
                      Configuration configuration,
                      ProviderOwnership ownership,
                      LongSupplier nanoClock) {
        if (localProvider == null) {
            throw new IllegalArgumentException("Local rule-based provider is required");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Manager configuration is required");
        }
        if (ownership == null) {
            throw new IllegalArgumentException("Provider ownership is required");
        }
        if (nanoClock == null) {
            throw new IllegalArgumentException("Monotonic clock source is required");
        }
        this.geminiProvider = geminiProvider;
        this.ollamaProvider = ollamaProvider;
        this.localProvider = localProvider;
        this.configuration = configuration;
        this.ownership = ownership;
        this.ownedProviders = collectOwnedProviders(ownership, geminiProvider, ollamaProvider, localProvider);
        this.nanoClock = nanoClock;
    }

    public Configuration configuration() {
        return configuration;
    }

    public ProviderMode mode() {
        return configuration.mode();
    }

    public boolean isOllamaEnabled() {
        return configuration.ollamaEnabled();
    }

    public ProviderOwnership ownership() {
        return ownership;
    }

    public int ownedProviderCount() {
        return ownedProviders.size();
    }

    /**
     * Distinguishes whether an error supports retrying the SAME provider.
     * For example, an absent Ollama model sets retryable=false, preventing repeated Ollama retries.
     */
    public static boolean isRetryableOnSameProvider(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof AIProviderException providerEx && providerEx.retryable();
    }

    /**
     * Determines whether an error is eligible to switch to the NEXT provider tier.
     * Note that non-retryable infrastructure/availability errors (e.g., missing Ollama model or
     * bad cloud API key) still allow falling back to another provider, whereas invalid caller inputs
     * or malformed domain requests ({@link AIProviderException.Kind#INVALID_REQUEST}) do not.
     */
    public static boolean isFallbackEligible(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
            return false;
        }
        if (cause instanceof AIProviderException providerEx) {
            return switch (providerEx.kind()) {
                case RATE_LIMITED, NETWORK, TIMEOUT, AUTHENTICATION, PROVIDER_FAILURE -> true;
                case INVALID_REQUEST, REFUSAL, INVALID_RESPONSE -> false;
            };
        }
        return false;
    }

    /**
     * Clears any missing-model cooldown on the manager and, if Ollama is enabled and backed by
     * {@link OllamaProvider}, triggers a bounded availability refresh. Makes zero network calls
     * when Ollama is disabled.
     */
    public CompletableFuture<Boolean> refreshOllamaAvailability() {
        synchronized (cooldownLock) {
            ollamaBlockedUntilNanos = Long.MIN_VALUE;
        }
        if (!configuration.ollamaEnabled() || ollamaProvider == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (ollamaProvider instanceof OllamaProvider concreteOllama) {
            return concreteOllama.refreshModelAvailability();
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Interpretation request is required"));
        }
        return executeWithRouting(provider -> provider.interpret(request));
    }

    @Override
    public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Explanation request is required"));
        }
        return executeWithRouting(provider -> provider.explain(request));
    }

    private <T> CompletionStage<ProviderResult<T>> executeWithRouting(
            Function<AIProvider, CompletionStage<ProviderResult<T>>> invocation) {
        List<CandidateSlot> candidates = buildCandidateChain();
        return attemptCandidate(candidates, 0, invocation, new ArrayList<>());
    }

    private <T> CompletionStage<ProviderResult<T>> attemptCandidate(
            List<CandidateSlot> candidates,
            int index,
            Function<AIProvider, CompletionStage<ProviderResult<T>>> invocation,
            List<ProviderResult.FallbackHop> trail) {
        CandidateSlot slot = candidates.get(index);
        boolean hasNext = index + 1 < candidates.size();

        if (slot.provider() == null) {
            var hop = new ProviderResult.FallbackHop(
                    slot.id(), slot.unavailableKind(), false, slot.unavailableReason());
            trail.add(hop);
            if (!hasNext) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        slot.unavailableKind(), false, slot.unavailableReason()));
            }
            return attemptCandidate(candidates, index + 1, invocation, trail);
        }

        if (slot.id() == ProviderResult.ProviderId.OLLAMA && isOllamaBlocked()) {
            String reason = "Ollama model unavailable (cooldown active; skipped repeated attempt)";
            trail.add(new ProviderResult.FallbackHop(
                    ProviderResult.ProviderId.OLLAMA, AIProviderException.Kind.PROVIDER_FAILURE, false, reason));
            if (!hasNext) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.PROVIDER_FAILURE, false, reason));
            }
            return attemptCandidate(candidates, index + 1, invocation, trail);
        }

        CompletionStage<ProviderResult<T>> stage;
        try {
            stage = invocation.apply(slot.provider());
        } catch (Throwable syncFailure) {
            stage = CompletableFuture.failedFuture(syncFailure);
        }

        return stage.handle((result, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(attachAttribution(result, slot.id(), trail));
            }
            Throwable cause = unwrap(failure);
            if (slot.id() == ProviderResult.ProviderId.OLLAMA && isUnavailableModelFailure(cause)) {
                markOllamaUnavailable();
            }

            if (hasNext && isFallbackEligible(cause)) {
                AIProviderException.Kind kind = failureKind(cause);
                String reason = safeMessage(cause);
                trail.add(new ProviderResult.FallbackHop(
                        slot.id(), kind, isRetryableOnSameProvider(cause), reason));
                return attemptCandidate(candidates, index + 1, invocation, trail).toCompletableFuture();
            }

            if (trail.isEmpty() || !isFallbackEligible(cause)) {
                return CompletableFuture.<ProviderResult<T>>failedFuture(cause);
            }

            // All candidates failed cleanly; preserve final kind and prior attempt context
            AIProviderException.Kind finalKind = failureKind(cause);
            boolean retryable = isRetryableOnSameProvider(cause);
            String combinedMessage = formatTrail(trail) + "; final failure (" + slot.id() + "): " + safeMessage(cause);
            return CompletableFuture.<ProviderResult<T>>failedFuture(
                    new AIProviderException(finalKind, retryable, combinedMessage));
        }).thenCompose(next -> next);
    }

    private List<CandidateSlot> buildCandidateChain() {
        return switch (configuration.mode()) {
            case OFFLINE -> List.of(localSlot());
            case CLOUD_ONLY -> List.of(geminiSlot());
            case LOCAL_AI -> {
                List<CandidateSlot> chain = new ArrayList<>(2);
                chain.add(ollamaSlot(true));
                chain.add(localSlot());
                yield List.copyOf(chain);
            }
            case AUTO -> {
                List<CandidateSlot> chain = new ArrayList<>(3);
                chain.add(geminiSlot());
                if (configuration.ollamaEnabled()) {
                    chain.add(ollamaSlot(false));
                }
                chain.add(localSlot());
                yield List.copyOf(chain);
            }
        };
    }

    private CandidateSlot geminiSlot() {
        if (geminiProvider != null) {
            return new CandidateSlot(ProviderResult.ProviderId.GEMINI, geminiProvider, null, null);
        }
        return new CandidateSlot(ProviderResult.ProviderId.GEMINI, null,
                AIProviderException.Kind.AUTHENTICATION,
                "Google Gemini is not configured; set GOOGLE_API_KEY in the local environment");
    }

    private CandidateSlot ollamaSlot(boolean includeDisabledReason) {
        if (!configuration.ollamaEnabled()) {
            return new CandidateSlot(ProviderResult.ProviderId.OLLAMA, null,
                    AIProviderException.Kind.PROVIDER_FAILURE,
                    includeDisabledReason ? "Ollama local AI is disabled" : "Ollama disabled");
        }
        if (ollamaProvider == null) {
            return new CandidateSlot(ProviderResult.ProviderId.OLLAMA, null,
                    AIProviderException.Kind.PROVIDER_FAILURE,
                    "Ollama local provider is not configured");
        }
        return new CandidateSlot(ProviderResult.ProviderId.OLLAMA, ollamaProvider, null, null);
    }

    private CandidateSlot localSlot() {
        return new CandidateSlot(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, localProvider, null, null);
    }

    private static <T> ProviderResult<T> attachAttribution(ProviderResult<T> result,
                                                           ProviderResult.ProviderId actualProvider,
                                                           List<ProviderResult.FallbackHop> trail) {
        boolean fallbackUsed = !trail.isEmpty();
        ProviderResult.ProviderId previousProvider = fallbackUsed ? trail.get(trail.size() - 1).provider() : null;
        String fallbackReason = fallbackUsed ? formatTrail(trail) : null;
        return result.withAttribution(new ProviderResult.ProviderAttribution(
                actualProvider, result.model(), fallbackUsed, previousProvider, fallbackReason, trail));
    }

    private boolean isOllamaBlocked() {
        long cooldownNanos = configuration.ollamaUnavailableCooldown().toNanos();
        if (cooldownNanos <= 0) {
            return false;
        }
        synchronized (cooldownLock) {
            return ollamaBlockedUntilNanos != Long.MIN_VALUE
                    && (ollamaBlockedUntilNanos - nanoClock.getAsLong()) > 0;
        }
    }

    private void markOllamaUnavailable() {
        long cooldownNanos = configuration.ollamaUnavailableCooldown().toNanos();
        if (cooldownNanos <= 0) {
            return;
        }
        synchronized (cooldownLock) {
            ollamaBlockedUntilNanos = nanoClock.getAsLong() + cooldownNanos;
        }
    }

    private static boolean isUnavailableModelFailure(Throwable cause) {
        return cause instanceof AIProviderException providerEx
                && providerEx.kind() == AIProviderException.Kind.PROVIDER_FAILURE
                && !providerEx.retryable()
                && providerEx.reason() == AIProviderException.Reason.MODEL_UNAVAILABLE;
    }

    private static AIProviderException.Kind failureKind(Throwable cause) {
        return cause instanceof AIProviderException providerEx
                ? providerEx.kind()
                : AIProviderException.Kind.PROVIDER_FAILURE;
    }

    private static String safeMessage(Throwable cause) {
        String msg = cause.getMessage();
        return msg != null && !msg.isBlank() ? msg : cause.getClass().getSimpleName();
    }

    private static String formatTrail(List<ProviderResult.FallbackHop> trail) {
        return trail.stream()
                .map(hop -> hop.provider() + " (" + hop.failureKind() + "): " + hop.reason())
                .collect(Collectors.joining(" -> "));
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (AIProvider provider : ownedProviders) {
                closeQuietly(provider);
            }
        }
    }

    private static List<AIProvider> collectOwnedProviders(ProviderOwnership ownership, AIProvider... candidates) {
        if (ownership != ProviderOwnership.OWNED) {
            return List.of();
        }
        Set<AIProvider> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AIProvider> owned = new ArrayList<>(candidates.length);
        for (AIProvider candidate : candidates) {
            if (candidate != null && seen.add(candidate)) {
                owned.add(candidate);
            }
        }
        return List.copyOf(owned);
    }

    private static void closeQuietly(AIProvider provider) {
        if (provider != null) {
            try {
                provider.close();
            } catch (Exception ignored) {
            }
        }
    }

    private record CandidateSlot(
            ProviderResult.ProviderId id,
            AIProvider provider,
            AIProviderException.Kind unavailableKind,
            String unavailableReason
    ) {}
}
