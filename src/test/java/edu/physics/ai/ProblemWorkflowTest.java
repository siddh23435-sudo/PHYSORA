package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSolution;
import edu.physics.problem.ProjectileSequenceSolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProblemWorkflowTest {
    private final ProblemValidator validator = new ProblemValidator();
    private final SolverOrchestrator orchestrator = new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier());

    @Test
    void validatedNaturalLanguageInterpretationFlowsThroughDeterministicSolver() {
        var draft = new InterpretationDraft("Find range and height", List.of(
                new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, givens()),
                new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, givens())),
                List.of("air resistance ignored"), List.of(), 0.98);

        var validation = validator.validate("A ball is projected at 20 m/s at 45 degrees; find range and maximum height.", draft);
        var result = orchestrator.solve(validation);

        assertEquals(ProblemValidationResult.Status.READY, validation.status());
        assertEquals(SolverOrchestrator.Status.VERIFIED, result.status(), String.valueOf(result.verification()));
        assertEquals(2, result.solution().completedParts().size());
        assertTrue(result.verification().verified());
        assertEquals(400.0 / 9.81, result.solution().completedParts().get(0).solution().candidates().get(0)
                .get("range").value(), 1e-8);
    }

    @Test
    void multipleValidRootsArePreservedAndCanBeVerified() {
        var input = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(25, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(40, PhysicalUnit.METRE),
                ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(0, PhysicalUnit.METRE));
        var draft = new InterpretationDraft("Find launch angles", List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FIND_LAUNCH_ANGLE, input)), List.of("same launch and landing height"), List.of(), 0.9);

        var result = orchestrator.solve(validator.validate("Find possible launch angles", draft));

        assertEquals(SolverOrchestrator.Status.VERIFIED, result.status(), String.valueOf(result.verification()));
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, result.solution().status());
        assertEquals(2, result.solution().completedParts().get(0).solution().candidates().size());
    }

    @Test
    void verifierRejectsCandidateThatDisagreesWithItsDerivation() {
        var input = new ProjectileProblem("range", ProjectileProblem.Target.HORIZONTAL_RANGE, givens());
        var valid = new ProjectileProblemSolver().solve(input);
        var candidate = Map.of("range", new PhysicalQuantity(999, PhysicalUnit.METRE));
        var tampered = new ProjectileSolution(valid.status(), valid.target(), valid.problemStatement(), valid.givens(),
                valid.requiredQuantities(), valid.coordinateConvention(), List.of(candidate), valid.steps(),
                valid.missingInputs(), valid.assumptions(), valid.warnings());
        var sequence = new ProjectileSequenceSolution(ProjectileSolution.Status.SOLVED,
                List.of(new ProjectileSequenceSolution.PartResult(0, tampered)), -1, List.of());

        var verification = new SolutionVerifier().verify(
                new edu.physics.problem.ProjectileProblemSequence("range", Map.of(), List.of(
                        new edu.physics.problem.ProjectileProblemSequence.Part(input.target(), input.givens(), Map.of()))),
                sequence);

        assertFalse(verification.verified());
        assertTrue(verification.issues().stream().anyMatch(issue -> issue.startsWith("candidate-step-value-mismatch")));
    }

    @Test
    void orchestratorReturnsClarificationWithoutCallingTheSolver() {
        var draft = new InterpretationDraft("ambiguous distance", List.of(), List.of(), List.of(
                new InterpretationDraft.Clarification("Which distance?", "The wording is ambiguous.",
                        List.of("horizontal displacement", "path length"))), 0.8);
        var validation = validator.validate("Find speed at distance 10 m", draft);

        var result = orchestrator.solve(validation);

        assertEquals(SolverOrchestrator.Status.NEEDS_CLARIFICATION, result.status());
        assertNull(result.solution());
    }

    private static Map<ProjectileProblem.Input, PhysicalQuantity> givens() {
        return Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
    }
}
