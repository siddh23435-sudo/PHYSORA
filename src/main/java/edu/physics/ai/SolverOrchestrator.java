package edu.physics.ai;

import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;

/** Passes validated requests to the Phase 1 solver and rejects incomplete or inconsistent output. */
public final class SolverOrchestrator {
    private final ProjectileProblemSolver solver;
    private final SolutionVerifier verifier;

    public SolverOrchestrator(ProjectileProblemSolver solver, SolutionVerifier verifier) {
        if (solver == null || verifier == null) throw new IllegalArgumentException("Solver and verifier are required");
        this.solver = solver;
        this.verifier = verifier;
    }

    public OrchestrationResult solve(ProblemValidationResult validation) {
        if (validation == null) return new OrchestrationResult(Status.REJECTED, null, null, null, "validation-required");
        if (validation.status() == ProblemValidationResult.Status.NEEDS_CLARIFICATION) {
            return new OrchestrationResult(Status.NEEDS_CLARIFICATION, validation.normalizedProblem(), null, null, "clarification-required");
        }
        if (validation.status() != ProblemValidationResult.Status.READY) {
            return new OrchestrationResult(Status.REJECTED, null, null, null, "interpretation-not-valid");
        }
        ProjectileProblemSequence problem = validation.normalizedProblem();
        ProjectileSequenceSolution solution = solver.solveSequence(problem);
        SolutionVerifier.Verification verification = verifier.verify(problem, solution);
        return verification.verified()
                ? new OrchestrationResult(Status.VERIFIED, problem, solution, verification, "")
                : new OrchestrationResult(Status.REJECTED, problem, solution, verification,
                        "solver-output-failed-verification");
    }

    public enum Status { VERIFIED, NEEDS_CLARIFICATION, REJECTED }

    public record OrchestrationResult(Status status,
                                      ProjectileProblemSequence problem,
                                      ProjectileSequenceSolution solution,
                                      SolutionVerifier.Verification verification,
                                      String messageCode) {}
}
