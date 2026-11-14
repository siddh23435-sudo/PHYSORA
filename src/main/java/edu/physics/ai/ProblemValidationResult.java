package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.ProjectileProblemSequence;

import java.util.List;

/**
 * Authoritative problem validation result carrying both canonical physics specification
 * and completeness verification ledger.
 */
public record ProblemValidationResult(
        Status status,
        CanonicalPhysicsProblem canonicalProblem,
        ConstraintCompletenessValidator.CompletenessReport completenessReport,
        ProjectileProblemSequence normalizedProblem,
        List<InterpretationDraft.Clarification> clarifications,
        List<String> errors,
        List<String> assumptions) {

    public enum Status {
        READY,
        NEEDS_CLARIFICATION,
        UNSUPPORTED,
        INVALID
    }

    public ProblemValidationResult {
        if (status == null) throw new IllegalArgumentException("Validation status is required");
        clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
        errors = errors == null ? List.of() : List.copyOf(errors);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        if (status == Status.READY && canonicalProblem == null && normalizedProblem == null) {
            throw new IllegalArgumentException("Ready validation requires a problem specification");
        }
    }

    /** Legacy five-argument constructor for backwards compatibility with existing callers. */
    public ProblemValidationResult(
            Status status,
            ProjectileProblemSequence normalizedProblem,
            List<InterpretationDraft.Clarification> clarifications,
            List<String> errors,
            List<String> assumptions) {
        this(status, null, null, normalizedProblem, clarifications, errors, assumptions);
    }

    /** Canonical six-argument constructor without legacy sequence. */
    public ProblemValidationResult(
            Status status,
            CanonicalPhysicsProblem canonicalProblem,
            ConstraintCompletenessValidator.CompletenessReport completenessReport,
            List<InterpretationDraft.Clarification> clarifications,
            List<String> errors,
            List<String> assumptions) {
        this(status, canonicalProblem, completenessReport, null, clarifications, errors, assumptions);
    }
}
