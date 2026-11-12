package edu.physics.ai;

import edu.physics.problem.ProjectileSolution;
import edu.physics.problem.ProjectileSequenceSolution;

/** Explanation input is limited to the original question and deterministic solver output. */
public record ExplanationRequest(String originalQuestion, ProjectileSequenceSolution verifiedSolution) {
    public ExplanationRequest {
        if (originalQuestion == null || originalQuestion.isBlank()) throw new IllegalArgumentException("Original question is required");
        if (verifiedSolution == null) throw new IllegalArgumentException("Verified solver output is required");
        if (verifiedSolution.status() != ProjectileSolution.Status.SOLVED
                && verifiedSolution.status() != ProjectileSolution.Status.MULTIPLE_SOLUTIONS) {
            throw new IllegalArgumentException("Only solved results can be explained");
        }
    }
}
