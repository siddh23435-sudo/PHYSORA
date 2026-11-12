package edu.physics.ai.ollama;

import edu.physics.ai.AIProviderException;
import edu.physics.ai.ExplanationDraft;
import edu.physics.ai.ExplanationRequest;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProviderResult;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OllamaProviderTest {
    private static final OllamaConfiguration CONFIG = new OllamaConfiguration(
            "http://127.0.0.1:11434", "qwen-physics-test", Duration.ofMillis(200), Duration.ofSeconds(5));

    private static final String TAGS_JSON_SUCCESS = """
            {
              "models": [
                {
                  "name": "qwen-physics-test:latest",
                  "model": "qwen-physics-test:latest",
                  "size": 4683083040
                },
                {
                  "name": "llama3:latest",
                  "model": "llama3:latest",
                  "size": 4000000000
                }
              ]
            }
            """;

    private static final String TAGS_JSON_MISSING_MODEL = """
            {
              "models": [
                {
                  "name": "qwen2.5:latest",
                  "model": "qwen2.5:latest"
                },
                {
                  "name": "llama3:latest",
                  "model": "llama3:latest"
                }
              ]
            }
            """;

    private static final String TAGS_JSON_ARBITRARY_VERSION_TAGS = """
            {
              "models": [
                {
                  "name": "qwen-physics-test:v1",
                  "model": "qwen-physics-test:v1"
                },
                {
                  "name": "qwen-physics-test:experimental",
                  "model": "qwen-physics-test:experimental"
                },
                {
                  "name": "qwen-physics-test:9b",
                  "model": "qwen-physics-test:9b"
                }
              ]
            }
            """;

    private static final String CHAT_INTERPRETATION_JSON = """
            {
              "model": "qwen-physics-test:latest",
              "message": {
                "role": "assistant",
                "content": "```json\\n{\\n  \\"normalizedQuestion\\": \\"Find maximum height\\",\\n  \\"parts\\": [\\n    {\\n      \\"target\\": \\"MAXIMUM_HEIGHT\\",\\n      \\"givens\\": [\\n        {\\"input\\": \\"INITIAL_SPEED\\", \\"value\\": 20.0, \\"unit\\": \\"METRE_PER_SECOND\\"},\\n        {\\"input\\": \\"LAUNCH_ANGLE\\", \\"value\\": 45.0, \\"unit\\": \\"DEGREE\\"},\\n        {\\"input\\": \\"GRAVITY\\", \\"value\\": 9.81, \\"unit\\": \\"METRE_PER_SECOND_SQUARED\\"}\\n      ],\\n      \\"references\\": []\\n    }\\n  ],\\n  \\"assumptions\\": [\\"Air resistance ignored\\"],\\n  \\"clarifications\\": [],\\n  \\"confidence\\": 0.95\\n}\\n```"
              },
              "prompt_eval_count": 120,
              "eval_count": 85,
              "done": true
            }
            """;

    private static final String CHAT_EXPLANATION_JSON = """
            {
              "model": "qwen-physics-test:latest",
              "message": {
                "role": "assistant",
                "content": "{\\n  \\"sections\\": [\\n    {\\n      \\"heading\\": \\"Peak Height Analysis\\",\\n      \\"claims\\": [\\n        {\\"text\\": \\"The maximum vertical height is determined by initial upward velocity and gravity.\\", \\"resultKey\\": \\"maximumHeight\\"}\\n      ]\\n    }\\n  ]\\n}"
              },
              "prompt_eval_count": 210,
              "eval_count": 64,
              "done": true
            }
            """;

    @Test
    void isModelMatching_AcceptsConfiguredModelAndLatestEquivalent() {
        assertTrue(OllamaProvider.isModelMatching("qwen-physics-test", "qwen-physics-test"));
        assertTrue(OllamaProvider.isModelMatching("qwen-physics-test:latest", "qwen-physics-test"));
        assertTrue(OllamaProvider.isModelMatching("QWEN-PHYSICS-TEST:LATEST", " qwen-physics-test "));
        assertTrue(OllamaProvider.isModelMatching("qwen-physics-test", "qwen-physics-test:latest"));
        assertTrue(OllamaProvider.isModelMatching("qwen-physics-test:latest", "qwen-physics-test:latest"));
        assertTrue(OllamaProvider.isModelMatching("qwen-physics-test:v1", "qwen-physics-test:v1"));
    }

    @Test
    void isModelMatching_StrictlyRejectsArbitraryAndUnrelatedTags() {
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test:v1", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test:experimental", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test:9b", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen2.5:latest", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen:latest", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("llama3:latest", "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test", "qwen-physics-test:v1"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test:v2", "qwen-physics-test:v1"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test:v1:latest", "qwen-physics-test:v1"));
        assertFalse(OllamaProvider.isModelMatching("", ""));
        assertFalse(OllamaProvider.isModelMatching(null, "qwen-physics-test"));
        assertFalse(OllamaProvider.isModelMatching("qwen-physics-test", null));
    }

    @Test
    void isModelAvailable_RejectsArbitraryTagsWhenConfiguredModelHasNoTag() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_ARBITRARY_VERSION_TAGS));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            assertFalse(provider.isModelAvailable().join());
        }
    }

    @Test
    void extractJsonContent_StripsMarkdownFencesAndOuterText() {
        assertEquals("{\"a\": 1}", OllamaProvider.extractJsonContent("{\"a\": 1}"));
        assertEquals("{\"a\": 1}", OllamaProvider.extractJsonContent("```json\n{\"a\": 1}\n```"));
        assertEquals("{\"a\": 1}", OllamaProvider.extractJsonContent("```\n{\"a\": 1}\n```"));
        assertEquals("{\"a\": 1}", OllamaProvider.extractJsonContent("Here is the JSON:\n```json\n{\"a\": 1}\n```\nDone!"));
    }

    @Test
    void isModelAvailable_ReturnsTrueWhenModelExistsInTags() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            assertTrue(provider.isModelAvailable().join());
        }
    }

    @Test
    void isModelAvailable_ReturnsFalseWhenModelMissingInTags() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_MISSING_MODEL));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            assertFalse(provider.isModelAvailable().join());
        }
    }

    @Test
    void isModelAvailable_HandlesServerDownOrTimeoutGracefully() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGetFailure(new ConnectException("Connection refused"));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            assertFalse(provider.isModelAvailable().join());
        }
    }

    @Test
    void availabilityCache_InvalidatesPositiveCacheWhenServerStopsWithConnectionError() {
        var clock = new AtomicLong(1_000_000_000L);
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePostFailure(new ConnectException("Connection refused"));
        fakeTransport.enqueueGetFailure(new ConnectException("Connection refused"));

        var provider = newTestProvider(fakeTransport, clock);

        assertTrue(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        // Within 10s positive TTL: server stops before /api/chat
        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        AIProviderException failure = assertProviderFailure(
                provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
        assertEquals(AIProviderException.Kind.NETWORK, failure.kind());
        assertTrue(failure.retryable());

        // Still within original 10s TTL: cache must have been invalidated, forcing fresh probe -> false
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertFalse(provider.isModelAvailable().join());
        assertEquals(2, fakeTransport.getCallCount());
    }

    @Test
    void availabilityCache_InvalidatesPositiveCacheOnHttp404ModelNotFound() {
        var clock = new AtomicLong(1_000_000_000L);
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePost(new OllamaTransport.Response(404, "{\"error\":\"model 'qwen-physics-test' not found\"}"));
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_MISSING_MODEL));

        var provider = newTestProvider(fakeTransport, clock);

        assertTrue(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        AIProviderException failure = assertProviderFailure(
                provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
        assertEquals(AIProviderException.Kind.PROVIDER_FAILURE, failure.kind());
        assertFalse(failure.retryable());
        assertEquals(AIProviderException.Reason.MODEL_UNAVAILABLE, failure.reason());

        // Positive cache invalidated: re-probes immediately and detects missing model
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertFalse(provider.isModelAvailable().join());
        assertEquals(2, fakeTransport.getCallCount());
    }

    @Test
    void availabilityCache_RefreshesWhenServerStartsDuringCacheTtl() {
        var clock = new AtomicLong(1_000_000_000L);
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGetFailure(new ConnectException("Connection refused"));
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueueGetFailure(new ConnectException("Connection refused"));
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));

        var provider = newTestProvider(fakeTransport, clock);

        // Server down initially -> negative cache active
        assertFalse(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        // Within negative TTL (1s < 2s), cached false avoids hammering dead port
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertFalse(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        // User starts Ollama while negative cache is still warm -> safe explicit refresh recovers immediately
        assertTrue(provider.refreshModelAvailability().join());
        assertEquals(2, fakeTransport.getCallCount());
        assertTrue(provider.isModelAvailable().join());
        assertEquals(2, fakeTransport.getCallCount());

        // Also verify automatic refresh when negative TTL (2s) expires inside the 10s window
        provider.invalidateAvailabilityCache();
        assertFalse(provider.isModelAvailable().join());
        assertEquals(3, fakeTransport.getCallCount());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
        assertTrue(provider.isModelAvailable().join());
        assertEquals(4, fakeTransport.getCallCount());
    }

    @Test
    void availabilityCache_CoalescesConcurrentCacheMissesIntoSingleProbe() throws Exception {
        var gate = new CompletableFuture<OllamaTransport.Response>();
        var probeInvoked = new CountDownLatch(1);
        var fakeTransport = new FakeOllamaTransport() {
            @Override
            public CompletableFuture<Response> get(String relativePath, Duration timeout) {
                super.getCallCount.incrementAndGet();
                probeInvoked.countDown();
                return gate;
            }
        };

        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            int callers = 12;
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                var startLatch = new CountDownLatch(1);
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            startLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return provider.isModelAvailable();
                    }, pool).thenCompose(f -> f));
                }
                startLatch.countDown();
                assertTrue(probeInvoked.await(3, TimeUnit.SECONDS));
                gate.complete(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));

                for (CompletableFuture<Boolean> f : futures) {
                    assertTrue(f.join());
                }
                assertEquals(1, fakeTransport.getCallCount());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void availabilityCache_UsesMonotonicClockForPositiveTtlExpiration() {
        var clock = new AtomicLong(5_000_000_000L);
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));

        var provider = newTestProvider(fakeTransport, clock);

        assertTrue(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(9));
        assertTrue(provider.isModelAvailable().join());
        assertEquals(1, fakeTransport.getCallCount());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(2)); // total 11s > 10s positive TTL
        assertTrue(provider.isModelAvailable().join());
        assertEquals(2, fakeTransport.getCallCount());
    }

    @Test
    void constructor_RejectsNullConfigurationCleanly() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaProvider(null));
        assertThrows(IllegalArgumentException.class, () -> new OllamaProvider(null, new FakeOllamaTransport()));
        assertThrows(IllegalArgumentException.class, () -> new OllamaProvider(CONFIG, null));
    }

    @Test
    void tagsProbe_HandlesNon200ResponsesAndTimeoutsWithMeaningfulClassification() {
        assertTagsFailure(new OllamaTransport.Response(401, "unauthorized"),
                AIProviderException.Kind.AUTHENTICATION, false);
        assertTagsFailure(new OllamaTransport.Response(404, "missing"),
                AIProviderException.Kind.PROVIDER_FAILURE, false);
        assertTagsFailure(new OllamaTransport.Response(429, "rate limited"),
                AIProviderException.Kind.RATE_LIMITED, true);
        assertTagsFailure(new OllamaTransport.Response(500, "internal error"),
                AIProviderException.Kind.PROVIDER_FAILURE, true);
        assertTagsFailure(new OllamaTransport.Response(503, "service unavailable"),
                AIProviderException.Kind.PROVIDER_FAILURE, true);

        var timeoutTransport = new FakeOllamaTransport();
        timeoutTransport.enqueueGetFailure(new HttpTimeoutException("probe timed out"));
        try (var provider = new OllamaProvider(CONFIG, timeoutTransport)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.TIMEOUT, ex.kind());
            assertTrue(ex.retryable());
        }
    }

    @Test
    void chatEndpoint_HandlesNon200HttpErrorsWithMeaningfulKindsAndRetryability() {
        assertChatFailure(400, "{\"error\":\"bad request\"}", AIProviderException.Kind.INVALID_REQUEST, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(403, "{\"error\":\"forbidden\"}", AIProviderException.Kind.AUTHENTICATION, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(404, "{\"error\":\"model 'qwen-physics-test' not found\"}", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.MODEL_UNAVAILABLE);
        assertChatFailure(404, "", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(404, "   ", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(404, "<html><body>404 Not Found</body></html>", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(404, "404 page not found", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(404, "{\"error\":\"model not found\"}", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(405, "Method Not Allowed", AIProviderException.Kind.PROVIDER_FAILURE, false, AIProviderException.Reason.GENERAL);
        assertChatFailure(429, "{\"error\":\"rate limit\"}", AIProviderException.Kind.RATE_LIMITED, true, AIProviderException.Reason.GENERAL);
        assertChatFailure(500, "{\"error\":\"server crash\"}", AIProviderException.Kind.PROVIDER_FAILURE, true, AIProviderException.Reason.GENERAL);
        assertChatFailure(503, "{\"error\":\"model loading\"}", AIProviderException.Kind.PROVIDER_FAILURE, true, AIProviderException.Reason.GENERAL);
    }

    @Test
    void threadSafety_RunsSerializationAndTransportCallsOffCallerThreadEvenOnCacheHit() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePost(new OllamaTransport.Response(200, CHAT_INTERPRETATION_JSON));
        fakeTransport.enqueuePost(new OllamaTransport.Response(200, CHAT_EXPLANATION_JSON));

        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            Thread testThread = Thread.currentThread();
            assertTrue(provider.isModelAvailable().join()); // Warm cache so interpret/explain hit cache

            provider.interpret(new InterpretationRequest("Launch at 20 m/s at 45 deg")).toCompletableFuture().join();
            provider.explain(new ExplanationRequest("Find max height", sampleSequenceSolution())).toCompletableFuture().join();

            assertEquals(1, fakeTransport.getThreads().size());
            assertNotEquals(testThread, fakeTransport.getThreads().get(0));
            assertTrue(fakeTransport.getThreads().get(0).getName().startsWith("physora-ollama-worker"));
            assertEquals(2, fakeTransport.postThreads().size());
            fakeTransport.postThreads().forEach(worker -> {
                assertNotEquals(testThread, worker);
                assertTrue(worker.getName().startsWith("physora-ollama-worker"));
            });
        }
    }

    @Test
    void responseGuard_RejectsOversizedAndEmptyResponses() {
        // 1. Oversized HTTP response body on /api/chat (> MAX_RESPONSE_CHARACTERS)
        String hugeBody = "x".repeat(OllamaProvider.MAX_RESPONSE_CHARACTERS + 1);
        assertChatResponseInvalid(new OllamaTransport.Response(200, hugeBody));

        // 2. Empty HTTP 200 body on /api/chat
        assertChatResponseInvalid(new OllamaTransport.Response(200, "   "));

        // 3. Empty/blank message.content
        String blankContent = """
                {
                  "model": "qwen-physics-test",
                  "message": {"role": "assistant", "content": "   "},
                  "done": true
                }
                """;
        assertChatResponseInvalid(new OllamaTransport.Response(200, blankContent));

        // 4. Oversized /api/tags response (> 500,000 chars)
        String hugeTags = "x".repeat(500_001);
        assertTagsFailure(new OllamaTransport.Response(200, hugeTags),
                AIProviderException.Kind.INVALID_RESPONSE, false);
    }

    @Test
    void interpret_SucceedsWithParsedDraftAndTokens() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePost(new OllamaTransport.Response(200, CHAT_INTERPRETATION_JSON));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            ProviderResult<InterpretationDraft> result = provider.interpret(
                    new InterpretationRequest("A ball is projected at 20 m/s at 45 degrees. Find its max height.")).toCompletableFuture().join();

            assertNotNull(result);
            assertEquals("qwen-physics-test:latest", result.model());
            assertEquals(120, result.usage().inputTokens());
            assertEquals(85, result.usage().outputTokens());

            InterpretationDraft draft = result.content();
            assertEquals(1, draft.parts().size());
            assertEquals(ProjectileProblem.Target.MAXIMUM_HEIGHT, draft.parts().get(0).target());
            assertEquals(20.0, draft.parts().get(0).givens().get(ProjectileProblem.Input.INITIAL_SPEED).value());
            assertEquals(45.0, draft.parts().get(0).givens().get(ProjectileProblem.Input.LAUNCH_ANGLE).value());
        }
    }

    @Test
    void interpret_FailsFastWhenModelNotInstalled() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_MISSING_MODEL));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.PROVIDER_FAILURE, ex.kind());
            assertFalse(ex.retryable());
            assertEquals(AIProviderException.Reason.MODEL_UNAVAILABLE, ex.reason());
        }
    }

    @Test
    void interpret_RejectsMalformedJsonResponse() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePost(new OllamaTransport.Response(200, """
                {
                  "model": "qwen-physics-test",
                  "message": {"role": "assistant", "content": "not-valid-json"},
                  "done": true
                }
                """));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.INVALID_RESPONSE, ex.kind());
        }
    }

    @Test
    void explain_SucceedsWithGroundedClaims() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePost(new OllamaTransport.Response(200, CHAT_EXPLANATION_JSON));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            ExplanationRequest request = new ExplanationRequest("A ball is projected at 20 m/s...", sampleSequenceSolution());
            ProviderResult<ExplanationDraft> result = provider.explain(request).toCompletableFuture().join();

            assertNotNull(result);
            assertEquals(1, result.content().sections().size());
            assertEquals("Peak Height Analysis", result.content().sections().get(0).heading());
            assertEquals("maximumHeight", result.content().sections().get(0).claims().get(0).resultKey());
        }
    }

    @Test
    void explain_HandlesHttpTimeoutAsRetryableTimeout() {
        var fakeTransport = new FakeOllamaTransport();
        fakeTransport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        fakeTransport.enqueuePostFailure(new HttpTimeoutException("request timed out"));
        try (var provider = new OllamaProvider(CONFIG, fakeTransport)) {
            AIProviderException aiEx = assertProviderFailure(
                    provider.explain(new ExplanationRequest("test", sampleSequenceSolution())).toCompletableFuture());
            assertEquals(AIProviderException.Kind.TIMEOUT, aiEx.kind());
            assertTrue(aiEx.retryable());
        }
    }

    private static OllamaProvider newTestProvider(FakeOllamaTransport transport, AtomicLong clock) {
        return new OllamaProvider(
                CONFIG,
                transport,
                clock::get,
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Runnable::run);
    }

    private static void assertTagsFailure(OllamaTransport.Response tagsResponse,
                                          AIProviderException.Kind expectedKind,
                                          boolean expectedRetryable) {
        var transport = new FakeOllamaTransport();
        transport.enqueueGet(tagsResponse);
        try (var provider = new OllamaProvider(CONFIG, transport)) {
            assertFalse(provider.isModelAvailable().join());
        }

        var transportForInterpret = new FakeOllamaTransport();
        transportForInterpret.enqueueGet(tagsResponse);
        try (var provider = new OllamaProvider(CONFIG, transportForInterpret)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(expectedKind, ex.kind());
            assertEquals(expectedRetryable, ex.retryable());
            assertEquals(AIProviderException.Reason.GENERAL, ex.reason());
        }
    }

    private static void assertChatFailure(int statusCode,
                                          String body,
                                          AIProviderException.Kind expectedKind,
                                          boolean expectedRetryable,
                                          AIProviderException.Reason expectedReason) {
        var transport = new FakeOllamaTransport();
        transport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        transport.enqueuePost(new OllamaTransport.Response(statusCode, body));
        try (var provider = new OllamaProvider(CONFIG, transport)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(expectedKind, ex.kind());
            assertEquals(expectedRetryable, ex.retryable());
            assertEquals(expectedReason, ex.reason());
        }
    }

    private static void assertChatResponseInvalid(OllamaTransport.Response response) {
        var transport = new FakeOllamaTransport();
        transport.enqueueGet(new OllamaTransport.Response(200, TAGS_JSON_SUCCESS));
        transport.enqueuePost(response);
        try (var provider = new OllamaProvider(CONFIG, transport)) {
            AIProviderException ex = assertProviderFailure(
                    provider.interpret(new InterpretationRequest("test")).toCompletableFuture());
            assertEquals(AIProviderException.Kind.INVALID_RESPONSE, ex.kind());
            assertFalse(ex.retryable());
        }
    }

    private static AIProviderException assertProviderFailure(CompletableFuture<?> future) {
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(AIProviderException.class, ex.getCause());
        return (AIProviderException) ex.getCause();
    }

    private static ProjectileSequenceSolution sampleSequenceSolution() {
        var givens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
        var problem = new ProjectileProblem("maxH", ProjectileProblem.Target.MAXIMUM_HEIGHT, givens);
        var solution = new ProjectileProblemSolver().solve(problem);
        return new ProjectileSequenceSolution(solution.status(), List.of(
                new ProjectileSequenceSolution.PartResult(0, solution)), -1, List.of());
    }

    private static class FakeOllamaTransport implements OllamaTransport {
        private final Queue<Object> getResponses = new ArrayDeque<>();
        private final Queue<Object> postResponses = new ArrayDeque<>();
        protected final AtomicInteger getCallCount = new AtomicInteger();
        private final List<Thread> getThreads = new CopyOnWriteArrayList<>();
        private final List<Thread> postThreads = new CopyOnWriteArrayList<>();

        void enqueueGet(Response response) {
            getResponses.add(response);
        }

        void enqueueGetFailure(Throwable throwable) {
            getResponses.add(throwable);
        }

        void enqueuePost(Response response) {
            postResponses.add(response);
        }

        void enqueuePostFailure(Throwable throwable) {
            postResponses.add(throwable);
        }

        int getCallCount() {
            return getCallCount.get();
        }

        List<Thread> getThreads() {
            return getThreads;
        }

        List<Thread> postThreads() {
            return postThreads;
        }

        @Override
        public CompletableFuture<Response> get(String relativePath, Duration timeout) {
            getCallCount.incrementAndGet();
            getThreads.add(Thread.currentThread());
            Object item = getResponses.poll();
            if (item == null) {
                return CompletableFuture.completedFuture(new Response(404, "Not Found"));
            }
            if (item instanceof Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
            return CompletableFuture.completedFuture((Response) item);
        }

        @Override
        public CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout) {
            postThreads.add(Thread.currentThread());
            Object item = postResponses.poll();
            if (item == null) {
                return CompletableFuture.completedFuture(new Response(404, "Not Found"));
            }
            if (item instanceof Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
            return CompletableFuture.completedFuture((Response) item);
        }
    }
}
