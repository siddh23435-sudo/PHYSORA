package edu.physics.problem;

import java.util.List;
import java.util.Map;

/** Structured solver output intended for later UI and explanation rendering. */
public record ProjectileSolution(
        Status status,
        ProjectileProblem.Target target,
        List<Map<String, PhysicalQuantity>> candidates,
        List<Step> steps,
        List<ProjectileProblem.Input> missingInputs,
        List<String> assumptions,
        List<String> warnings) {

    public enum Status { SOLVED, MULTIPLE_SOLUTIONS, NO_SOLUTION, UNDERDETERMINED, AMBIGUOUS, UNBOUNDED, INVALID_INPUT }

    public record Step(String equationId, String equation,
                       Map<String, Double> substitutions,
                       Map<String, PhysicalQuantity> results,
                       String interpretationId) {
        public Step {
            substitutions = Map.copyOf(substitutions);
            results = Map.copyOf(results);
        }
    }

    public ProjectileSolution {
        candidates = candidates.stream().map(Map::copyOf).toList();
        steps = List.copyOf(steps);
        missingInputs = List.copyOf(missingInputs);
        assumptions = List.copyOf(assumptions);
        warnings = List.copyOf(warnings);
    }
}
