package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Result of apparent rain velocity and umbrella orientation calculations.
 *
 * @param rainVelocityGround   Velocity of rain relative to Ground (m/s)
 * @param personVelocityGround Velocity of person/observer relative to Ground (m/s)
 * @param apparentRainVelocity Apparent velocity of rain observed by moving person: v_{r/m} = v_r - v_m (m/s)
 * @param umbrellaAngleRad     Signed angle from upward vertical (+j). Positive tilts forward (+x), negative backward (-x)
 * @param apparentRainSpeed    Magnitude of apparent rain velocity |v_{r/m}| in m/s
 */
public record RainManResult(
        Vector2D rainVelocityGround,
        Vector2D personVelocityGround,
        Vector2D apparentRainVelocity,
        double umbrellaAngleRad,
        double apparentRainSpeed
) {

    public RainManResult {
        Objects.requireNonNull(rainVelocityGround, "rainVelocityGround must not be null");
        Objects.requireNonNull(personVelocityGround, "personVelocityGround must not be null");
        Objects.requireNonNull(apparentRainVelocity, "apparentRainVelocity must not be null");
        if (!Double.isFinite(umbrellaAngleRad)) {
            throw new IllegalArgumentException("umbrellaAngleRad must be finite: " + umbrellaAngleRad);
        }
        if (!Double.isFinite(apparentRainSpeed) || apparentRainSpeed < 0.0) {
            throw new IllegalArgumentException("apparentRainSpeed must be non-negative and finite: " + apparentRainSpeed);
        }
    }

    /**
     * Umbrella shielding unit vector pointing directly opposite to apparent rain.
     */
    public Vector2D umbrellaOrientationVector() {
        return apparentRainVelocity.multiply(-1.0).normalize();
    }
}

