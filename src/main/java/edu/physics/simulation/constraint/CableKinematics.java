package edu.physics.simulation.constraint;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Pure immutable kinematic snapshot of a direct point-to-point inextensible cable.
 *
 * <p>Captures attachment world coordinates, velocities, accelerations, cable displacement,
 * instantaneous length L, unit direction vector u (pointing from anchor/body B to body A),
 * rest length L_0, gap g = L_0 - L, gap rate g_dot = -u · v_rel, and convective acceleration
 * gamma_conv = J_dot · v.
 *
 * <p>Contains no solver state, unilateral multiplier flags, or tolerance-based status classifications.
 */
public record CableKinematics(
        Vector2D worldAttachmentA,
        Vector2D worldAttachmentB,
        Vector2D worldVelocityA,
        Vector2D worldVelocityB,
        Vector2D worldAccelerationA,
        Vector2D worldAccelerationB,
        Vector2D relativeDisplacement,
        Vector2D relativeVelocity,
        Vector2D relativeAcceleration,
        double currentLength,
        Vector2D unitDirection,
        double restLength,
        double gap,
        double gapRate,
        double convectiveAcceleration) {

    public CableKinematics {
        Objects.requireNonNull(worldAttachmentA, "worldAttachmentA is required");
        Objects.requireNonNull(worldAttachmentB, "worldAttachmentB is required");
        Objects.requireNonNull(worldVelocityA, "worldVelocityA is required");
        Objects.requireNonNull(worldVelocityB, "worldVelocityB is required");
        Objects.requireNonNull(worldAccelerationA, "worldAccelerationA is required");
        Objects.requireNonNull(worldAccelerationB, "worldAccelerationB is required");
        Objects.requireNonNull(relativeDisplacement, "relativeDisplacement is required");
        Objects.requireNonNull(relativeVelocity, "relativeVelocity is required");
        Objects.requireNonNull(relativeAcceleration, "relativeAcceleration is required");
        Objects.requireNonNull(unitDirection, "unitDirection is required");

        if (!Double.isFinite(currentLength) || currentLength < 0.0) {
            throw new IllegalArgumentException("currentLength must be finite and >= 0: " + currentLength);
        }
        if (!Double.isFinite(restLength) || restLength <= 0.0) {
            throw new IllegalArgumentException("restLength must be finite and > 0: " + restLength);
        }
        if (!Double.isFinite(gap)) {
            throw new IllegalArgumentException("gap must be finite: " + gap);
        }
        if (!Double.isFinite(gapRate)) {
            throw new IllegalArgumentException("gapRate must be finite: " + gapRate);
        }
        if (!Double.isFinite(convectiveAcceleration)) {
            throw new IllegalArgumentException("convectiveAcceleration must be finite: " + convectiveAcceleration);
        }
    }
}

