package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Analytical closest approach and collision evaluation for two bodies in relative motion.
 *
 * @param tMinUnconstrained         Geometric line vertex time on (-∞, +∞) in seconds
 * @param dMinLine                  Perpendicular distance to the infinite line of relative motion in meters
 * @param tMinInterval              Clamped closest approach time t* in [0, T] in seconds
 * @param dMinInterval              Minimum separation distance achieved during simultaneous flight in meters
 * @param contactClassification     Spatial proximity classification (EXACT_CONTACT, TOLERANCE_CONTACT, NEAR_MISS, NO_CONTACT)
 * @param kinematicsState           Kinematic state (STATIONARY_SEPARATION, UNIFORM_RELATIVE_VELOCITY)
 * @param positionAAtClosest        Ground position of body A at t* (meters)
 * @param positionBAtClosest        Ground position of body B at t* (meters)
 * @param simultaneousFlightDuration Duration of simultaneous active flight T = min(t_land,A, t_land,B) (seconds)
 * @param contactRadius             Configurable contact detection threshold in meters
 * @param proximityRadius           Configurable proximity/near-miss threshold in meters
 */
public record ClosestApproachResult(
        double tMinUnconstrained,
        double dMinLine,
        double tMinInterval,
        double dMinInterval,
        ContactClassification contactClassification,
        RelativeKinematicsState kinematicsState,
        Vector2D positionAAtClosest,
        Vector2D positionBAtClosest,
        double simultaneousFlightDuration,
        double contactRadius,
        double proximityRadius
) {

    public ClosestApproachResult {
        Objects.requireNonNull(contactClassification, "contactClassification must not be null");
        Objects.requireNonNull(kinematicsState, "kinematicsState must not be null");
        Objects.requireNonNull(positionAAtClosest, "positionAAtClosest must not be null");
        Objects.requireNonNull(positionBAtClosest, "positionBAtClosest must not be null");
        if (!Double.isFinite(tMinInterval) || tMinInterval < 0.0) {
            throw new IllegalArgumentException("tMinInterval must be non-negative and finite: " + tMinInterval);
        }
        if (!Double.isFinite(dMinInterval) || dMinInterval < 0.0) {
            throw new IllegalArgumentException("dMinInterval must be non-negative and finite: " + dMinInterval);
        }
        if (Double.isNaN(simultaneousFlightDuration) || simultaneousFlightDuration < 0.0) {
            throw new IllegalArgumentException("simultaneousFlightDuration must be non-negative: " + simultaneousFlightDuration);
        }
    }

    /**
     * Returns true if physical contact occurs during active flight (exact or within tolerance).
     */
    public boolean hasContact() {
        return contactClassification == ContactClassification.EXACT_CONTACT
                || contactClassification == ContactClassification.TOLERANCE_CONTACT;
    }
}

