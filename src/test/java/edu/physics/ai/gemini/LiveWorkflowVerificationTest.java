package edu.physics.ai.gemini;

import edu.physics.ai.AIProblemWorkflow;
import edu.physics.ai.AIProvider;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.AIWorkflowResult;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.ProviderResult;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.problem.ProjectileProblemSolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LiveWorkflowVerificationTest {
    @Test
    void liveWorkflowSolvesStandardProjectileMotion() {
        boolean liveExplicitlyRequested = Boolean.getBoolean("physora.live.gemini");
        AIProvider provider;

        if (liveExplicitlyRequested) {
            String key = System.getenv("GOOGLE_API_KEY");
            Assumptions.assumeTrue(key != null && !key.isBlank(), "GOOGLE_API_KEY must be set for live integration test");
            try {
                provider = GeminiProviderFactory.createFromEnvironment();
            } catch (Exception ex) {
                Assumptions.abort("Gemini provider creation failed: " + ex.getMessage());
                return;
            }
        } else {
            // Hermetic mock provider for automated regression suite: zero network calls, zero quota consumed
            provider = createHermeticMockProvider();
        }

        AIProblemWorkflow workflow = new AIProblemWorkflow(
                provider,
                new ProblemValidator(),
                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                new ExplanationVerifier()
        );

        AIWorkflowResult result;
        try {
            result = workflow.submit(new InterpretationRequest(
                    "A ball is projected at 20 m/s at 45 degrees with g = 9.81 m/s^2. Find horizontal range."
            )).toCompletableFuture().join();
        } catch (Exception ex) {
            if (liveExplicitlyRequested) {
                Assumptions.abort("Live network call not reachable from environment: " + ex.getMessage());
                return;
            }
            throw ex;
        }

        if (result.status() == AIWorkflowResult.Status.PROVIDER_ERROR
                && result.providerError() == AIProviderException.Kind.NETWORK) {
            Assumptions.abort("Live network unreachable: " + result.detailMessage());
            return;
        }

        if (result.explanation() != null) {
            System.out.println("[LIVE VERIFICATION] Sections count: " + result.explanation().content().sections().size());
            for (var section : result.explanation().content().sections()) {
                System.out.println("  Section: " + section.heading());
                for (var claim : section.claims()) {
                    System.out.println("    Claim: \"" + claim.text() + "\" | resultKey: " + claim.resultKey());
                }
            }
        }
        if (result.explanationVerification() != null) {
            System.out.println("[LIVE VERIFICATION] Issues: " + result.explanationVerification().issues());
        }

        assertNotNull(result);
        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status(),
                "Expected COMPLETE status, but got " + result.status() + " with issues: "
                + (result.explanationVerification() != null ? result.explanationVerification().issues() : result.detailMessage()));
        assertNotNull(result.orchestration());
        assertNotNull(result.orchestration().solution());
        assertFalse(result.orchestration().solution().completedParts().isEmpty());
        System.out.println("[LIVE VERIFICATION] Success! Result status: " + result.status());
    }

    private static AIProvider createHermeticMockProvider() {
        String interpretationJson = """
                {"normalizedQuestion":"A ball is projected at 20 m/s at 45 degrees with g = 9.81 m/s^2. Find horizontal range.",
                 "parts":[{"target":"HORIZONTAL_RANGE","givens":[
                   {"input":"INITIAL_SPEED","value":20.0,"unit":"METRE_PER_SECOND"},
                   {"input":"LAUNCH_ANGLE","value":45.0,"unit":"DEGREE"},
                   {"input":"GRAVITY","value":9.81,"unit":"METRE_PER_SECOND_SQUARED"}],"references":[]}],
                 "assumptions":["no-air-resistance"],"clarifications":[],"confidence":0.99}
                """;
        String explanationJson = """
                {"sections":[
                  {"heading":"Physical Principles and Coordinate Setup","claims":[
                    {"text":"The trajectory follows standard two-dimensional kinematics under uniform gravitational acceleration.","resultKey":null},
                    {"text":"Air resistance is neglected and coordinates are oriented with positive y vertical and positive x horizontal.","resultKey":null}]},
                  {"heading":"Analytical Kinematic Derivation","claims":[
                    {"text":"The analytical calculation yields the verified result for this projectile target:","resultKey":"range"}]}]}
                """;

        GeminiTransport mockTransport = (model, systemInstruction, userPrompt, jsonSchema, maxOutputTokens) -> {
            if (systemInstruction != null && systemInstruction.contains("recalculate")) {
                return CompletableFuture.completedFuture(new GeminiTransport.Response(
                        explanationJson, "mock-exp-req-1", "gemini-3.1-flash-lite", new ProviderResult.Usage(110, 65)));
            }
            return CompletableFuture.completedFuture(new GeminiTransport.Response(
                    interpretationJson, "mock-int-req-1", "gemini-3.1-flash-lite", new ProviderResult.Usage(95, 48)));
        };

        GeminiProviderConfiguration config = new GeminiProviderConfiguration(
                "gemini-3.1-flash-lite", Duration.ofSeconds(10), 3, 2000, 1000);
        return new GeminiProvider(mockTransport, config);
    }
}

