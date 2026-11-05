package edu.physics.projectile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileMotionTest {
    private static final double TOLERANCE = 1e-9;
    private static final double G = 9.8;

    @Test
    void computesNormalFortyFiveDegreeTrajectoryAndLanding() {
        ProjectileMotion projectile = projectile(20.0, 45.0, G, 0.0, 0.0, 0.0);
        double expectedFlight = 2.0 * 20.0 * Math.sin(Math.PI / 4.0) / G;
        double expectedRange = 20.0 * 20.0 / G;

        assertEquals(expectedFlight, projectile.timeOfFlight().orElseThrow(), TOLERANCE);
        assertEquals(expectedRange, projectile.horizontalRange().orElseThrow(), TOLERANCE);
        assertEquals(20.0 * 20.0 / (4.0 * G),
                projectile.maximumHeight().orElseThrow(), TOLERANCE);
        ProjectileMotion.State landing = projectile.stateAt(expectedFlight);
        assertEquals(expectedRange, landing.x(), TOLERANCE);
        assertEquals(0.0, landing.y(), TOLERANCE);
        assertEquals(-20.0 * Math.sin(Math.PI / 4.0), landing.vy(), TOLERANCE);
    }

    @Test
    void zeroSpeedDropsFromElevatedLaunchPoint() {
        ProjectileMotion projectile = projectile(0.0, 25.0, G, 4.0, 10.0, 0.0);
        double flight = Math.sqrt(20.0 / G);
        ProjectileMotion.State state = projectile.stateAt(flight);

        assertEquals(flight, projectile.timeOfFlight().orElseThrow(), TOLERANCE);
        assertEquals(4.0, state.x(), TOLERANCE);
        assertEquals(0.0, state.y(), TOLERANCE);
        assertEquals(10.0, projectile.maximumHeight().orElseThrow(), TOLERANCE);
    }

    @Test
    void verticalLaunchHasNoHorizontalDisplacement() {
        ProjectileMotion projectile = projectile(12.0, 90.0, G, 2.0, 0.0, 0.0);

        assertEquals(2.0, projectile.stateAt(0.5).x(), TOLERANCE);
        assertEquals(0.0, projectile.stateAt(0.5).vx(), TOLERANCE);
        assertEquals(12.0 * 12.0 / (2.0 * G),
                projectile.maximumHeight().orElseThrow(), TOLERANCE);
        assertEquals(2.0 * 12.0 / G,
                projectile.timeOfFlight().orElseThrow(), TOLERANCE);
    }

    @Test
    void zeroGravityProducesLinearMotionAndNoLandingWhenMovingHorizontally() {
        ProjectileMotion projectile = projectile(10.0, 0.0, 0.0, 0.0, 3.0, 0.0);
        ProjectileMotion.State state = projectile.stateAt(2.0);

        assertEquals(20.0, state.x(), TOLERANCE);
        assertEquals(3.0, state.y(), TOLERANCE);
        assertTrue(projectile.timeOfFlight().isEmpty());
        assertTrue(projectile.horizontalRange().isEmpty());
        assertEquals(3.0, projectile.maximumHeight().orElseThrow(), TOLERANCE);

        ProjectileMotion upward = projectile(10.0, 90.0, 0.0, 0.0, 0.0, 0.0);
        assertTrue(upward.timeOfFlight().isEmpty());
        assertFalse(upward.maximumHeight().isPresent());
    }

    @Test
    void elevatedLaunchUsesLandingHeightQuadraticInsteadOfEqualHeightFormula() {
        ProjectileMotion projectile = projectile(10.0, 30.0, 10.0, 5.0, 5.0, 0.0);
        double expectedFlight = (Math.sqrt(125.0) + 5.0) / 10.0;
        ProjectileMotion.State landing = projectile.stateAt(expectedFlight);

        assertEquals(expectedFlight, projectile.timeOfFlight().orElseThrow(), TOLERANCE);
        assertEquals(0.0, landing.y(), TOLERANCE);
        assertEquals(5.0 + 10.0 * Math.cos(Math.PI / 6.0) * expectedFlight,
                landing.x(), TOLERANCE);
    }

    @Test
    void launchUpwardFromLandingHeightUsesLaterCrossing() {
        ProjectileMotion projectile = projectile(8.0, 90.0, 10.0, 0.0, 0.0, 0.0);

        assertEquals(1.6, projectile.timeOfFlight().orElseThrow(), TOLERANCE);
    }

    @Test
    void rejectsNegativeOrNonFinitePhysicalInputsAndNegativeTime() {
        assertThrows(IllegalArgumentException.class, () -> projectile(-1.0, 45.0, G, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> projectile(1.0, 45.0, -G, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> projectile(Double.NaN, 45.0, G, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> projectile(1.0, Double.POSITIVE_INFINITY, G, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> projectile(1.0, 45.0, G, 0, 0, 0).stateAt(-0.1));
        assertThrows(IllegalArgumentException.class,
                () -> projectile(1.0, 45.0, G, 0, 0, 0).stateAt(Double.NaN));
    }

    private static ProjectileMotion projectile(double speed, double angle, double gravity,
                                               double initialX, double initialY, double landingY) {
        return new ProjectileMotion(speed, angle, gravity, initialX, initialY, landingY);
    }
}
