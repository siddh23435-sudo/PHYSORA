package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Analytical solver for rain-man apparent motion and umbrella orientation problems.
 *
 * <p>Coordinate Conventions:
 * <ul>
 *   <li>Horizontal motion is along +x (m/s).</li>
 *   <li>Upward vertical is +y (m/s). Rain typically falls downward (v_ry < 0).</li>
 *   <li>Apparent rain velocity: v_{r/m} = v_r - v_m.</li>
 *   <li>Umbrella vector points opposite to apparent rain: u = -v_{r/m} = (v_mx - v_rx) i + (-v_ry) j.</li>
 *   <li>Umbrella angle phi is measured from the upward vertical (+j). Positive phi tilts forward (+x).</li>
 * </ul>
 * </p>
 */
public final class RainManSolver {

    private RainManSolver() {
        // Utility class
    }

    /**
     * Solves apparent rain velocity and required umbrella tilt for given ground velocities.
     */
    public static RainManResult solve(Vector2D rainVelocityGround, Vector2D personVelocityGround) {
        Objects.requireNonNull(rainVelocityGround, "rainVelocityGround must not be null");
        Objects.requireNonNull(personVelocityGround, "personVelocityGround must not be null");

        Vector2D apparentRain = GalileanTransform.relativeVelocity(rainVelocityGround, personVelocityGround);
        double apparentSpeed = apparentRain.magnitude();

        // Umbrella points opposite to apparent rain vector
        Vector2D umbrellaDir = apparentRain.multiply(-1.0);

        // Angle from upward vertical (+j) towards horizontal (+i)
        // atan2(x, y) returns 0 when vector is (0, y>0) and positive towards +x
        double umbrellaAngle = Math.atan2(umbrellaDir.x(), umbrellaDir.y());

        return new RainManResult(
                rainVelocityGround,
                personVelocityGround,
                apparentRain,
                umbrellaAngle,
                apparentSpeed
        );
    }

    /**
     * Classic JEE Inverse Solver:
     * Deduces true ground rain velocity (v_rx, v_ry) from two observations of umbrella tilt
     * at two different horizontal walking speeds (vPerson1, vPerson2).
     *
     * <p>Governing equations:
     * <pre>
     *   tan(phi_1) = (v1 - v_rx) / v_down
     *   tan(phi_2) = (v2 - v_rx) / v_down
     *   v_down = (v2 - v1) / (tan(phi_2) - tan(phi_1))
     *   v_rx = v1 - v_down * tan(phi_1)
     *   v_r = (v_rx, -v_down)
     * </pre>
     * </p>
     */
    public static Vector2D solveInverseFromTwoObservations(double vPerson1,
                                                           double angleRad1,
                                                           double vPerson2,
                                                           double angleRad2) {
        if (!Double.isFinite(vPerson1) || !Double.isFinite(angleRad1)
                || !Double.isFinite(vPerson2) || !Double.isFinite(angleRad2)) {
            throw new IllegalArgumentException("Observation values must be finite");
        }

        double tan1 = Math.tan(angleRad1);
        double tan2 = Math.tan(angleRad2);
        double denom = tan2 - tan1;

        if (Math.abs(denom) <= 1e-12) {
            throw new ArithmeticException("Observations must have distinct umbrella tilt angles: tan1=" + tan1 + ", tan2=" + tan2);
        }

        double vDown = (vPerson2 - vPerson1) / denom;
        if (vDown <= 0.0) {
            throw new IllegalArgumentException("Inconsistent observations: calculated downward speed must be positive: " + vDown);
        }

        double vRx = vPerson1 - vDown * tan1;
        double vRy = -vDown; // Downward vertical velocity

        return new Vector2D(vRx, vRy);
    }
}

