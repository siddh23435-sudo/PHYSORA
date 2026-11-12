package edu.physics.ai;

import edu.physics.problem.ProjectileProblemSequence;

import java.util.List;

public record ProblemValidationResult(Status status,
                                      ProjectileProblemSequence normalizedProblem,
                                      List<InterpretationDraft.Clarification> clarifications,
                                      List<String> errors,
                                      List<String> assumptions) {
    public enum Status { READY, NEEDS_CLARIFICATION, INVALID }

    public ProblemValidationResult {
        if (status == null) throw new IllegalArgumentException("Validation status is required");
        clarifications = List.copyOf(clarifications);
        errors = List.copyOf(errors);
        assumptions = List.copyOf(assumptions);
        if (status == Status.READY && normalizedProblem == null) {
            throw new IllegalArgumentException("Ready validation requires a normalized problem");
        }
    }
}
