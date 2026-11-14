package edu.physics.simulation.constraint;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deeply immutable result of a unilateral active-set constraint solve.
 *
 * @param converged whether the solve achieved complementarity and verified physical residuals
 * @param iterations number of active-set iterations performed
 * @param generalizedAccelerations public-output acceleration vector a_public = a_alg + delta_a
 * @param algebraicAccelerations raw algebraic acceleration vector a_alg = M^-1(f_ext + J^T lambda_alg)
 * @param cableStates physical regime for each cable (keyed by constraintId)
 * @param cableTensions public non-negative tensile forces (clamped: max(0.0, lambda_alg))
 * @param algebraicMultipliers raw algebraic multipliers lambda_alg (for diagnostics and tolerance verification)
 * @param slackAccelerations kinematic slack accelerations evaluated under a_public
 * @param bilateralMultipliers multipliers for bilateral equality constraints
 * @param activeSetBitSet bitset of active cable indices in canonical order
 * @param maxComplementaritySlack max(lambda_public_i * |w_public_i|) across all cables
 */
public record UnilateralSolveResult(
        boolean converged,
        int iterations,
        double[] generalizedAccelerations,
        double[] algebraicAccelerations,
        Map<String, CableState> cableStates,
        Map<String, Double> cableTensions,
        Map<String, Double> algebraicMultipliers,
        Map<String, Double> slackAccelerations,
        Map<String, double[]> bilateralMultipliers,
        BitSet activeSetBitSet,
        double maxComplementaritySlack) {

    public UnilateralSolveResult {
        Objects.requireNonNull(generalizedAccelerations, "generalizedAccelerations is required");
        Objects.requireNonNull(algebraicAccelerations, "algebraicAccelerations is required");
        Objects.requireNonNull(cableStates, "cableStates is required");
        Objects.requireNonNull(cableTensions, "cableTensions is required");
        Objects.requireNonNull(algebraicMultipliers, "algebraicMultipliers is required");
        Objects.requireNonNull(slackAccelerations, "slackAccelerations is required");
        Objects.requireNonNull(bilateralMultipliers, "bilateralMultipliers is required");
        Objects.requireNonNull(activeSetBitSet, "activeSetBitSet is required");

        // Deep defensive copies on construction
        generalizedAccelerations = generalizedAccelerations.clone();
        algebraicAccelerations = algebraicAccelerations.clone();
        cableStates = Map.copyOf(cableStates);
        cableTensions = Map.copyOf(cableTensions);
        algebraicMultipliers = Map.copyOf(algebraicMultipliers);
        slackAccelerations = Map.copyOf(slackAccelerations);
        activeSetBitSet = (BitSet) activeSetBitSet.clone();

        Map<String, double[]> bilateralCopy = new HashMap<>(bilateralMultipliers.size());
        for (Map.Entry<String, double[]> entry : bilateralMultipliers.entrySet()) {
            bilateralCopy.put(entry.getKey(), entry.getValue().clone());
        }
        bilateralMultipliers = Collections.unmodifiableMap(bilateralCopy);
    }

    // Defensive copies on access to guarantee deep immutability against caller mutation

    @Override
    public double[] generalizedAccelerations() {
        return generalizedAccelerations.clone();
    }

    @Override
    public double[] algebraicAccelerations() {
        return algebraicAccelerations.clone();
    }

    @Override
    public BitSet activeSetBitSet() {
        return (BitSet) activeSetBitSet.clone();
    }

    @Override
    public Map<String, double[]> bilateralMultipliers() {
        Map<String, double[]> deepCopy = new HashMap<>(bilateralMultipliers.size());
        for (Map.Entry<String, double[]> entry : bilateralMultipliers.entrySet()) {
            deepCopy.put(entry.getKey(), entry.getValue().clone());
        }
        return Collections.unmodifiableMap(deepCopy);
    }

    public double[] bilateralMultiplier(String constraintId) {
        double[] vals = bilateralMultipliers.get(constraintId);
        return vals == null ? null : vals.clone();
    }
}

