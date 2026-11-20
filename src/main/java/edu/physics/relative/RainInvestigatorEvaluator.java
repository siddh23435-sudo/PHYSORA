package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Domain evaluation and validation layer for two-observation inverse rain velocity reconstruction.
 *
 * <p>Coordinates input validation, strict angle-domain boundary filtering, scale-aware numerical
 * degeneracy checks, downward velocity consistency, conditioning analysis, and authoritative
 * invocation of {@link RainManSolver#solveInverseFromTwoObservations(double, double, double, double)}.</p>
 */
public final class RainInvestigatorEvaluator {

    /**
     * Strict upper bound for supported observation angles: |phi| < pi/2 - 1e-6 radians (~89.99994 deg).
     */
    public static final double MAX_SUPPORTED_ANGLE_RAD = (Math.PI / 2.0) - 1e-6;

    /**
     * Pedagogical classification threshold for downward condition number kappa_down.
     * Observations with kappa_down <= 25.0 are classified as VALID; kappa_down > 25.0 are ILL_CONDITIONED.
     */
    public static final double ILL_CONDITIONED_THRESHOLD = 25.0;

    /**
     * Assumed nominal angular measurement uncertainty for students: 1.0 degree in radians.
     */
    public static final double NOMINAL_ANGLE_UNCERTAINTY_RAD = Math.toRadians(1.0);

    private RainInvestigatorEvaluator() {
        // Utility class
    }

    /**
     * Evaluates two observations of apparent umbrella tilt at different horizontal walking velocities
     * to reconstruct the true ground-frame rain velocity.
     *
     * @param vPerson1   First observer walking velocity in m/s (signed horizontal)
     * @param angleRad1  First apparent umbrella tilt from upward vertical in radians
     * @param vPerson2   Second observer walking velocity in m/s (signed horizontal)
     * @param angleRad2  Second apparent umbrella tilt from upward vertical in radians
     * @param policy     Numerical tolerance policy (must not be null)
     * @return Immutable result containing classification status, reconstructed velocity (if solvable),
     *         conditioning diagnostics, and diagnostic message
     */
    public static RainInvestigatorResult evaluate(
            double vPerson1, double angleRad1,
            double vPerson2, double angleRad2,
            RelativeTolerancePolicy policy) {

        Objects.requireNonNull(policy, "policy must not be null");

        // Step 1 - Raw finite-input validation
        if (!Double.isFinite(vPerson1) || !Double.isFinite(angleRad1) ||
            !Double.isFinite(vPerson2) || !Double.isFinite(angleRad2)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.INVALID_NON_FINITE,
                "Observation inputs must be finite numbers.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 2 - Strict angle-domain validation
        if (Math.abs(angleRad1) >= MAX_SUPPORTED_ANGLE_RAD || Math.abs(angleRad2) >= MAX_SUPPORTED_ANGLE_RAD) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.INVALID_ANGLE_RANGE,
                String.format(java.util.Locale.ROOT,
                    "Umbrella tilt angle magnitude (%.6f rad) must be strictly less than boundary (%.6f rad).",
                    Math.max(Math.abs(angleRad1), Math.abs(angleRad2)), MAX_SUPPORTED_ANGLE_RAD),
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 3 - Finite walking-speed difference
        double deltaVm = vPerson2 - vPerson1;
        if (!Double.isFinite(deltaVm)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Walking velocity difference overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 4 - Identical walking speeds
        if (policy.areEqual(vPerson1, vPerson2, 1e-12)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS,
                "Observations at identical walking velocities provide zero vertical resolution.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 5 - Tangent calculations
        double tan1 = Math.tan(angleRad1);
        double tan2 = Math.tan(angleRad2);
        if (!Double.isFinite(tan1) || !Double.isFinite(tan2)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Observation angle tangents overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 6 - Parallel observation rays
        double denom = tan2 - tan1;
        if (!Double.isFinite(denom)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Observation tangent difference overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        double denomThreshold = Math.max(1e-12, policy.relativeTolerance() * Math.max(Math.abs(tan1), Math.abs(tan2)));
        if (!Double.isFinite(denomThreshold)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Denominator threshold calculation overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        if (Math.abs(denom) <= denomThreshold) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS,
                "Observations have identical or parallel apparent angles; velocity rays do not intersect.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 7 - Downward-speed calculation
        double vDown = deltaVm / denom;
        if (!Double.isFinite(vDown)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Calculated downward speed overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        if (vDown <= 0.0) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION,
                "Inconsistent observations: apparent directions imply rain falling upward (v_down <= 0).",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 8 - Conditioning calculation (using sec^2(phi) = 1 + tan^2(phi))
        double secSq1 = 1.0 + tan1 * tan1;
        double secSq2 = 1.0 + tan2 * tan2;
        double sec4_1 = secSq1 * secSq1;
        double sec4_2 = secSq2 * secSq2;
        double numerator = Math.sqrt(sec4_1 + sec4_2);
        double kappa = numerator / Math.abs(denom);

        if (!Double.isFinite(kappa)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Conditioning metric calculation overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 9 - Estimated relative uncertainty
        double relUncertainty = kappa * NOMINAL_ANGLE_UNCERTAINTY_RAD;
        if (!Double.isFinite(relUncertainty)) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Estimated relative uncertainty calculation overflowed finite numerical limits.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        // Step 10 - Authoritative solver invocation
        Vector2D reconstructedRain;
        try {
            reconstructedRain = RainManSolver.solveInverseFromTwoObservations(vPerson1, angleRad1, vPerson2, angleRad2);
        } catch (ArithmeticException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Observations must have distinct umbrella tilt angles")) {
                return RainInvestigatorResult.unsolvable(
                    RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS,
                    e.getMessage(),
                    vPerson1, angleRad1, vPerson2, angleRad2
                );
            }
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Unexpected arithmetic exception during solver execution: " + e.getMessage(),
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Inconsistent observations: calculated downward speed must be positive")) {
                return RainInvestigatorResult.unsolvable(
                    RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION,
                    e.getMessage(),
                    vPerson1, angleRad1, vPerson2, angleRad2
                );
            }
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Unexpected illegal argument during solver execution: " + e.getMessage(),
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        } catch (RuntimeException e) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Unexpected runtime failure during solver execution: " + e.getMessage(),
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        if (reconstructedRain == null || !Double.isFinite(reconstructedRain.x()) || !Double.isFinite(reconstructedRain.y())) {
            return RainInvestigatorResult.unsolvable(
                RainInvestigatorStatus.NUMERICAL_FAILURE,
                "Solver returned non-finite velocity coordinates.",
                vPerson1, angleRad1, vPerson2, angleRad2
            );
        }

        RainInvestigatorStatus status = (kappa > ILL_CONDITIONED_THRESHOLD)
                ? RainInvestigatorStatus.ILL_CONDITIONED
                : RainInvestigatorStatus.VALID;

        String diagnosticMessage = (status == RainInvestigatorStatus.ILL_CONDITIONED)
                ? String.format(java.util.Locale.ROOT,
                    "Reconstructed rain velocity is mathematically valid but highly sensitive: kappa_down = %.2f " +
                    "(estimated downward relative uncertainty ~%.1f%% assuming nominal 1.0 deg measurement uncertainty).",
                    kappa, relUncertainty * 100.0)
                : String.format(java.util.Locale.ROOT,
                    "Reconstructed rain velocity resolved reliably: kappa_down = %.2f " +
                    "(estimated downward relative uncertainty ~%.1f%% assuming nominal 1.0 deg measurement uncertainty).",
                    kappa, relUncertainty * 100.0);

        return RainInvestigatorResult.solvable(
            status,
            reconstructedRain,
            kappa,
            relUncertainty,
            diagnosticMessage,
            vPerson1, angleRad1, vPerson2, angleRad2
        );
    }

    /**
     * Evaluates two observations using {@link RelativeTolerancePolicy#DEFAULT}.
     */
    public static RainInvestigatorResult evaluate(
            double vPerson1, double angleRad1,
            double vPerson2, double angleRad2) {
        return evaluate(vPerson1, angleRad1, vPerson2, angleRad2, RelativeTolerancePolicy.DEFAULT);
    }
}

