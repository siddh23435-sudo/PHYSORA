package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Galilean reference frame transformations and relative kinematic vector algebra in 2D Cartesian coordinates.
 *
 * <p>All equations adhere to classical Galilean relativity:
 * <pre>
 *   r_{B/A} = r_B - r_A
 *   v_{B/A} = v_B - v_A
 *   a_{B/A} = a_B - a_A
 * </pre>
 * Under uniform downward gravity g, a_A = a_B = -g j, so a_{B/A} = 0 identically.
 * Relative motion is therefore pure rectilinear uniform velocity in the observer frame:
 * <pre>
 *   r_{B/A}(t) = r_{B/A}(0) + v_{B/A} * t
 * </pre>
 * </p>
 */
public final class GalileanTransform {

    private GalileanTransform() {
        // Utility class
    }

    /**
     * Relative position of body B observed from body A: r_{B/A} = r_B - r_A.
     */
    public static Vector2D relativePosition(Vector2D posB, Vector2D posA) {
        Objects.requireNonNull(posB, "posB must not be null");
        Objects.requireNonNull(posA, "posA must not be null");
        return posB.subtract(posA);
    }

    /**
     * Relative velocity of body B observed from body A: v_{B/A} = v_B - v_A.
     */
    public static Vector2D relativeVelocity(Vector2D velB, Vector2D velA) {
        Objects.requireNonNull(velB, "velB must not be null");
        Objects.requireNonNull(velA, "velA must not be null");
        return velB.subtract(velA);
    }

    /**
     * Relative acceleration of body B observed from body A: a_{B/A} = a_B - a_A.
     */
    public static Vector2D relativeAcceleration(Vector2D accB, Vector2D accA) {
        Objects.requireNonNull(accB, "accB must not be null");
        Objects.requireNonNull(accA, "accA must not be null");
        return accB.subtract(accA);
    }

    /**
     * Transforms a ground frame coordinate into the observer frame centered on observerOrigin.
     */
    public static Vector2D toObserverFrame(Vector2D groundPos, Vector2D observerOrigin) {
        return relativePosition(groundPos, observerOrigin);
    }

    /**
     * Transforms an observer frame coordinate back into the ground laboratory frame.
     */
    public static Vector2D toGroundFrame(Vector2D relativePos, Vector2D observerGroundPos) {
        Objects.requireNonNull(relativePos, "relativePos must not be null");
        Objects.requireNonNull(observerGroundPos, "observerGroundPos must not be null");
        return relativePos.add(observerGroundPos);
    }

    /**
     * Computes the relative position at time t under uniform identical gravitational acceleration.
     * Since relative acceleration vanishes identically, r_{B/A}(t) = r0 + vRel * t.
     */
    public static Vector2D straightLineRelativePosition(Vector2D initialRelativePosition,
                                                        Vector2D initialRelativeVelocity,
                                                        double time) {
        Objects.requireNonNull(initialRelativePosition, "initialRelativePosition must not be null");
        Objects.requireNonNull(initialRelativeVelocity, "initialRelativeVelocity must not be null");
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite: " + time);
        }
        return initialRelativePosition.add(initialRelativeVelocity.multiply(time));
    }

    /**
     * Transforms the position of body A or B into the coordinates of the target reference frame.
     */
    public static Vector2D positionInFrame(Vector2D posA, Vector2D posB, boolean forBodyA, ReferenceFrame frame) {
        Objects.requireNonNull(posA, "posA must not be null");
        Objects.requireNonNull(posB, "posB must not be null");
        Objects.requireNonNull(frame, "frame must not be null");

        return switch (frame) {
            case GROUND -> forBodyA ? posA : posB;
            case OBSERVER_A -> forBodyA ? Vector2D.ZERO : posB.subtract(posA);
            case OBSERVER_B -> forBodyA ? posA.subtract(posB) : Vector2D.ZERO;
        };
    }

    /**
     * Transforms the velocity of body A or B into the coordinates of the target reference frame.
     */
    public static Vector2D velocityInFrame(Vector2D velA, Vector2D velB, boolean forBodyA, ReferenceFrame frame) {
        Objects.requireNonNull(velA, "velA must not be null");
        Objects.requireNonNull(velB, "velB must not be null");
        Objects.requireNonNull(frame, "frame must not be null");

        return switch (frame) {
            case GROUND -> forBodyA ? velA : velB;
            case OBSERVER_A -> forBodyA ? Vector2D.ZERO : velB.subtract(velA);
            case OBSERVER_B -> forBodyA ? velA.subtract(velB) : Vector2D.ZERO;
        };
    }
}
