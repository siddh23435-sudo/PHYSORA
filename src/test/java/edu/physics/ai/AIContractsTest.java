package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AIContractsTest {
    @Test
    void interpretationDraftKeepsTypedInputsImmutable() {
        var givens = new java.util.EnumMap<ProjectileProblem.Input, PhysicalQuantity>(ProjectileProblem.Input.class);
        givens.put(ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND));
        var part = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, givens);
        givens.clear();

        assertEquals(20, part.givens().get(ProjectileProblem.Input.INITIAL_SPEED).value());
        assertThrows(UnsupportedOperationException.class, () -> part.givens().clear());
    }

    @Test
    void interpretationRequestPreservesClarificationTurns() {
        var request = new InterpretationRequest("Find the speed 10 m from launch", List.of(
                new InterpretationRequest.ClarificationTurn("What does distance mean?", "Horizontal displacement")));

        assertEquals("Horizontal displacement", request.clarifications().get(0).answer());
        assertThrows(UnsupportedOperationException.class, () -> request.clarifications().clear());
    }

    @Test
    void explanationCannotBeRequestedForAnUnsolvedResult() {
        var invalid = new ProjectileProblemSolver().solve(new ProjectileProblem("missing data",
                ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of()));

        var sequence = new ProjectileSequenceSolution(invalid.status(),
                java.util.List.of(new ProjectileSequenceSolution.PartResult(0, invalid)), 0, java.util.List.of());
        assertThrows(IllegalArgumentException.class, () -> new ExplanationRequest("missing data", sequence));
    }

    @Test
    void explanationRequestRequiresARealDeterministicSolution() {
        var solved = new ProjectileProblemSolver().solve(new ProjectileProblem("range",
                ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))));

        assertDoesNotThrow(() -> new ExplanationRequest("range", new ProjectileSequenceSolution(solved.status(),
                java.util.List.of(new ProjectileSequenceSolution.PartResult(0, solved)), -1, java.util.List.of())));
    }

    @Test
    void providerFailureExposesRetryClassificationWithoutSecretDetails() {
        var error = new AIProviderException(AIProviderException.Kind.RATE_LIMITED, true, "Provider request was rate limited");

        assertEquals(AIProviderException.Kind.RATE_LIMITED, error.kind());
        assertTrue(error.retryable());
        assertFalse(error.getMessage().contains("sk-"));
    }
}
