package edu.physics.ai;

import edu.physics.ai.local.LocalAIProvider;
import edu.physics.ai.ollama.OllamaConfiguration;
import edu.physics.ai.ollama.OllamaProvider;
import edu.physics.ai.ollama.OllamaTransport;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AIProviderManagerTest {
    private static final String SAMPLE_QUESTION =
            "A ball is projected at 20 m/s at 45 degrees with g = 9.81 m/s^2. Find horizontal range.";

    @Test
    void autoMode_UsesGeminiPrimaryWhenSuccessful() {
        var gemini = new RecordingProvider("gemini-3.1-flash-lite", sampleRangeInterpretation(), sampleRangeExplanation());
        var ollama = new RecordingProvider("qwen-physics-test:latest", sampleRangeInterpretation(), sampleRangeExplanation());
        var local = new LocalAIProvider();

        try (var manager = new AIProviderManager(gemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var interp = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            var explain = manager.explain(new ExplanationRequest(SAMPLE_QUESTION, sampleSolution())).toCompletableFuture().join();

            assertEquals(1, gemini.interpretCalls.get());
            assertEquals(1, gemini.explainCalls.get());
            assertEquals(0, ollama.interpretCalls.get());
            assertEquals(0, ollama.explainCalls.get());

            assertEquals(ProviderResult.ProviderId.GEMINI, interp.attribution().provider());
            assertEquals("gemini-3.1-flash-lite", interp.attribution().model());
            assertFalse(interp.attribution().fallbackUsed());
            assertNull(interp.attribution().previousProvider());
            assertNull(interp.attribution().fallbackReason());

            assertEquals(ProviderResult.ProviderId.GEMINI, explain.attribution().provider());
            assertFalse(explain.attribution().fallbackUsed());
        }
    }

    @Test
    void autoMode_FallsBackFromGeminiToOllamaWhenEnabledThenToLocal() {
        // Case 1: Gemini rate limited -> Ollama succeeds
        var failingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.RATE_LIMITED, true, "HTTP 429 quota exceeded"));
        var workingOllama = new RecordingProvider("qwen-physics-test:latest", sampleRangeInterpretation(), sampleRangeExplanation());
        var countingLocal = new CountingLocalProvider();

        try (var manager = new AIProviderManager(failingGemini, workingOllama, countingLocal,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var result = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();

            assertEquals(ProviderResult.ProviderId.OLLAMA, result.attribution().provider());
            assertEquals("qwen-physics-test:latest", result.attribution().model());
            assertTrue(result.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.GEMINI, result.attribution().previousProvider());
            assertTrue(result.attribution().fallbackReason().contains("RATE_LIMITED"));
            assertEquals(0, countingLocal.interpretCalls.get());
        }

        // Case 2: Gemini network outage + Ollama timeout -> LocalAIProvider succeeds with full 2-hop trail
        var networkDownGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.NETWORK, true, new ConnectException("UnknownHostException").getMessage()));
        var timedOutOllama = failingProvider(new AIProviderException(
                AIProviderException.Kind.TIMEOUT, true, "Ollama timed out"));
        var fallbackLocal = new CountingLocalProvider();

        try (var manager = new AIProviderManager(networkDownGemini, timedOutOllama, fallbackLocal,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var result = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();

            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, result.attribution().provider());
            assertEquals("local-physics-engine", result.attribution().model());
            assertTrue(result.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.OLLAMA, result.attribution().previousProvider());
            assertEquals(2, result.attribution().fallbackTrail().size());
            assertEquals(ProviderResult.ProviderId.GEMINI, result.attribution().fallbackTrail().get(0).provider());
            assertEquals(ProviderResult.ProviderId.OLLAMA, result.attribution().fallbackTrail().get(1).provider());
            assertEquals(1, fallbackLocal.interpretCalls.get());
        }

        // Case 3: Missing Gemini credentials (gemini == null) with Ollama enabled in AUTO -> Ollama succeeds cleanly
        var ollamaWithoutGeminiKey = new RecordingProvider("qwen-physics-test:latest", sampleRangeInterpretation(), sampleRangeExplanation());
        try (var managerNoGemini = new AIProviderManager(null, ollamaWithoutGeminiKey, new CountingLocalProvider(),
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var result = managerNoGemini.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.OLLAMA, result.attribution().provider());
            assertTrue(result.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.GEMINI, result.attribution().previousProvider());
            assertTrue(result.attribution().fallbackReason().contains("GOOGLE_API_KEY"));
        }
    }

    @Test
    void disabledOllama_IsNeverContactedInAnyMode() {
        AIProvider strictForbiddenOllama = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                throw new AssertionError("Ollama must never be called when disabled");
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                throw new AssertionError("Ollama must never be called when disabled");
            }
        };

        // 1. AUTO mode with gemini failing -> goes straight to LocalAIProvider without touching Ollama
        var failingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.RATE_LIMITED, true, "429"));
        var local = new CountingLocalProvider();
        try (var autoManager = new AIProviderManager(failingGemini, strictForbiddenOllama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, false))) {
            var res = autoManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, res.attribution().provider());
            assertEquals(ProviderResult.ProviderId.GEMINI, res.attribution().previousProvider());
            assertFalse(autoManager.refreshOllamaAvailability().join());
        }

        // 2. LOCAL_AI mode when Ollama explicitly disabled -> still zero Ollama calls, falls back to LocalAIProvider
        try (var localAiManager = new AIProviderManager(failingGemini, strictForbiddenOllama, local,
                new AIProviderManager.Configuration(ProviderMode.LOCAL_AI, false))) {
            var res = localAiManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, res.attribution().provider());
            assertTrue(res.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.OLLAMA, res.attribution().previousProvider());
            assertTrue(res.attribution().fallbackReason().contains("disabled"));
        }
    }

    @Test
    void cloudOnlyMode_FailsDirectlyWithoutFallbackToOllamaOrLocal() {
        var failingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.RATE_LIMITED, true, "Gemini quota exhausted"));
        var ollama = new RecordingProvider("qwen-physics-test", sampleRangeInterpretation(), sampleRangeExplanation());
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(failingGemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.CLOUD_ONLY, true))) {
            AIProviderException ex = assertProviderFailure(
                    manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture());
            assertEquals(AIProviderException.Kind.RATE_LIMITED, ex.kind());
            assertEquals(0, ollama.interpretCalls.get());
            assertEquals(0, local.interpretCalls.get());
        }

        // Configured Gemini failing with AUTHENTICATION fails directly in CLOUD_ONLY without fallback
        var authFailingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.AUTHENTICATION, false, "Gemini API key rejected (HTTP 403)"));
        try (var manager = new AIProviderManager(authFailingGemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.CLOUD_ONLY, true))) {
            AIProviderException ex = assertProviderFailure(
                    manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture());
            assertEquals(AIProviderException.Kind.AUTHENTICATION, ex.kind());
            assertFalse(ex.retryable());
            assertEquals(0, ollama.interpretCalls.get());
            assertEquals(0, local.interpretCalls.get());
        }

        // Unconfigured Gemini credentials fail cleanly with AUTHENTICATION in CLOUD_ONLY
        try (var unconfigured = new AIProviderManager(null, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.CLOUD_ONLY, true))) {
            AIProviderException ex = assertProviderFailure(
                    unconfigured.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture());
            assertEquals(AIProviderException.Kind.AUTHENTICATION, ex.kind());
            assertFalse(ex.retryable());
            assertEquals(0, ollama.interpretCalls.get());
            assertEquals(0, local.interpretCalls.get());
        }
    }

    @Test
    void localAiMode_TriesOllamaThenFallsBackToLocalWithoutTouchingGemini() {
        AIProvider forbiddenGemini = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                throw new AssertionError("Gemini must never be called in LOCAL_AI mode");
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                throw new AssertionError("Gemini must never be called in LOCAL_AI mode");
            }
        };

        var workingOllama = new RecordingProvider("qwen-physics-test", sampleRangeInterpretation(), sampleRangeExplanation());
        var local = new CountingLocalProvider();
        try (var manager = new AIProviderManager(forbiddenGemini, workingOllama, local,
                new AIProviderManager.Configuration(ProviderMode.LOCAL_AI, true))) {
            var res = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.OLLAMA, res.attribution().provider());
            assertFalse(res.attribution().fallbackUsed());
            assertEquals(0, local.interpretCalls.get());
        }

        var failingOllama = failingProvider(new AIProviderException(
                AIProviderException.Kind.NETWORK, true, "Ollama daemon unreachable"));
        try (var fallbackManager = new AIProviderManager(forbiddenGemini, failingOllama, local,
                new AIProviderManager.Configuration(ProviderMode.LOCAL_AI, true))) {
            var res = fallbackManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, res.attribution().provider());
            assertTrue(res.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.OLLAMA, res.attribution().previousProvider());
            assertEquals(1, local.interpretCalls.get());
        }
    }

    @Test
    void offlineMode_MakesZeroNetworkCallsAndUsesLocalProviderOnly() {
        AIProvider forbiddenCloud = failingProvider(new AssertionError("Cloud provider must not be called in OFFLINE mode"));
        AIProvider forbiddenOllama = failingProvider(new AssertionError("Ollama must not be called in OFFLINE mode"));
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(forbiddenCloud, forbiddenOllama, local,
                new AIProviderManager.Configuration(ProviderMode.OFFLINE, true))) {
            var interp = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            var explain = manager.explain(new ExplanationRequest(SAMPLE_QUESTION, sampleSolution())).toCompletableFuture().join();

            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, interp.attribution().provider());
            assertEquals("local-physics-engine", interp.attribution().model());
            assertFalse(interp.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, explain.attribution().provider());
            assertFalse(explain.attribution().fallbackUsed());
            assertEquals(1, local.interpretCalls.get());
            assertEquals(1, local.explainCalls.get());
        }
    }

    @Test
    void fallbackPolicy_DistinguishesSameProviderRetryFromCrossProviderFallback() {
        var missingOllamaModel = new AIProviderException(AIProviderException.Kind.PROVIDER_FAILURE, false, "model missing");
        assertFalse(AIProviderManager.isRetryableOnSameProvider(missingOllamaModel));
        assertTrue(AIProviderManager.isFallbackEligible(missingOllamaModel));

        var authFailure = new AIProviderException(AIProviderException.Kind.AUTHENTICATION, false, "bad key");
        assertFalse(AIProviderManager.isRetryableOnSameProvider(authFailure));
        assertTrue(AIProviderManager.isFallbackEligible(authFailure));

        var rateLimited = new AIProviderException(AIProviderException.Kind.RATE_LIMITED, true, "429");
        assertTrue(AIProviderManager.isRetryableOnSameProvider(rateLimited));
        assertTrue(AIProviderManager.isFallbackEligible(rateLimited));

        var invalidInput = new AIProviderException(AIProviderException.Kind.INVALID_REQUEST, false, "question too long");
        assertFalse(AIProviderManager.isRetryableOnSameProvider(invalidInput));
        assertFalse(AIProviderManager.isFallbackEligible(invalidInput));

        var refusal = new AIProviderException(AIProviderException.Kind.REFUSAL, false, "refused");
        assertFalse(AIProviderManager.isFallbackEligible(refusal));

        var invalidResponse = new AIProviderException(AIProviderException.Kind.INVALID_RESPONSE, false, "bad json");
        assertFalse(AIProviderManager.isFallbackEligible(invalidResponse));

        assertFalse(AIProviderManager.isFallbackEligible(new IllegalArgumentException("bad domain argument")));
    }

    @Test
    void fallbackTransitions_CoversAuth503AndTotalChainFailure() {
        var authRejectedGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.AUTHENTICATION, false, "HTTP 403 invalid key"));
        var unavailable503Ollama = failingProvider(new AIProviderException(
                AIProviderException.Kind.PROVIDER_FAILURE, true, "HTTP 503 loading"));
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(authRejectedGemini, unavailable503Ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var res = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, res.attribution().provider());
            assertEquals(2, res.attribution().fallbackTrail().size());
            assertEquals(AIProviderException.Kind.AUTHENTICATION, res.attribution().fallbackTrail().get(0).failureKind());
            assertEquals(AIProviderException.Kind.PROVIDER_FAILURE, res.attribution().fallbackTrail().get(1).failureKind());
            assertTrue(res.attribution().fallbackTrail().get(1).retryableOnSameProvider());
        }

        // All providers failing preserves combined diagnostic trail and final kind
        var failingLocal = failingProvider(new AIProviderException(
                AIProviderException.Kind.PROVIDER_FAILURE, false, "local rule parser unavailable"));
        try (var allFailManager = new AIProviderManager(authRejectedGemini, unavailable503Ollama, failingLocal,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            AIProviderException ex = assertProviderFailure(
                    allFailManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture());
            assertEquals(AIProviderException.Kind.PROVIDER_FAILURE, ex.kind());
            assertFalse(ex.retryable());
            assertTrue(ex.getMessage().contains("GEMINI (AUTHENTICATION)"));
            assertTrue(ex.getMessage().contains("OLLAMA (PROVIDER_FAILURE)"));
            assertTrue(ex.getMessage().contains("LOCAL_RULE_ENGINE"));
        }
    }

    @Test
    void invalidRequest_PropagatesDirectlyWithoutFallbackInAutoAndLocalAiModes() {
        var invalidGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.INVALID_REQUEST, false, "Question exceeds limit"));
        var ollama = new RecordingProvider("qwen-physics-test", sampleRangeInterpretation(), sampleRangeExplanation());
        var local = new CountingLocalProvider();

        try (var autoManager = new AIProviderManager(invalidGemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            AIProviderException ex = assertProviderFailure(
                    autoManager.interpret(new InterpretationRequest("bad")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.INVALID_REQUEST, ex.kind());
            assertEquals(0, ollama.interpretCalls.get());
            assertEquals(0, local.interpretCalls.get());
        }

        var invalidOllama = failingProvider(new AIProviderException(
                AIProviderException.Kind.INVALID_REQUEST, false, "HTTP 400 malformed payload"));
        try (var localAiManager = new AIProviderManager(null, invalidOllama, local,
                new AIProviderManager.Configuration(ProviderMode.LOCAL_AI, true))) {
            AIProviderException ex = assertProviderFailure(
                    localAiManager.interpret(new InterpretationRequest("bad")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.INVALID_REQUEST, ex.kind());
            assertEquals(0, local.interpretCalls.get());
        }
    }

    @Test
    void missingOllamaModel_PreventsRepeatedOllamaAttemptsWhileAllowingLocalFallback() {
        var clock = new AtomicLong(1_000_000_000L);
        var failingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.RATE_LIMITED, true, "HTTP 429"));
        var missingModelOllamaCalls = new AtomicInteger();

        // Simulate missing model using real OllamaProvider backed by mock HTTP /api/tags missing qwen-physics-test
        OllamaTransport mockTransport = new OllamaTransport() {
            @Override
            public CompletableFuture<Response> get(String relativePath, Duration timeout) {
                missingModelOllamaCalls.incrementAndGet();
                return CompletableFuture.completedFuture(new Response(200,
                        "{\"models\":[{\"name\":\"llama3:latest\",\"model\":\"llama3:latest\"}]}"));
            }

            @Override
            public CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout) {
                throw new AssertionError("Should not POST /api/chat when model tag is missing");
            }
        };
        var ollamaProvider = new OllamaProvider(OllamaConfiguration.defaultConfiguration(), mockTransport);
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(failingGemini, ollamaProvider, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true, Duration.ofSeconds(10)),
                clock::get)) {

            // Step 1: Gemini 429 -> Ollama missing model (PROVIDER_FAILURE, retryable=false) -> LocalAI succeeds!
            var interp = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, interp.attribution().provider());
            assertTrue(interp.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.OLLAMA, interp.attribution().previousProvider());
            assertEquals(1, missingModelOllamaCalls.get());

            // Step 2: Subsequent explain() 1s later skips Ollama entirely due to unavailable cooldown -> LocalAI handles it
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            var explain = manager.explain(new ExplanationRequest(SAMPLE_QUESTION, sampleSolution())).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, explain.attribution().provider());
            assertEquals(1, missingModelOllamaCalls.get(), "Repeated Ollama probe/call must be suppressed during cooldown");
            assertTrue(explain.attribution().fallbackReason().contains("cooldown"));

            // Step 3: Advancing past cooldown + refreshing allows Ollama check again
            clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
            manager.refreshOllamaAvailability().join();
            assertEquals(2, missingModelOllamaCalls.get());
        }
    }

    @Test
    void unrelatedOllamaProviderFailure_FallsBackWithoutActivatingMissingModelCooldown() {
        var clock = new AtomicLong(1_000_000_000L);
        var failingGemini = failingProvider(new AIProviderException(
                AIProviderException.Kind.RATE_LIMITED, true, "HTTP 429"));
        var postCalls = new AtomicInteger();

        // Real OllamaProvider: tags available, first /api/chat returns HTTP 405 (non-retryable GENERAL provider failure),
        // second /api/chat returns 200. Cooldown MUST NOT trigger on HTTP 405.
        OllamaTransport mockTransport = new OllamaTransport() {
            @Override
            public CompletableFuture<Response> get(String relativePath, Duration timeout) {
                return CompletableFuture.completedFuture(new Response(200,
                        "{\"models\":[{\"name\":\"qwen-physics-test:latest\",\"model\":\"qwen-physics-test:latest\"}]}"));
            }

            @Override
            public CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout) {
                if (postCalls.incrementAndGet() == 1) {
                    return CompletableFuture.completedFuture(new Response(405, "Method Not Allowed"));
                }
                return CompletableFuture.completedFuture(new Response(200, """
                        {
                          "model": "qwen-physics-test:latest",
                          "message": {
                            "role": "assistant",
                            "content": "{\\"sections\\":[{\\"heading\\":\\"Range\\",\\"claims\\":[{\\"text\\":\\"Computed from horizontal velocity and flight duration.\\",\\"resultKey\\":\\"range\\"}]}]}"
                          },
                          "prompt_eval_count": 20,
                          "eval_count": 15,
                          "done": true
                        }
                        """));
            }
        };
        var ollamaProvider = new OllamaProvider(OllamaConfiguration.defaultConfiguration(), mockTransport);
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(failingGemini, ollamaProvider, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true, Duration.ofSeconds(10)),
                AIProviderManager.ProviderOwnership.OWNED, clock::get)) {

            // Step 1: HTTP 405 -> falls back cleanly to LocalAIProvider without activating missing-model cooldown
            var interp = manager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, interp.attribution().provider());
            assertTrue(interp.attribution().fallbackUsed());
            assertEquals(1, postCalls.get());
            assertEquals(1, local.interpretCalls.get());

            // Step 2: Immediate next request (zero clock advance!) STILL attempts Ollama and succeeds!
            var explain = manager.explain(new ExplanationRequest(SAMPLE_QUESTION, sampleSolution())).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.OLLAMA, explain.attribution().provider());
            assertEquals("qwen-physics-test:latest", explain.attribution().model());
            assertEquals(2, postCalls.get());
            assertEquals(0, local.explainCalls.get());
        }

        // Mock non-retryable GENERAL PROVIDER_FAILURE (e.g. generic HTTP 405) also does not activate cooldown
        var generalFailureCalls = new AtomicInteger();
        var endpointMock = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                if (generalFailureCalls.incrementAndGet() == 1) {
                    return CompletableFuture.failedFuture(new AIProviderException(
                            AIProviderException.Kind.PROVIDER_FAILURE, false, "HTTP 405 endpoint error"));
                }
                return CompletableFuture.completedFuture(new ProviderResult<>(
                        sampleRangeInterpretation(), "ol-2", "qwen-physics-test", new ProviderResult.Usage(5, 5)));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        try (var retryableManager = new AIProviderManager(null, endpointMock, new CountingLocalProvider(),
                new AIProviderManager.Configuration(ProviderMode.LOCAL_AI, true, Duration.ofSeconds(10)),
                clock::get)) {
            var first = retryableManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, first.attribution().provider());

            var second = retryableManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.OLLAMA, second.attribution().provider());
            assertEquals(2, generalFailureCalls.get());
        }
    }

    @Test
    void workflowIntegration_TracksInterpretationAndExplanationProvidersIndependently() {
        // Interpretation succeeds on Gemini; Explanation fails 429 on Gemini and falls back to Ollama
        AIProvider mixedGemini = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.completedFuture(new ProviderResult<>(
                        sampleRangeInterpretation(), "gem-int-1", "gemini-3.1-flash-lite",
                        new ProviderResult.Usage(50, 40)));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.RATE_LIMITED, true, "Gemini quota hit on explain"));
            }
        };
        var workingOllama = new RecordingProvider("qwen-physics-test:latest", sampleRangeInterpretation(), sampleRangeExplanation());
        var local = new CountingLocalProvider();

        try (var manager = new AIProviderManager(mixedGemini, workingOllama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true))) {
            var workflow = new AIProblemWorkflow(
                    manager,
                    new ProblemValidator(),
                    new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                    new ExplanationVerifier());

            AIWorkflowResult result = workflow.submit(new InterpretationRequest(SAMPLE_QUESTION))
                    .toCompletableFuture().join();

            assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());

            // Verify interpretation attribution: Gemini directly
            var interpAttr = result.interpretationAttribution();
            assertNotNull(interpAttr);
            assertEquals(ProviderResult.ProviderId.GEMINI, interpAttr.provider());
            assertEquals("gemini-3.1-flash-lite", interpAttr.model());
            assertFalse(interpAttr.fallbackUsed());

            // Verify explanation attribution: independently tracked Ollama after Gemini 429 fallback
            var explainAttr = result.explanationAttribution();
            assertNotNull(explainAttr);
            assertEquals(ProviderResult.ProviderId.OLLAMA, explainAttr.provider());
            assertEquals("qwen-physics-test:latest", explainAttr.model());
            assertTrue(explainAttr.fallbackUsed());
            assertEquals(ProviderResult.ProviderId.GEMINI, explainAttr.previousProvider());
            assertTrue(explainAttr.fallbackReason().contains("RATE_LIMITED"));

            // Ensure Ollama handled explain, Gemini handled interpret, Local was untouched
            assertEquals(0, workingOllama.interpretCalls.get());
            assertEquals(1, workingOllama.explainCalls.get());
            assertEquals(0, local.interpretCalls.get());
            assertEquals(0, local.explainCalls.get());
        }
    }

    @Test
    void factory_DisablesOllamaByDefaultAndOperatesWithoutGeminiCredentialsOrStartupChecks() {
        // Empty environment: AUTO mode, Ollama disabled, missing GOOGLE_API_KEY allowed -> runs via LocalAIProvider
        try (var defaultManager = AIProviderManagerFactory.createFromEnvironment(Map.of())) {
            assertEquals(ProviderMode.AUTO, defaultManager.mode());
            assertFalse(defaultManager.isOllamaEnabled());

            var result = defaultManager.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, result.attribution().provider());
            assertTrue(result.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.GEMINI, result.attribution().previousProvider());
            assertTrue(result.attribution().fallbackReason().contains("GOOGLE_API_KEY"));
        }

        // Explicitly enabled Ollama without GOOGLE_API_KEY succeeds in creation (zero startup network check)
        try (var ollamaEnabledManager = AIProviderManagerFactory.createFromEnvironment(Map.of(
                "PHYSORA_AI_MODE", "local_ai",
                "PHYSORA_OLLAMA_ENABLED", "true"))) {
            assertEquals(ProviderMode.LOCAL_AI, ollamaEnabledManager.mode());
            assertTrue(ollamaEnabledManager.isOllamaEnabled());
        }

        // Legacy PHYSORA_AI_PROVIDER=local maps to OFFLINE with Ollama disabled
        try (var offlineManager = AIProviderManagerFactory.createFromEnvironment(Map.of(
                "PHYSORA_AI_PROVIDER", "local"))) {
            assertEquals(ProviderMode.OFFLINE, offlineManager.mode());
            assertFalse(offlineManager.isOllamaEnabled());
        }

        // PHYSORA_AI_MODE=offline with both PHYSORA_OLLAMA_ENABLED=true and GOOGLE_API_KEY still makes zero network calls
        try (var strictOfflineFactory = AIProviderManagerFactory.createFromEnvironment(Map.of(
                "PHYSORA_AI_MODE", "offline",
                "PHYSORA_OLLAMA_ENABLED", "true",
                "GOOGLE_API_KEY", "dummy-offline-test-key"))) {
            assertEquals(ProviderMode.OFFLINE, strictOfflineFactory.mode());
            assertEquals(AIProviderManager.ProviderOwnership.OWNED, strictOfflineFactory.ownership());
            assertEquals(1, strictOfflineFactory.ownedProviderCount());

            var interp = strictOfflineFactory.interpret(new InterpretationRequest(SAMPLE_QUESTION)).toCompletableFuture().join();
            var explain = strictOfflineFactory.explain(new ExplanationRequest(SAMPLE_QUESTION, sampleSolution())).toCompletableFuture().join();
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, interp.attribution().provider());
            assertEquals("local-physics-engine", interp.attribution().model());
            assertFalse(interp.attribution().fallbackUsed());
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, explain.attribution().provider());
            assertFalse(explain.attribution().fallbackUsed());
        }

        // Explicit PHYSORA_AI_MODE cleanly overrides conflicting legacy PHYSORA_AI_PROVIDER when constructing Gemini config
        try (var overriddenLegacy = AIProviderManagerFactory.createFromEnvironment(Map.of(
                "PHYSORA_AI_MODE", "auto",
                "PHYSORA_AI_PROVIDER", "local",
                "GOOGLE_API_KEY", "dummy-key"))) {
            assertEquals(ProviderMode.AUTO, overriddenLegacy.mode());
            assertEquals(AIProviderManager.ProviderOwnership.OWNED, overriddenLegacy.ownership());
        }

        assertThrows(IllegalArgumentException.class, () ->
                AIProviderManagerFactory.createFromEnvironment(Map.of("PHYSORA_AI_MODE", "invalid_mode")));
        assertThrows(IllegalArgumentException.class, () ->
                AIProviderManagerFactory.createFromEnvironment(Map.of("PHYSORA_OLLAMA_ENABLED", "maybe")));
    }

    @Test
    void ownedManager_ClosesOwnedProvidersOnceAndFactoryManagerOwnsCreatedProviders() {
        var gemini = new ClosingRecorder(false);
        var ollama = new ClosingRecorder(false);
        var local = new ClosingRecorder(false);

        var manager = new AIProviderManager(gemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.OWNED);
        assertEquals(AIProviderManager.ProviderOwnership.OWNED, manager.ownership());
        assertEquals(3, manager.ownedProviderCount());

        manager.close();
        manager.close(); // Idempotent second close must not close providers again
        assertEquals(1, gemini.closeCount.get());
        assertEquals(1, ollama.closeCount.get());
        assertEquals(1, local.closeCount.get());

        try (var factoryManager = AIProviderManagerFactory.createFromEnvironment(Map.of())) {
            assertEquals(AIProviderManager.ProviderOwnership.OWNED, factoryManager.ownership());
            assertEquals(1, factoryManager.ownedProviderCount());
        }
    }

    @Test
    void sharedManager_DoesNotCloseInjectedProviders() {
        var gemini = new ClosingRecorder(false);
        var ollama = new ClosingRecorder(false);
        var local = new ClosingRecorder(false);

        var defaultShared = new AIProviderManager(gemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true));
        assertEquals(AIProviderManager.ProviderOwnership.SHARED, defaultShared.ownership());
        assertEquals(0, defaultShared.ownedProviderCount());
        defaultShared.close();

        var explicitShared = new AIProviderManager(gemini, ollama, local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.SHARED);
        explicitShared.close();

        assertEquals(0, gemini.closeCount.get());
        assertEquals(0, ollama.closeCount.get());
        assertEquals(0, local.closeCount.get());
    }

    @Test
    void ownedManager_DeduplicatesDuplicateProviderInstancesWhenClosing() {
        var sharedInstance = new ClosingRecorder(false);
        var distinctInstance = new ClosingRecorder(false);

        var manager = new AIProviderManager(sharedInstance, sharedInstance, distinctInstance,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.OWNED);
        assertEquals(2, manager.ownedProviderCount());

        manager.close();
        assertEquals(1, sharedInstance.closeCount.get());
        assertEquals(1, distinctInstance.closeCount.get());
    }

    @Test
    void close_ThrowingOwnedProviderDoesNotPreventCleanupOfOtherOwnedProviders() {
        var throwingFirst = new ClosingRecorder(true);
        var cleanSecond = new ClosingRecorder(false);
        var cleanThird = new ClosingRecorder(false);

        var manager = new AIProviderManager(throwingFirst, cleanSecond, cleanThird,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.OWNED);

        assertDoesNotThrow(manager::close);
        assertEquals(1, throwingFirst.closeCount.get());
        assertEquals(1, cleanSecond.closeCount.get());
        assertEquals(1, cleanThird.closeCount.get());
    }

    private static AIProvider failingProvider(Throwable failure) {
        return new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                if (failure instanceof Error err) throw err;
                return CompletableFuture.failedFuture(failure);
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                if (failure instanceof Error err) throw err;
                return CompletableFuture.failedFuture(failure);
            }
        };
    }

    private static AIProviderException assertProviderFailure(CompletableFuture<?> future) {
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(AIProviderException.class, ex.getCause());
        return (AIProviderException) ex.getCause();
    }

    private static InterpretationDraft sampleRangeInterpretation() {
        return new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.HORIZONTAL_RANGE,
                        Map.of(
                                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)
                        ))),
                List.of("Air resistance ignored"),
                List.of(),
                0.98);
    }

    private static ExplanationDraft sampleRangeExplanation() {
        return new ExplanationDraft(List.of(
                new ExplanationDraft.Section("Range Analysis", List.of(
                        new ExplanationDraft.Claim("The horizontal range is calculated from constant horizontal velocity and flight time.", "range")))
        ));
    }

    private static ProjectileSequenceSolution sampleSolution() {
        var givens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
        var problem = new ProjectileProblem("range", ProjectileProblem.Target.HORIZONTAL_RANGE, givens);
        var solution = new ProjectileProblemSolver().solve(problem);
        return new ProjectileSequenceSolution(solution.status(), List.of(
                new ProjectileSequenceSolution.PartResult(0, solution)), -1, List.of());
    }

    private static final class RecordingProvider implements AIProvider {
        private final String modelName;
        private final InterpretationDraft interpretation;
        private final ExplanationDraft explanation;
        private final AtomicInteger interpretCalls = new AtomicInteger();
        private final AtomicInteger explainCalls = new AtomicInteger();

        private RecordingProvider(String modelName, InterpretationDraft interpretation, ExplanationDraft explanation) {
            this.modelName = modelName;
            this.interpretation = interpretation;
            this.explanation = explanation;
        }

        @Override
        public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
            interpretCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new ProviderResult<>(
                    interpretation, "req-int", modelName, new ProviderResult.Usage(10, 20)));
        }

        @Override
        public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
            explainCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new ProviderResult<>(
                    explanation, "req-exp", modelName, new ProviderResult.Usage(15, 25)));
        }
    }

    private static final class CountingLocalProvider implements AIProvider {
        private final LocalAIProvider delegate = new LocalAIProvider();
        private final AtomicInteger interpretCalls = new AtomicInteger();
        private final AtomicInteger explainCalls = new AtomicInteger();

        @Override
        public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
            interpretCalls.incrementAndGet();
            return delegate.interpret(request);
        }

        @Override
        public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
            explainCalls.incrementAndGet();
            return delegate.explain(request);
        }
    }

    private static final class ClosingRecorder implements AIProvider {
        private final boolean throwOnClose;
        private final AtomicInteger closeCount = new AtomicInteger();

        private ClosingRecorder(boolean throwOnClose) {
            this.throwOnClose = throwOnClose;
        }

        @Override
        public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            if (throwOnClose) {
                throw new RuntimeException("simulated close error");
            }
        }
    }
}
