package edu.physics.ai;

import edu.physics.problem.CanonicalSolution;
import edu.physics.problem.ProjectileSolution;
import edu.physics.problem.ProjectileSequenceSolution;

/** Explanation input is limited to the original question and deterministic solver output. */
public record ExplanationRequest(
        String originalQuestion,
        CanonicalSolution canonicalSolution,
        ProjectileSequenceSolution verifiedSolution) {

    public ExplanationRequest {
        if (originalQuestion == null || originalQuestion.isBlank()) {
            throw new IllegalArgumentException("Original question is required");
        }
        if (canonicalSolution == null && verifiedSolution == null) {
            throw new IllegalArgumentException("Verified solver output is required");
        }
        if (canonicalSolution != null) {
            if (canonicalSolution.status() != CanonicalSolution.OverallStatus.SOLVED) {
                throw new IllegalArgumentException("Only solved canonical results can be explained");
            }
            for (CanonicalSolution.PartResult part : canonicalSolution.partResults()) {
                if (part.status() != CanonicalSolution.PartStatus.SOLVED || part.candidates().size() != 1) {
                    throw new IllegalArgumentException("All canonical parts must be uniquely solved");
                }
            }
        }
        if (canonicalSolution == null && verifiedSolution != null) {
            if (verifiedSolution.status() != ProjectileSolution.Status.SOLVED
                    && verifiedSolution.status() != ProjectileSolution.Status.MULTIPLE_SOLUTIONS) {
                throw new IllegalArgumentException("Only solved results can be explained");
            }
        }
    }

    public ExplanationRequest(String originalQuestion, CanonicalSolution canonicalSolution) {
        this(originalQuestion, canonicalSolution, null);
    }

    public ExplanationRequest(String originalQuestion, ProjectileSequenceSolution verifiedSolution) {
        this(originalQuestion, null, verifiedSolution);
    }
}
