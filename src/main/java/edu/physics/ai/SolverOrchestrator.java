package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.CanonicalSolution;
import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSequenceSolution;
import edu.physics.problem.ProjectileSolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        if (validation == null) {
            return new OrchestrationResult(Status.REJECTED, null, null, null, null, null, "validation-required");
        }
        if (validation.status() == ProblemValidationResult.Status.NEEDS_CLARIFICATION) {
            return new OrchestrationResult(Status.NEEDS_CLARIFICATION, validation.canonicalProblem(), null,
                    validation.normalizedProblem(), null, null, "clarification-required");
        }
        if (validation.status() == ProblemValidationResult.Status.UNSUPPORTED) {
            return new OrchestrationResult(Status.REJECTED, validation.canonicalProblem(), null,
                    validation.normalizedProblem(), null, null, "unsupported-physics-constraints");
        }
        if (validation.status() != ProblemValidationResult.Status.READY) {
            return new OrchestrationResult(Status.REJECTED, validation.canonicalProblem(), null,
                    validation.normalizedProblem(), null, null, "interpretation-not-valid");
        }

        // Canonical execution path
        if (validation.canonicalProblem() != null) {
            CanonicalPhysicsProblem canonicalProblem = validation.canonicalProblem();
            CanonicalSolution canonicalSolution = solver.solveCanonical(canonicalProblem);
            SolutionVerifier.Verification verification = verifier.verify(canonicalProblem, canonicalSolution);

            ProjectileSequenceSolution legacySolution = toLegacySequenceSolution(canonicalSolution);

            boolean verified = verification.verified()
                    && (canonicalSolution.status() == CanonicalSolution.OverallStatus.SOLVED
                    || canonicalSolution.status() == CanonicalSolution.OverallStatus.MULTIPLE_SOLUTIONS);

            String messageCode = verified ? "" : determineMessageCode(canonicalSolution, verification);
            Status status = verified ? Status.VERIFIED : Status.REJECTED;

            return new OrchestrationResult(
                    status,
                    canonicalProblem,
                    canonicalSolution,
                    validation.normalizedProblem(),
                    legacySolution,
                    verification,
                    messageCode);
        }

        // Legacy fallback path when canonical problem is not available
        ProjectileProblemSequence problem = validation.normalizedProblem();
        if (problem == null) {
            return new OrchestrationResult(Status.REJECTED, null, null, null, null, null, "problem-not-available");
        }
        ProjectileSequenceSolution solution = solver.solveSequence(problem);
        SolutionVerifier.Verification verification = verifier.verify(problem, solution);
        return verification.verified()
                ? new OrchestrationResult(Status.VERIFIED, null, null, problem, solution, verification, "")
                : new OrchestrationResult(Status.REJECTED, null, null, problem, solution, verification,
                        "solver-output-failed-verification");
    }

    private static String determineMessageCode(CanonicalSolution solution, SolutionVerifier.Verification verification) {
        if (solution != null && solution.status() != CanonicalSolution.OverallStatus.SOLVED
                && solution.status() != CanonicalSolution.OverallStatus.MULTIPLE_SOLUTIONS) {
            return switch (solution.status()) {
                case PARTIALLY_SOLVED -> "solver-output-partially-solved";
                case UNDERDETERMINED -> "solver-output-underdetermined";
                case NO_SOLUTION -> "solver-output-no-solution";
                case UNBOUNDED -> "solver-output-unbounded";
                case AMBIGUOUS -> "solver-output-ambiguous";
                case INVALID_INPUT -> "solver-output-invalid-input";
                case UNSUPPORTED -> "solver-output-unsupported";
                default -> "solver-output-" + solution.status().name().toLowerCase().replace('_', '-');
            };
        }
        if (verification != null && !verification.verified()) {
            return "solver-output-failed-verification";
        }
        if (solution != null && solution.status() == CanonicalSolution.OverallStatus.MULTIPLE_SOLUTIONS) {
            return "solver-output-multiple-solutions";
        }
        return "solver-output-verified";
    }

    /**
     * Read-only compatibility adapter converting CanonicalSolution to ProjectileSequenceSolution
     * for standard targets only. Returns null if any part uses surface targets to avoid altering semantics.
     */
    public static ProjectileSequenceSolution toLegacySequenceSolution(CanonicalSolution canonical) {
        if (canonical == null) return null;
        for (CanonicalSolution.PartResult part : canonical.partResults()) {
            if (!(part.target() instanceof CanonicalPhysicsProblem.StandardTarget)) {
                return null;
            }
        }
        List<ProjectileSequenceSolution.PartResult> parts = new ArrayList<>();
        for (CanonicalSolution.PartResult part : canonical.partResults()) {
            CanonicalPhysicsProblem.StandardTarget std = (CanonicalPhysicsProblem.StandardTarget) part.target();
            ProjectileSolution.Status partStatus = switch (part.status()) {
                case SOLVED -> ProjectileSolution.Status.SOLVED;
                case MULTIPLE_SOLUTIONS -> ProjectileSolution.Status.MULTIPLE_SOLUTIONS;
                case NO_SOLUTION -> ProjectileSolution.Status.NO_SOLUTION;
                case UNDERDETERMINED -> ProjectileSolution.Status.UNDERDETERMINED;
                case AMBIGUOUS -> ProjectileSolution.Status.AMBIGUOUS;
                case UNBOUNDED -> ProjectileSolution.Status.UNBOUNDED;
                case INVALID_INPUT, UNSUPPORTED, DEPENDENCY_UNAVAILABLE -> ProjectileSolution.Status.INVALID_INPUT;
            };
            ProjectileSolution sol = new ProjectileSolution(
                    partStatus,
                    std.target(),
                    canonical.statement(),
                    Map.of(),
                    part.requiredQuantities(),
                    canonical.coordinateConvention(),
                    part.candidates(),
                    part.steps(),
                    List.of(),
                    part.assumptions(),
                    part.diagnostics());
            parts.add(new ProjectileSequenceSolution.PartResult(part.partIndex(), sol));
        }
        ProjectileSolution.Status overallStatus = switch (canonical.status()) {
            case SOLVED -> ProjectileSolution.Status.SOLVED;
            case MULTIPLE_SOLUTIONS -> ProjectileSolution.Status.MULTIPLE_SOLUTIONS;
            case NO_SOLUTION -> ProjectileSolution.Status.NO_SOLUTION;
            case UNDERDETERMINED -> ProjectileSolution.Status.UNDERDETERMINED;
            case AMBIGUOUS -> ProjectileSolution.Status.AMBIGUOUS;
            case UNBOUNDED -> ProjectileSolution.Status.UNBOUNDED;
            default -> ProjectileSolution.Status.INVALID_INPUT;
        };
        return new ProjectileSequenceSolution(overallStatus, parts, -1, canonical.diagnostics());
    }

    public enum Status { VERIFIED, NEEDS_CLARIFICATION, REJECTED }

    public record OrchestrationResult(
            Status status,
            CanonicalPhysicsProblem canonicalProblem,
            CanonicalSolution canonicalSolution,
            ProjectileProblemSequence problem,
            ProjectileSequenceSolution solution,
            SolutionVerifier.Verification verification,
            String messageCode) {

        public OrchestrationResult(Status status,
                                   ProjectileProblemSequence problem,
                                   ProjectileSequenceSolution solution,
                                   SolutionVerifier.Verification verification,
                                   String messageCode) {
            this(status, null, null, problem, solution, verification, messageCode);
        }
    }
}
