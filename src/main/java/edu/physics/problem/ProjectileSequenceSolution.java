package edu.physics.problem;

import java.util.List;

/** Multipart result that stops before silently choosing an ambiguous dependency branch. */
public record ProjectileSequenceSolution(
        ProjectileSolution.Status status,
        List<PartResult> completedParts,
        int unresolvedPartIndex,
        List<String> warnings) {

    public record PartResult(int index, ProjectileSolution solution) {}

    public ProjectileSequenceSolution {
        completedParts = List.copyOf(completedParts);
        warnings = List.copyOf(warnings);
    }
}
