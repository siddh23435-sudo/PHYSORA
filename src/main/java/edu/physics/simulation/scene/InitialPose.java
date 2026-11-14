package edu.physics.simulation.scene;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable initial kinematic state and pose of a body in a laboratory scene blueprint.
 *
 * <p>Separated completely from mutable simulation runtime variables (net forces, accumulators,
 * instantaneous accelerations) to ensure the scene blueprint remains pristine and reconstructable.
 */
public record InitialPose(
        Vector2D position,
        double angle,
        Vector2D velocity,
        double angularVelocity) {

    public InitialPose {
        Objects.requireNonNull(position, "position is required");
        Objects.requireNonNull(velocity, "velocity is required");
        if (!Double.isFinite(angle)) {
            throw new IllegalArgumentException("angle must be finite: " + angle);
        }
        if (!Double.isFinite(angularVelocity)) {
            throw new IllegalArgumentException("angularVelocity must be finite: " + angularVelocity);
        }
    }

    public static InitialPose atRest(Vector2D position) {
        return new InitialPose(position, 0.0, Vector2D.ZERO, 0.0);
    }

    public static InitialPose atRest(Vector2D position, double angle) {
        return new InitialPose(position, angle, Vector2D.ZERO, 0.0);
    }

    public static InitialPose of(Vector2D position, Vector2D velocity) {
        return new InitialPose(position, 0.0, velocity, 0.0);
    }
}

