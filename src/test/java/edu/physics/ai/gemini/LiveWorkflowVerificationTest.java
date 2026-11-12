package edu.physics.ai.gemini;

import edu.physics.ai.AIProblemWorkflow;
import edu.physics.ai.AIProvider;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.AIWorkflowResult;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.problem.ProjectileProblemSolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveWorkflowVerificationTest {
    @Test
    void liveWorkflowSolvesStandardProjectileMotion() {
        String key = System.getenv("GOOGLE_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "GOOGLE_API_KEY must be set");

        AIProvider provider;
        try {
            provider = GeminiProviderFactory.createFromEnvironment();
        } catch (Exception ex) {
            Assumptions.abort("Gemini provider creation failed: " + ex.getMessage());
            return;
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
            Assumptions.abort("Live network call not reachable from environment: " + ex.getMessage());
            return;
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
}

