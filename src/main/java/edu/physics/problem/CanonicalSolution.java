package edu.physics.problem;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic canonical solution retaining independent per-target results and statuses
 * without discarding valid answers when a sibling target cannot be evaluated.
 */
public record CanonicalSolution(
        OverallStatus status,
        String statement,
        String coordinateConvention,
        List<PartResult> partResults,
        List<String> unsupportedConstraints,
        List<String> assumptions,
        List<String> diagnostics) {

    public enum OverallStatus {
        SOLVED,
        MULTIPLE_SOLUTIONS,
        PARTIALLY_SOLVED,
        NO_SOLUTION,
        UNBOUNDED,
        UNDERDETERMINED,
        AMBIGUOUS,
        INVALID_INPUT,
        UNSUPPORTED,
        NO_UNIFIED_SOLUTION
    }

    public enum PartStatus {
        SOLVED,
        MULTIPLE_SOLUTIONS,
        NO_SOLUTION,
        UNBOUNDED,
        UNDERDETERMINED,
        AMBIGUOUS,
        INVALID_INPUT,
        UNSUPPORTED,
        DEPENDENCY_UNAVAILABLE
    }

    public enum ContactClassification {
        FINITE_FLIGHT,
        IMMEDIATE_CONTACT,
        NO_LANDING,
        NOT_APPLICABLE
    }

    public record PartResult(
            int partIndex,
            CanonicalPhysicsProblem.CanonicalTarget target,
            PartStatus status,
            ContactClassification contactClassification,
            List<String> requiredQuantities,
            List<Map<String, PhysicalQuantity>> candidates,
            List<ProjectileSolution.Step> steps,
            List<String> missingInputs,
            List<String> assumptions,
            List<String> diagnostics) {

        public PartResult {
            if (partIndex < 0) throw new IllegalArgumentException("Part index must be non-negative");
            Objects.requireNonNull(target, "Canonical target is required");
            Objects.requireNonNull(status, "Part status is required");
            Objects.requireNonNull(contactClassification, "Contact classification is required");
            requiredQuantities = List.copyOf(requiredQuantities);
            candidates = candidates.stream().map(Map::copyOf).toList();
            steps = List.copyOf(steps);
            missingInputs = List.copyOf(missingInputs);
            assumptions = List.copyOf(assumptions);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean isSolved() {
            return status == PartStatus.SOLVED || status == PartStatus.MULTIPLE_SOLUTIONS;
        }
    }

    public CanonicalSolution {
        Objects.requireNonNull(status, "Overall status is required");
        if (statement == null) statement = "";
        if (coordinateConvention == null) coordinateConvention = "";
        partResults = List.copyOf(partResults);
        unsupportedConstraints = List.copyOf(unsupportedConstraints);
        assumptions = List.copyOf(assumptions);
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Returns the first {@link PartResult} matching {@code target}. If multiple parts request the
     * same target with different parameters (such as different query times), iterate {@link #partResults()} directly.
     */
    public Optional<PartResult> resultFor(CanonicalPhysicsProblem.CanonicalTarget target) {
        return partResults.stream().filter(part -> part.target().equals(target)).findFirst();
    }

    public List<PartResult> solvedParts() {
        return partResults.stream().filter(PartResult::isSolved).toList();
    }

    /** Deterministically derives problem-level status from evaluated per-target results. */
    public static OverallStatus deriveOverallStatus(List<PartResult> results,
                                                    boolean hasUnsupported,
                                                    boolean hasInvalidProblem) {
        if (hasUnsupported) {
            return OverallStatus.UNSUPPORTED;
        }
        if (hasInvalidProblem || results.isEmpty()) {
            return OverallStatus.INVALID_INPUT;
        }
        boolean hasInvalidPart = results.stream().anyMatch(part -> part.status() == PartStatus.INVALID_INPUT);
        if (hasInvalidPart) {
            return OverallStatus.INVALID_INPUT;
        }

        long solvedCount = results.stream().filter(PartResult::isSolved).count();
        boolean hasMultiple = results.stream().anyMatch(part -> part.status() == PartStatus.MULTIPLE_SOLUTIONS);

        if (solvedCount == results.size()) {
            return hasMultiple ? OverallStatus.MULTIPLE_SOLUTIONS : OverallStatus.SOLVED;
        }
        if (solvedCount > 0) {
            return OverallStatus.PARTIALLY_SOLVED;
        }

        EnumSet<PartStatus> distinct = EnumSet.noneOf(PartStatus.class);
        for (PartResult result : results) {
            distinct.add(result.status());
        }
        if (distinct.size() == 1) {
            PartStatus only = distinct.iterator().next();
            return switch (only) {
                case NO_SOLUTION -> OverallStatus.NO_SOLUTION;
                case UNBOUNDED -> OverallStatus.UNBOUNDED;
                case UNDERDETERMINED -> OverallStatus.UNDERDETERMINED;
                case AMBIGUOUS -> OverallStatus.AMBIGUOUS;
                case INVALID_INPUT -> OverallStatus.INVALID_INPUT;
                case UNSUPPORTED -> OverallStatus.UNSUPPORTED;
                case DEPENDENCY_UNAVAILABLE -> OverallStatus.NO_UNIFIED_SOLUTION;
                case SOLVED, MULTIPLE_SOLUTIONS -> OverallStatus.SOLVED;
            };
        }
        if (distinct.contains(PartStatus.INVALID_INPUT)) {
            return OverallStatus.INVALID_INPUT;
        }
        if (distinct.contains(PartStatus.UNSUPPORTED)) {
            return OverallStatus.UNSUPPORTED;
        }
        return OverallStatus.NO_UNIFIED_SOLUTION;
    }
}

