package edu.physics.ai.gemini;

import edu.physics.ai.AIProviderException;
import edu.physics.ai.ExplanationRequest;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProviderResult;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;
import edu.physics.problem.ProjectileSolution;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class GeminiProviderTest {
    private static final GeminiProviderConfiguration CONFIG =
            new GeminiProviderConfiguration("test-model", Duration.ofSeconds(10), 3, 2_000, 1_000);
    private static final String INTERPRETATION_JSON = """
            {"normalizedQuestion":"Find flight time","parts":[{"target":"FLIGHT_TIME","givens":[
              {"input":"INITIAL_SPEED","value":20,"unit":"METRE_PER_SECOND"},
              {"input":"LAUNCH_ANGLE","value":45,"unit":"DEGREE"},
              {"input":"GRAVITY","value":9.81,"unit":"METRE_PER_SECOND_SQUARED"}],"references":[]}],
             "assumptions":["air resistance ignored"],"clarifications":[],"confidence":0.98}
            """;

    @Test
    void interpretationUsesStructuredSchemaAndMapsTypedGivens() {
        var transport = new FakeTransport(CompletableFuture.completedFuture(new GeminiTransport.Response(
                INTERPRETATION_JSON, "provider-req-7", "gemini-model-rev", new ProviderResult.Usage(81, 46))));
        var provider = new GeminiProvider(transport, CONFIG);

        var response = provider.interpret(new InterpretationRequest("A ball at 20 m/s and 45 degrees: flight time?"))
                .toCompletableFuture().join();

        assertEquals("provider-req-7", response.requestId());
        assertEquals("gemini-model-rev", response.model());
        assertEquals(81, response.usage().inputTokens());
        assertEquals(46, response.usage().outputTokens());
        assertEquals(ProjectileProblem.Target.FLIGHT_TIME, response.content().parts().get(0).target());
        assertEquals(20, response.content().parts().get(0).givens()
                .get(ProjectileProblem.Input.INITIAL_SPEED).value());
        assertEquals("test-model", transport.model);
        assertEquals("A ball at 20 m/s and 45 degrees: flight time?", transport.prompt.substring(transport.prompt.indexOf("Question:\n") + 10));
        assertEquals("application/json", transport.schema != null ? "application/json" : "");
        assertTrue(transport.schema.containsKey("required"));
        assertTrue(transport.systemInstruction.contains("Do not solve the physics"));
    }

    @Test
    void ambiguityAndClarificationTurnsArePreserved() {
        String json = """
                {"normalizedQuestion":"distance query","parts":[],"assumptions":[],"clarifications":[
                  {"prompt":"Which distance?","reason":"Distance can have different meanings.","options":["horizontal","along path"]}],"confidence":0.51}
                """;
        var transport = new FakeTransport(CompletableFuture.completedFuture(new GeminiTransport.Response(json, "r", "m", null)));
        var provider = new GeminiProvider(transport, CONFIG);

        var result = provider.interpret(new InterpretationRequest("speed after distance 10m", List.of(
                new InterpretationRequest.ClarificationTurn("Which distance?", "horizontal displacement"))))
                .toCompletableFuture().join();

        assertEquals("horizontal", result.content().clarifications().get(0).options().get(0));
        assertTrue(transport.prompt.contains("A: horizontal displacement"));
    }

    @Test
    void malformedAndUnsupportedStructuredResponsesAreRejected() {
        var malformed = new GeminiProvider(new FakeTransport(CompletableFuture.completedFuture(
                new GeminiTransport.Response("{broken", "r", "m", null))), CONFIG);
        var unsupported = new GeminiProvider(new FakeTransport(CompletableFuture.completedFuture(
                new GeminiTransport.Response(INTERPRETATION_JSON.replace("FLIGHT_TIME", "MADE_UP"), "r", "m", null))), CONFIG);

        assertEquals(AIProviderException.Kind.INVALID_RESPONSE, failureKind(malformed));
        assertEquals(AIProviderException.Kind.INVALID_RESPONSE, failureKind(unsupported));
    }

    @Test
    void providerClassifiesRateLimitTimeoutAndAuthenticationWithoutExposingRawMessage() {
        assertEquals(AIProviderException.Kind.RATE_LIMITED, failureKind(failed("HTTP 429 secret=AIzaSuperSecret")));
        assertEquals(AIProviderException.Kind.AUTHENTICATION, failureKind(failed("HTTP 403 secret=AIzaSuperSecret")));
        assertEquals(AIProviderException.Kind.TIMEOUT, failureKind(failed(new java.util.concurrent.TimeoutException("secret"))));
        try {
            failed("HTTP 403 secret=AIzaSuperSecret").interpret(new InterpretationRequest("q")).toCompletableFuture().join();
            fail("Expected provider failure");
        } catch (CompletionException ex) {
            assertFalse(ex.getCause().getMessage().contains("AIza"));
        }
    }

    @Test
    void cancelingProviderRequestCancelsUnderlyingNetworkFuture() {
        CompletableFuture<GeminiTransport.Response> networkCall = new CompletableFuture<>();
        var provider = new GeminiProvider(new FakeTransport(networkCall), CONFIG);

        var request = provider.interpret(new InterpretationRequest("question")).toCompletableFuture();
        assertTrue(request.cancel(true));

        assertTrue(networkCall.isCancelled());
    }

    @Test
    void explanationPromptContainsOnlyVerifiedSolverDataAndParsesStructuredSections() {
        var givens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
        var problem = new ProjectileProblem("range", ProjectileProblem.Target.HORIZONTAL_RANGE, givens);
        var solution = new ProjectileProblemSolver().solve(problem);
        var sequence = new ProjectileSequenceSolution(solution.status(), List.of(
                new ProjectileSequenceSolution.PartResult(0, solution)), -1, List.of());
        var transport = new FakeTransport(CompletableFuture.completedFuture(new GeminiTransport.Response(
                "{\"sections\":[{\"heading\":\"Principle\",\"claims\":[{\"text\":\"Horizontal speed remains constant.\",\"resultKey\":\"range\"}]}]}",
                "exp-1", "test-model", null)));
        var provider = new GeminiProvider(transport, CONFIG);

        var explanation = provider.explain(new ExplanationRequest("Find horizontal range", sequence))
                .toCompletableFuture().join();

        assertEquals("range", explanation.content().sections().get(0).claims().get(0).resultKey());
        assertTrue(transport.prompt.contains("40.7747") || transport.prompt.contains("40.774719"));
        assertTrue(transport.systemInstruction.contains("Do not recalculate"));
        assertFalse(transport.prompt.contains("API_KEY"));
    }

    @Test
    void configurationReadsOnlyNonSecretSettingsAndFactoryExplainsMissingKeySafely() {
        var config = GeminiProviderConfiguration.fromEnvironment(Map.of(
                "PHYSORA_AI_PROVIDER", "gemini", "PHYSORA_GEMINI_MODEL", "gemini-test",
                "PHYSORA_AI_TIMEOUT_SECONDS", "20", "PHYSORA_AI_MAX_ATTEMPTS", "2"));

        assertEquals("gemini-test", config.model());
        assertEquals(Duration.ofSeconds(20), config.timeout());
        assertEquals(2, config.maxAttempts());
        assertFalse(GeminiProviderConfiguration.apiKeyConfigured(Map.of()));
        assertThrows(AIProviderException.class, () -> GeminiProviderFactory.createFromEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GeminiProviderConfiguration.fromEnvironment(
                Map.of("PHYSORA_AI_MAX_ATTEMPTS", "99")));
    }

    private static AIProviderException.Kind failureKind(GeminiProvider provider) {
        try {
            provider.interpret(new InterpretationRequest("q")).toCompletableFuture().join();
            fail("Expected provider failure");
        } catch (CompletionException ex) {
            return ((AIProviderException) ex.getCause()).kind();
        }
        throw new AssertionError("unreachable");
    }

    private static GeminiProvider failed(Object error) {
        CompletableFuture<GeminiTransport.Response> future = new CompletableFuture<>();
        future.completeExceptionally((Throwable) (error instanceof Throwable ? error : new RuntimeException(error.toString())));
        return new GeminiProvider(new FakeTransport(future), CONFIG);
    }

    private static final class FakeTransport implements GeminiTransport {
        private final CompletableFuture<GeminiTransport.Response> response;
        private String model;
        private String systemInstruction;
        private String prompt;
        private Map<String, Object> schema;

        private FakeTransport(CompletableFuture<GeminiTransport.Response> response) { this.response = response; }

        @Override
        public CompletableFuture<GeminiTransport.Response> generateJson(String model, String systemInstruction,
                String userPrompt, Map<String, Object> jsonSchema, int maxOutputTokens) {
            this.model = model;
            this.systemInstruction = systemInstruction;
            this.prompt = userPrompt;
            this.schema = jsonSchema;
            return response;
        }
    }
}
