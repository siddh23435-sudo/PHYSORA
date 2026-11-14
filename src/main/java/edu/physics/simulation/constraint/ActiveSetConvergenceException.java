package edu.physics.simulation.constraint;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic exception thrown when the unilateral active-set solver encounters non-convergence,
 * cycle trap exhaustion, iteration cap exceeded, or physical residual validation failure.
 *
 * <p>Preserves comprehensive numerical state for diagnostic analysis without masking failures.
 */
public class ActiveSetConvergenceException extends IllegalStateException {

    private final int iterationCount;
    private final List<BitSet> visitedActiveSets;
    private final Map<String, Double> algebraicMultipliers;
    private final Map<String, Double> slackAccelerations;
    private final List<String> offendingConstraintIds;
    private final String failureReason;

    public ActiveSetConvergenceException(
            String message,
            int iterationCount,
            List<BitSet> visitedActiveSets,
            Map<String, Double> algebraicMultipliers,
            Map<String, Double> slackAccelerations,
            List<String> offendingConstraintIds,
            String failureReason) {

        super(message);
        this.iterationCount = iterationCount;
        this.visitedActiveSets = visitedActiveSets == null
                ? List.of()
                : visitedActiveSets.stream().map(b -> (BitSet) b.clone()).toList();
        this.algebraicMultipliers = algebraicMultipliers == null
                ? Map.of()
                : Map.copyOf(algebraicMultipliers);
        this.slackAccelerations = slackAccelerations == null
                ? Map.of()
                : Map.copyOf(slackAccelerations);
        this.offendingConstraintIds = offendingConstraintIds == null
                ? List.of()
                : List.copyOf(offendingConstraintIds);
        this.failureReason = failureReason;
    }

    public int iterationCount() {
        return iterationCount;
    }

    public List<BitSet> visitedActiveSets() {
        return Collections.unmodifiableList(visitedActiveSets);
    }

    public Map<String, Double> algebraicMultipliers() {
        return algebraicMultipliers;
    }

    public Map<String, Double> slackAccelerations() {
        return slackAccelerations;
    }

    public List<String> offendingConstraintIds() {
        return offendingConstraintIds;
    }

    public String failureReason() {
        return failureReason;
    }
}

