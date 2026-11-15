package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Result of 1D pursuit and overtaking analytical calculations.
 *
 * @param initialSeparation        Initial displacement x0 = x_B(0) - x_A(0) in meters
 * @param initialRelativeVelocity  Initial relative velocity v_rel0 = v_B(0) - v_A(0) in m/s
 * @param relativeAcceleration     Relative acceleration a_rel = a_B - a_A in m/s²
 * @param discriminant             Quadratic discriminant Delta = v_rel0² - 2 * a_rel * x0
 * @param primaryCollisionTime     Earliest collision time in [0, T_active] (t = 0 for initial co-location)
 * @param subsequentCollisionTime  Subsequent positive collision root for x0 = 0 (empty if none)
 * @param minimumSeparationInterval Minimum absolute separation min |x_rel(t)| achieved on [0, T_active] (meters)
 * @param activeIntervalDuration   Duration of active scenario interval T_active in seconds
 * @param status                   Physical pursuit classification
 */
public record Pursuit1DResult(
        double initialSeparation,
        double initialRelativeVelocity,
        double relativeAcceleration,
        double discriminant,
        OptionalDouble primaryCollisionTime,
        OptionalDouble subsequentCollisionTime,
        double minimumSeparationInterval,
        double activeIntervalDuration,
        Pursuit1DStatus status
) {

    public Pursuit1DResult {
        Objects.requireNonNull(primaryCollisionTime, "primaryCollisionTime must not be null");
        Objects.requireNonNull(subsequentCollisionTime, "subsequentCollisionTime must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (!Double.isFinite(initialSeparation)) {
            throw new IllegalArgumentException("initialSeparation must be finite: " + initialSeparation);
        }
        if (!Double.isFinite(initialRelativeVelocity)) {
            throw new IllegalArgumentException("initialRelativeVelocity must be finite: " + initialRelativeVelocity);
        }
        if (!Double.isFinite(relativeAcceleration)) {
            throw new IllegalArgumentException("relativeAcceleration must be finite: " + relativeAcceleration);
        }
        if (!Double.isFinite(minimumSeparationInterval) || minimumSeparationInterval < 0.0) {
            throw new IllegalArgumentException("minimumSeparationInterval must be non-negative and finite: " + minimumSeparationInterval);
        }
        if (!Double.isFinite(activeIntervalDuration) || activeIntervalDuration < 0.0) {
            throw new IllegalArgumentException("activeIntervalDuration must be non-negative and finite: " + activeIntervalDuration);
        }
    }

    /**
     * Returns true if a valid collision occurs within the active scenario interval.
     */
    public boolean hasCollision() {
        return primaryCollisionTime.isPresent();
    }

    /**
     * Returns true if physical contact occurs during the active interval,
     * either through analytical center-of-mass collision or because minimum
     * surface separation is within the specified contact radius.
     */
    public boolean hasContact(double contactRadius) {
        return hasCollision() || minimumSeparationInterval <= contactRadius;
    }

    /**
     * Returns true if physical contact occurs using default contact radius (0.2 m).
     */
    public boolean hasContact() {
        return hasContact(RelativeTolerancePolicy.DEFAULT_CONTACT_RADIUS);
    }
}

