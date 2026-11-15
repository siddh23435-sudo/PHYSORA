package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Result of analytical river-boat crossing calculations.
 *
 * @param riverWidth      Width of the river d in meters (d > 0)
 * @param riverSpeed      Flow speed of the river current v_r in m/s (v_r >= 0)
 * @param boatSpeed       Boat engine speed relative to water v_b in m/s (v_b > 0)
 * @param headingAngleRad Steering angle theta in radians upstream from the bank normal
 * @param crossingTime    Crossing duration in seconds (empty if crossing never completes in finite time)
 * @param downstreamDrift Downstream drift along opposite bank in meters (empty if boat never crosses)
 * @param crossingType    Physical crossing outcome classification
 */
public record RiverBoatResult(
        double riverWidth,
        double riverSpeed,
        double boatSpeed,
        double headingAngleRad,
        OptionalDouble crossingTime,
        OptionalDouble downstreamDrift,
        RiverBoatCrossingType crossingType
) {

    public RiverBoatResult {
        Objects.requireNonNull(crossingTime, "crossingTime must not be null");
        Objects.requireNonNull(downstreamDrift, "downstreamDrift must not be null");
        Objects.requireNonNull(crossingType, "crossingType must not be null");
        if (!Double.isFinite(riverWidth) || riverWidth <= 0.0) {
            throw new IllegalArgumentException("riverWidth must be positive and finite: " + riverWidth);
        }
        if (!Double.isFinite(riverSpeed) || riverSpeed < 0.0) {
            throw new IllegalArgumentException("riverSpeed must be non-negative and finite: " + riverSpeed);
        }
        if (!Double.isFinite(boatSpeed) || boatSpeed <= 0.0) {
            throw new IllegalArgumentException("boatSpeed must be positive and finite: " + boatSpeed);
        }
        if (!Double.isFinite(headingAngleRad)) {
            throw new IllegalArgumentException("headingAngleRad must be finite: " + headingAngleRad);
        }
    }

    /**
     * Returns true if the boat successfully crosses to the opposite bank in finite time.
     */
    public boolean reachesOppositeBank() {
        return crossingTime.isPresent();
    }
}

