package edu.physics.ai;

import edu.physics.ai.local.LocalAIProvider;
import edu.physics.problem.CanonicalSolution;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the Ask AI pipeline exercising LocalAIProvider → ProblemValidator
 * → SolverOrchestrator to verify that natural-language projectile questions produce correct
 * deterministic solver results.
 */
class AskAiReproductionTest {
    private LocalAIProvider provider;
    private ProblemValidator validator;
    private SolverOrchestrator solver;

    @BeforeEach
    void setUp() {
        provider = new LocalAIProvider();
        validator = new ProblemValidator();
        solver = new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier());
    }

    /** Runs the full pipeline and returns the orchestration result, asserting READY validation. */
    private SolverOrchestrator.OrchestrationResult solvePipeline(String question) throws Exception {
        InterpretationRequest req = new InterpretationRequest(question);
        ProviderResult<InterpretationDraft> draftResult = provider.interpret(req).toCompletableFuture().get();
        InterpretationDraft draft = draftResult.content();
        ProblemValidationResult validation = validator.validate(req, draft);
        assertEquals(ProblemValidationResult.Status.READY, validation.status(),
                "Expected READY but got " + validation.status()
                + "; clarifications=" + validation.clarifications()
                + "; errors=" + validation.errors());
        return solver.solve(validation);
    }

    /** Runs the full pipeline and returns the InterpretationDraft for inspection. */
    private InterpretationDraft interpretOnly(String question) throws Exception {
        InterpretationRequest req = new InterpretationRequest(question);
        return provider.interpret(req).toCompletableFuture().get().content();
    }

    @Nested
    @DisplayName("Exact Reproduction Question")
    class ExactReproduction {

        static final String QUESTION = "A ball is kicked from level ground with an initial speed "
                + "of 20 m/s at an angle of 30\u00b0 above the horizontal. Taking gravity (g) as "
                + "10 m/s\u00b2, what is the maximum height the ball reaches?";

        @Test
        @DisplayName("Gravity is extracted from Unicode superscript phrasing")
        void gravityExtracted() throws Exception {
            InterpretationDraft draft = interpretOnly(QUESTION);
            assertTrue(draft.clarifications().isEmpty(),
                    "No clarifications should be needed; got: " + draft.clarifications());
            PhysicalQuantity g = draft.parts().get(0).givens().get(ProjectileProblem.Input.GRAVITY);
            assertNotNull(g, "GRAVITY must be present in givens");
            assertEquals(10.0, g.value(), 1e-9);
            assertEquals(PhysicalUnit.METRE_PER_SECOND_SQUARED, g.unit());
        }

        @Test
        @DisplayName("Full pipeline produces verified maximum height of 5.00 m")
        void fullPipelineMaximumHeight() throws Exception {
            SolverOrchestrator.OrchestrationResult result = solvePipeline(QUESTION);
            assertEquals(SolverOrchestrator.Status.VERIFIED, result.status());
            assertNotNull(result.canonicalSolution());
            assertEquals(CanonicalSolution.OverallStatus.SOLVED, result.canonicalSolution().status());
            assertEquals(1, result.canonicalSolution().partResults().size());

            CanonicalSolution.PartResult part = result.canonicalSolution().partResults().get(0);
            assertEquals(1, part.candidates().size(), "Expected exactly one candidate");
            PhysicalQuantity maxH = part.candidates().get(0).get("maximumHeight");
            assertNotNull(maxH, "maximumHeight must be in candidates");
            assertEquals(5.0, maxH.value(), 0.01, "H_max = (u sin θ)² / 2g = 5.00 m");
            assertEquals(PhysicalUnit.METRE, maxH.unit());
        }

        @Test
        @DisplayName("Inferred initial and landing heights are zero")
        void inferredHeights() throws Exception {
            InterpretationDraft draft = interpretOnly(QUESTION);
            PhysicalQuantity y0 = draft.parts().get(0).givens().get(ProjectileProblem.Input.INITIAL_Y);
            PhysicalQuantity yL = draft.parts().get(0).givens().get(ProjectileProblem.Input.LANDING_Y);
            assertNotNull(y0);
            assertNotNull(yL);
            assertEquals(0.0, y0.value(), 1e-9);
            assertEquals(0.0, yL.value(), 1e-9);
            assertEquals(PhysicalUnit.METRE, y0.unit());
            assertEquals(PhysicalUnit.METRE, yL.unit());
        }
    }

    @Nested
    @DisplayName("Gravity Extraction Variants")
    class GravityVariants {

        @ParameterizedTest(name = "gravity extracted from: \"{0}\"")
        @ValueSource(strings = {
            "speed of 20 m/s at angle 45 degrees. Taking gravity (g) as 10 m/s\u00b2, find the range.",
            "speed of 20 m/s at angle 45 degrees. g = 9.8 m/s^2. What is the range?",
            "speed of 20 m/s at angle 45 degrees. gravity = 10 m/s2. What is the range?",
            "speed of 20 m/s at angle 45 degrees. gravity is 9.81. What is the range?",
            "speed of 20 m/s at angle 45 degrees. Take g = 10 m/s^2. What is the range?",
            "speed of 20 m/s at angle 45 degrees. gravity of 10 m/s^2. Find the range."
        })
        void gravityExtractedFromVariants(String question) throws Exception {
            InterpretationDraft draft = interpretOnly(question);
            assertTrue(draft.clarifications().isEmpty(),
                    "No clarifications expected; got: " + draft.clarifications());
            PhysicalQuantity g = draft.parts().get(0).givens().get(ProjectileProblem.Input.GRAVITY);
            assertNotNull(g, "GRAVITY must be extracted from: " + question);
            assertTrue(g.value() > 0, "Gravity must be positive");
            assertEquals(PhysicalUnit.METRE_PER_SECOND_SQUARED, g.unit());
        }

        @Test
        @DisplayName("Missing gravity triggers clarification")
        void missingGravityTriggersClarification() throws Exception {
            String question = "A ball is thrown at 20 m/s at angle 30 degrees. What is the max height?";
            InterpretationDraft draft = interpretOnly(question);
            assertFalse(draft.clarifications().isEmpty(),
                    "Should request clarification for missing gravity");
            assertTrue(draft.clarifications().get(0).prompt().toLowerCase().contains("gravit"),
                    "Clarification should mention gravity");
        }
    }

    @Nested
    @DisplayName("Tower/Cliff Launch (Non-Zero Initial Height)")
    class TowerLaunch {

        @Test
        @DisplayName("Launch from a 20 m tower produces correct range")
        void towerLaunch() throws Exception {
            String question = "A ball is projected from a tower of 20 m with speed 10 m/s at angle 30 degrees. "
                    + "g = 10 m/s^2. What is the horizontal range?";
            InterpretationDraft draft = interpretOnly(question);
            PhysicalQuantity y0 = draft.parts().get(0).givens().get(ProjectileProblem.Input.INITIAL_Y);
            assertNotNull(y0, "Initial height must be extracted");
            assertEquals(20.0, y0.value(), 1e-9, "Tower height = 20 m");
            assertEquals(PhysicalUnit.METRE, y0.unit());

            // Landing should be at ground level
            PhysicalQuantity yL = draft.parts().get(0).givens().get(ProjectileProblem.Input.LANDING_Y);
            assertNotNull(yL);
            assertEquals(0.0, yL.value(), 1e-9);

            SolverOrchestrator.OrchestrationResult result = solvePipeline(question);
            assertEquals(SolverOrchestrator.Status.VERIFIED, result.status());
        }
    }

    @Nested
    @DisplayName("Target Detection")
    class TargetDetection {

        @Test
        @DisplayName("Range question detected")
        void rangeDetected() throws Exception {
            String question = "speed of 20 m/s at angle 45 degrees, g = 10 m/s^2. What is the horizontal range?";
            InterpretationDraft draft = interpretOnly(question);
            assertEquals(ProjectileProblem.Target.HORIZONTAL_RANGE, draft.parts().get(0).target());
        }

        @Test
        @DisplayName("Flight time detected")
        void flightTimeDetected() throws Exception {
            String question = "speed of 20 m/s at angle 60 degrees, g = 10 m/s^2. What is the time of flight?";
            InterpretationDraft draft = interpretOnly(question);
            assertEquals(ProjectileProblem.Target.FLIGHT_TIME, draft.parts().get(0).target());
        }

        @Test
        @DisplayName("Time to apex detected")
        void timeToApexDetected() throws Exception {
            String question = "speed of 20 m/s at angle 45 degrees, g = 10 m/s^2. What is the time to maximum height?";
            InterpretationDraft draft = interpretOnly(question);
            boolean hasApex = draft.parts().stream()
                    .anyMatch(p -> p.target() == ProjectileProblem.Target.TIME_TO_APEX);
            assertTrue(hasApex, "Should detect TIME_TO_APEX target");
        }
    }

    @Nested
    @DisplayName("Analytical Verification")
    class AnalyticalVerification {

        @Test
        @DisplayName("Range = u^2 sin(2θ) / g for ground-level launch")
        void rangeFromGroundLevel() throws Exception {
            // u=20 m/s, θ=45°, g=10 m/s² → R = 20²×sin(90°)/10 = 40 m
            String question = "speed of 20 m/s at angle 45 degrees, g = 10 m/s^2. What is the range?";
            SolverOrchestrator.OrchestrationResult result = solvePipeline(question);
            assertEquals(SolverOrchestrator.Status.VERIFIED, result.status());
            CanonicalSolution.PartResult part = result.canonicalSolution().partResults().get(0);
            PhysicalQuantity range = part.candidates().get(0).entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase().contains("range"))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            assertNotNull(range, "Range must be in candidates");
            assertEquals(40.0, range.value(), 0.1, "R = u² sin(2θ)/g = 40.0 m");
        }

        @Test
        @DisplayName("Flight time = 2u sin(θ) / g for ground-level launch")
        void flightTimeFromGroundLevel() throws Exception {
            // u=20 m/s, θ=30°, g=10 m/s² → T = 2×20×sin(30°)/10 = 2.0 s
            String question = "speed of 20 m/s at angle 30 degrees, g = 10 m/s^2. What is the flight time?";
            SolverOrchestrator.OrchestrationResult result = solvePipeline(question);
            assertEquals(SolverOrchestrator.Status.VERIFIED, result.status());
            CanonicalSolution.PartResult part = result.canonicalSolution().partResults().get(0);
            PhysicalQuantity flightTime = part.candidates().get(0).entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase().contains("time") || e.getKey().toLowerCase().contains("landing"))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            assertNotNull(flightTime, "Flight time must be in candidates");
            assertEquals(2.0, flightTime.value(), 0.01, "T = 2u sin(θ)/g = 2.0 s");
        }
    }

    @Nested
    @DisplayName("Missing Parameter Clarifications")
    class MissingParameters {

        @Test
        @DisplayName("Missing launch angle triggers clarification")
        void missingAngle() throws Exception {
            // No angle specified — should ask for it or default
            String question = "A ball is thrown at speed 20 m/s. g = 10 m/s^2. What is the max height?";
            InterpretationDraft draft = interpretOnly(question);
            // If angle is not extracted, parts may be empty or clarification needed
            if (draft.parts().isEmpty() || draft.parts().get(0).givens().get(ProjectileProblem.Input.LAUNCH_ANGLE) == null) {
                // This is acceptable — the validator should catch the missing angle
                ProblemValidationResult validation = validator.validate(
                        new InterpretationRequest(question), draft);
                assertNotEquals(ProblemValidationResult.Status.READY, validation.status(),
                        "Should not be READY without launch angle");
            }
        }

        @Test
        @DisplayName("Missing speed triggers non-READY status in validator")
        void missingSpeed() throws Exception {
            String question = "A ball is thrown at angle 45 degrees. g = 10 m/s^2. What is the range?";
            InterpretationDraft draft = interpretOnly(question);
            if (draft.parts().isEmpty() || draft.parts().get(0).givens().get(ProjectileProblem.Input.INITIAL_SPEED) == null) {
                ProblemValidationResult validation = validator.validate(
                        new InterpretationRequest(question), draft);
                assertNotEquals(ProblemValidationResult.Status.READY, validation.status(),
                        "Should not be READY without initial speed");
            }
        }
    }

    @Nested
    @DisplayName("Local Fallback Provider")
    class LocalFallback {

        @Test
        @DisplayName("Local provider completes synchronously without network")
        void localProviderIsInstant() throws Exception {
            String question = "speed 25 m/s at angle 60 degrees, g = 9.8 m/s^2. What is the max height?";
            long start = System.nanoTime();
            InterpretationDraft draft = interpretOnly(question);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertFalse(draft.parts().isEmpty(), "Should parse at least one part");
            assertTrue(elapsedMs < 500, "Local provider should respond in <500ms, took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Full pipeline with local provider produces verified result")
        void localProviderFullPipeline() throws Exception {
            // u=25, θ=60°, g=9.8 → H_max = (25×sin60°)² / (2×9.8) = (21.65)² / 19.6 ≈ 23.915 m
            String question = "speed 25 m/s at angle 60 degrees, g = 9.8 m/s^2. What is the max height?";
            SolverOrchestrator.OrchestrationResult result = solvePipeline(question);
            assertEquals(SolverOrchestrator.Status.VERIFIED, result.status());
            CanonicalSolution.PartResult part = result.canonicalSolution().partResults().get(0);
            PhysicalQuantity maxH = part.candidates().get(0).get("maximumHeight");
            assertNotNull(maxH);
            double expected = Math.pow(25.0 * Math.sin(Math.toRadians(60.0)), 2) / (2 * 9.8);
            assertEquals(expected, maxH.value(), 0.01);
        }
    }
}
