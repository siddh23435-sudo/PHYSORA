package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable specification of a two-dimensional body for relative motion analysis.
 *
 * @param id              Unique identifier for the body (e.g. "Projectile A", "Boat", "Raindrop")
 * @param initialPosition Launch position in the ground Cartesian frame (meters)
 * @param initialVelocity Launch velocity in the ground Cartesian frame (m/s)
 * @param radius          Physical collision radius for contact detection (meters, >= 0)
 * @param landingSurfaceY Altitude of the horizontal surface where flight terminates (meters)
 */
public record RelativeBody2D(
        String id,
        Vector2D initialPosition,
        Vector2D initialVelocity,
        double radius,
        double landingSurfaceY
) {

    public RelativeBody2D {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(initialPosition, "initialPosition must not be null");
        Objects.requireNonNull(initialVelocity, "initialVelocity must not be null");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative and finite: " + radius);
        }
        if (!Double.isFinite(landingSurfaceY)) {
            throw new IllegalArgumentException("landingSurfaceY must be finite: " + landingSurfaceY);
        }
    }

    /**
     * Factory for a standard point mass or spherical body with surface landing plane at y = 0.
     */
    public static RelativeBody2D ofGround(String id, Vector2D position, Vector2D velocity, double radius) {
        return new RelativeBody2D(id, position, velocity, radius, 0.0);
    }
}

