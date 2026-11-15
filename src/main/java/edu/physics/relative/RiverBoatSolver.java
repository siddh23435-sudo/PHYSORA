package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Analytical solver for river-boat vector crossing problems.
 *
 * <p>Coordinate Convention:
 * <ul>
 *   <li>River current flows along +x with speed v_r (v_r >= 0).</li>
 *   <li>Across-river direction to opposite bank is +y with width d (d > 0).</li>
 *   <li>Steering angle theta is measured upstream from the +y normal to the bank (-pi/2 <= theta <= pi/2).</li>
 * </ul>
 * Boat velocity components in the ground bank frame:
 * <pre>
 *   v_x = v_r - v_b * sin(theta)
 *   v_y = v_b * cos(theta)
 * </pre>
 * </p>
 */
public final class RiverBoatSolver {

    private RiverBoatSolver() {
        // Utility class
    }

    /**
     * Solves crossing for an arbitrary steering angle theta using default tolerance policy.
     */
    public static RiverBoatResult solveForHeading(double riverWidth, double riverSpeed, double boatSpeed, double headingAngleRad) {
        return solveForHeading(riverWidth, riverSpeed, boatSpeed, headingAngleRad, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves crossing for an arbitrary steering angle theta with a specific tolerance policy.
     */
    public static RiverBoatResult solveForHeading(double riverWidth,
                                                  double riverSpeed,
                                                  double boatSpeed,
                                                  double headingAngleRad,
                                                  RelativeTolerancePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        validateInputs(riverWidth, riverSpeed, boatSpeed);

        double cosTheta = Math.cos(headingAngleRad);
        double sinTheta = Math.sin(headingAngleRad);
        double vy = boatSpeed * cosTheta;

        // If across-bank velocity vy <= 0, the boat never crosses to the opposite bank
        if (vy <= policy.velocityTolerance()) {
            boolean isEqualSpeed = policy.areEqual(boatSpeed, riverSpeed, policy.velocityTolerance());
            boolean isDirectlyUpstream = Math.abs(headingAngleRad - (Math.PI / 2.0)) <= 1e-6;

            RiverBoatCrossingType type = (isEqualSpeed && isDirectlyUpstream)
                    ? RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC
                    : RiverBoatCrossingType.GENERAL_CROSSING;

            return new RiverBoatResult(
                    riverWidth,
                    riverSpeed,
                    boatSpeed,
                    headingAngleRad,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    type
            );
        }

        double tCrossing = riverWidth / vy;
        double vx = riverSpeed - boatSpeed * sinTheta;
        double drift = vx * tCrossing;

        RiverBoatCrossingType type = RiverBoatCrossingType.GENERAL_CROSSING;
        if (Math.abs(headingAngleRad) <= policy.relativeTolerance()) {
            type = RiverBoatCrossingType.SHORTEST_TIME;
        } else if (policy.isZeroPosition(drift, riverWidth)) {
            type = RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE;
        } else if (boatSpeed < riverSpeed) {
            double optimalSinTheta = boatSpeed / riverSpeed;
            double optimalTheta = Math.asin(optimalSinTheta);
            if (policy.areEqual(headingAngleRad, optimalTheta, 1e-9)) {
                type = RiverBoatCrossingType.ZERO_DRIFT_UNACHIEVABLE;
            }
        }

        return new RiverBoatResult(
                riverWidth,
                riverSpeed,
                boatSpeed,
                headingAngleRad,
                OptionalDouble.of(tCrossing),
                OptionalDouble.of(drift),
                type
        );
    }

    /**
     * Solves the shortest time crossing strategy (theta = 0).
     */
    public static RiverBoatResult solveShortestTime(double riverWidth, double riverSpeed, double boatSpeed) {
        validateInputs(riverWidth, riverSpeed, boatSpeed);
        double tMin = riverWidth / boatSpeed;
        double drift = riverSpeed * tMin;

        return new RiverBoatResult(
                riverWidth,
                riverSpeed,
                boatSpeed,
                0.0,
                OptionalDouble.of(tMin),
                OptionalDouble.of(drift),
                RiverBoatCrossingType.SHORTEST_TIME
        );
    }

    /**
     * Solves the shortest path strategy (seeking zero downstream drift).
     */
    public static RiverBoatResult solveShortestPath(double riverWidth, double riverSpeed, double boatSpeed) {
        return solveShortestPath(riverWidth, riverSpeed, boatSpeed, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves the shortest path strategy with a specific tolerance policy.
     */
    public static RiverBoatResult solveShortestPath(double riverWidth,
                                                    double riverSpeed,
                                                    double boatSpeed,
                                                    RelativeTolerancePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        validateInputs(riverWidth, riverSpeed, boatSpeed);

        // Case 1: Equal Speed (v_b == v_r)
        if (policy.areEqual(boatSpeed, riverSpeed, policy.velocityTolerance())) {
            // Heading directly upstream (theta = 90 deg) cancels current, but across-river velocity is zero
            return new RiverBoatResult(
                    riverWidth,
                    riverSpeed,
                    boatSpeed,
                    Math.PI / 2.0,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC
            );
        }

        // Case 2: Boat speed exceeds current (v_b > v_r) -> Zero drift achievable
        if (boatSpeed > riverSpeed) {
            double sinTheta = riverSpeed / boatSpeed;
            double theta = Math.asin(sinTheta);
            double cosTheta = Math.sqrt(1.0 - sinTheta * sinTheta);
            double vNet = boatSpeed * cosTheta; // sqrt(v_b^2 - v_r^2)
            double tCrossing = riverWidth / vNet;

            return new RiverBoatResult(
                    riverWidth,
                    riverSpeed,
                    boatSpeed,
                    theta,
                    OptionalDouble.of(tCrossing),
                    OptionalDouble.of(0.0),
                    RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE
            );
        }

        // Case 3: Boat speed less than current (v_b < v_r) -> Minimum drift strategy
        // Optimal steering angle satisfies sin(theta) = v_b / v_r
        double sinTheta = boatSpeed / riverSpeed;
        double theta = Math.asin(sinTheta);
        double cosTheta = Math.sqrt(1.0 - sinTheta * sinTheta);
        double tCrossing = riverWidth / (boatSpeed * cosTheta);
        double minDrift = riverWidth * Math.sqrt((riverSpeed * riverSpeed) / (boatSpeed * boatSpeed) - 1.0);

        return new RiverBoatResult(
                riverWidth,
                riverSpeed,
                boatSpeed,
                theta,
                OptionalDouble.of(tCrossing),
                OptionalDouble.of(minDrift),
                RiverBoatCrossingType.ZERO_DRIFT_UNACHIEVABLE
        );
    }

    private static void validateInputs(double riverWidth, double riverSpeed, double boatSpeed) {
        if (!Double.isFinite(riverWidth) || riverWidth <= 0.0) {
            throw new IllegalArgumentException("riverWidth must be positive and finite: " + riverWidth);
        }
        if (!Double.isFinite(riverSpeed) || riverSpeed < 0.0) {
            throw new IllegalArgumentException("riverSpeed must be non-negative and finite: " + riverSpeed);
        }
        if (!Double.isFinite(boatSpeed) || boatSpeed <= 0.0) {
            throw new IllegalArgumentException("boatSpeed must be positive and finite: " + boatSpeed);
        }
    }
}

