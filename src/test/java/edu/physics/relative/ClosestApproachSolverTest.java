package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClosestApproachSolver - Two-Projectile Relative Pursuit Tests")
class ClosestApproachSolverTest {

    @Test
    @DisplayName("Exact collision during simultaneous parabolic flight (Flagship Scenario)")
    void testExactCollisionDuringFlight() {
        // Projectile A launched from (0, 0) with v0 = (20, 25)
        // Projectile B launched from (100, 0) towards A with v0 = (-20, 25)
        // Gravity g = 10 m/s^2
        // Both have vy0 = 25 -> t_land = 5.0 s
        // r0 = (100, 0), v_rel = (-40, 0)
        // t_min = - (100 * -40) / 1600 = 4000 / 1600 = 2.5 s
        // Since 2.5 s < 5.0 s, exact collision occurs at t = 2.5 s at (50, y(2.5))
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 0.0), new Vector2D(20.0, 25.0), 0.1);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(100.0, 0.0), new Vector2D(-20.0, 25.0), 0.1);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(RelativeKinematicsState.UNIFORM_RELATIVE_VELOCITY, result.kinematicsState());
        assertEquals(ContactClassification.EXACT_CONTACT, result.contactClassification());
        assertTrue(result.hasContact());
        assertEquals(2.5, result.tMinUnconstrained(), 1e-9);
        assertEquals(2.5, result.tMinInterval(), 1e-9);
        assertEquals(0.0, result.dMinLine(), 1e-9);
        assertEquals(0.0, result.dMinInterval(), 1e-9);
        assertEquals(5.0, result.simultaneousFlightDuration(), 1e-9);

        // Verify ground positions match at t = 2.5 s
        assertEquals(50.0, result.positionAAtClosest().x(), 1e-9);
        assertEquals(50.0, result.positionBAtClosest().x(), 1e-9);
        double expectedY = 25.0 * 2.5 - 0.5 * 10.0 * 2.5 * 2.5; // 62.5 - 31.25 = 31.25 m
        assertEquals(expectedY, result.positionAAtClosest().y(), 1e-9);
        assertEquals(expectedY, result.positionBAtClosest().y(), 1e-9);
    }

    @Test
    @DisplayName("Line collision occurs after ground landing (t_min > T): correctly rejects collision")
    void testPostLandingLineCollisionRejection() {
        // Body A launched from (0, 0) with vy0 = 10 m/s -> t_land = 2.0 s (g = 10)
        // Body B launched from (200, 0) with vy0 = 10 m/s -> t_land = 2.0 s
        // vxA = 20 m/s, vxB = -20 m/s -> v_rel = (-40, 0)
        // Line collision would occur at t = 200 / 40 = 5.0 s
        // But both bodies hit the ground at T = 2.0 s!
        // At T = 2.0 s: posA = (40, 0), posB = (120, 0) -> separation = 80 m
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 0.0), new Vector2D(20.0, 10.0), 0.2);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(200.0, 0.0), new Vector2D(-20.0, 10.0), 0.2);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(5.0, result.tMinUnconstrained(), 1e-9);
        assertEquals(0.0, result.dMinLine(), 1e-9);

        // Interval closest approach clamped to landing at T = 2.0 s
        assertEquals(2.0, result.tMinInterval(), 1e-9);
        assertEquals(120.0, result.dMinInterval(), 1e-9);
        assertEquals(ContactClassification.NO_CONTACT, result.contactClassification());
        assertFalse(result.hasContact());
    }

    @Test
    @DisplayName("Diverging trajectories (t_min < 0) achieve closest approach at launch t = 0")
    void testDivergentTrajectories() {
        // Both launched moving away from each other:
        // A at (0, 0) moving left (-10, 20), B at (50, 0) moving right (+10, 20)
        // r0 = (50, 0), v_rel = (20, 0) -> t_min = - (50 * 20) / 400 = -2.5 s
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 0.0), new Vector2D(-10.0, 20.0), 0.2);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(50.0, 0.0), new Vector2D(10.0, 20.0), 0.2);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(-2.5, result.tMinUnconstrained(), 1e-9);
        assertEquals(0.0, result.tMinInterval(), 1e-9);
        assertEquals(50.0, result.dMinInterval(), 1e-9);
        assertEquals(ContactClassification.NO_CONTACT, result.contactClassification());
    }

    @Test
    @DisplayName("Near-miss trajectory within proximity threshold")
    void testNearMiss() {
        // r0 = (100, 1.0), v_rel = (-40, 0) -> d_min,line = 1.0 m
        // t_min = 2.5 s, flight duration = 5.0 s
        // Effective contact radius = 0.4 m, proximity radius = 2.0 m
        // Since d_min = 1.0 m lies in (0.4, 2.0], status is NEAR_MISS
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 0.0), new Vector2D(20.0, 25.0), 0.2);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(100.0, 1.0), new Vector2D(-20.0, 25.0), 0.2);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(ContactClassification.NEAR_MISS, result.contactClassification());
        assertFalse(result.hasContact());
        assertEquals(1.0, result.dMinInterval(), 1e-9);
    }

    @Test
    @DisplayName("Stationary co-located launch (v_rel = 0, r0 = 0)")
    void testStationaryCoLocation() {
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(10.0, 0.0), new Vector2D(15.0, 20.0), 0.1);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(10.0, 0.0), new Vector2D(15.0, 20.0), 0.1);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(RelativeKinematicsState.STATIONARY_SEPARATION, result.kinematicsState());
        assertEquals(ContactClassification.EXACT_CONTACT, result.contactClassification());
        assertEquals(0.0, result.dMinInterval(), 1e-9);
    }

    @Test
    @DisplayName("Stationary parallel launch (v_rel = 0, r0 != 0)")
    void testStationaryParallelLaunch() {
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 0.0), new Vector2D(15.0, 20.0), 0.1);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(30.0, 0.0), new Vector2D(15.0, 20.0), 0.1);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 10.0);

        assertEquals(RelativeKinematicsState.STATIONARY_SEPARATION, result.kinematicsState());
        assertEquals(ContactClassification.NO_CONTACT, result.contactClassification());
        assertEquals(30.0, result.dMinInterval(), 1e-9);
    }

    @Test
    @DisplayName("Zero gravity pursuit preserves infinite simultaneous flight duration")
    void testZeroGravityInfiniteFlightDuration() {
        // Both launched with vy >= 0 in zero gravity -> neither lands
        RelativeBody2D bodyA = RelativeBody2D.ofGround("A", new Vector2D(0.0, 10.0), new Vector2D(10.0, 5.0), 0.1);
        RelativeBody2D bodyB = RelativeBody2D.ofGround("B", new Vector2D(50.0, 10.0), new Vector2D(-10.0, 5.0), 0.1);

        ClosestApproachResult result = ClosestApproachSolver.solve(bodyA, bodyB, 0.0);

        assertEquals(Double.POSITIVE_INFINITY, result.simultaneousFlightDuration());
        assertEquals(2.5, result.tMinInterval(), 1e-9);
        assertEquals(ContactClassification.EXACT_CONTACT, result.contactClassification());
    }
}
