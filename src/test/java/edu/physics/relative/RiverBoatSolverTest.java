package edu.physics.relative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiverBoatSolver - River Boat Relative Velocity and Drift Tests")
class RiverBoatSolverTest {

    @Test
    @DisplayName("Shortest time crossing strategy heads perpendicular to current")
    void testShortestTime() {
        // d = 400 m, v_r = 3 m/s, v_b = 5 m/s -> t_min = 400 / 5 = 80 s, drift = 3 * 80 = 240 m
        RiverBoatResult result = RiverBoatSolver.solveShortestTime(400.0, 3.0, 5.0);

        assertEquals(RiverBoatCrossingType.SHORTEST_TIME, result.crossingType());
        assertTrue(result.reachesOppositeBank());
        assertEquals(80.0, result.crossingTime().getAsDouble(), 1e-9);
        assertEquals(240.0, result.downstreamDrift().getAsDouble(), 1e-9);
        assertEquals(0.0, result.headingAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("Zero drift achievable when boat speed exceeds current (v_b > v_r)")
    void testZeroDriftAchievable() {
        // d = 400 m, v_r = 3 m/s, v_b = 5 m/s -> sin(theta) = 3/5 = 0.6
        // v_net = sqrt(25 - 9) = 4 m/s -> t = 400 / 4 = 100 s, drift = 0
        RiverBoatResult result = RiverBoatSolver.solveShortestPath(400.0, 3.0, 5.0);

        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE, result.crossingType());
        assertTrue(result.reachesOppositeBank());
        assertEquals(100.0, result.crossingTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.downstreamDrift().getAsDouble(), 1e-9);
        assertEquals(Math.asin(0.6), result.headingAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("Equal speed boundary (v_b == v_r) correctly returns ZERO_DRIFT_ONLY_ASYMPTOTIC with empty crossing time")
    void testEqualSpeedAsymptoticBoundary() {
        // d = 400 m, v_r = 4 m/s, v_b = 4 m/s
        RiverBoatResult result = RiverBoatSolver.solveShortestPath(400.0, 4.0, 4.0);

        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC, result.crossingType());
        assertFalse(result.reachesOppositeBank());
        assertFalse(result.crossingTime().isPresent());
        assertFalse(result.downstreamDrift().isPresent());
        assertEquals(Math.PI / 2.0, result.headingAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("Minimum drift when boat speed is less than current (v_b < v_r)")
    void testMinimumDriftWhenCurrentExceedsBoatSpeed() {
        // d = 300 m, v_r = 5 m/s, v_b = 3 m/s -> sin(theta) = 3/5 = 0.6
        // cos(theta) = 0.8
        // min drift = 300 * sqrt(25/9 - 1) = 300 * (4/3) = 400 m
        // t = 300 / (3 * 0.8) = 300 / 2.4 = 125 s
        RiverBoatResult result = RiverBoatSolver.solveShortestPath(300.0, 5.0, 3.0);

        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_UNACHIEVABLE, result.crossingType());
        assertTrue(result.reachesOppositeBank());
        assertEquals(125.0, result.crossingTime().getAsDouble(), 1e-9);
        assertEquals(400.0, result.downstreamDrift().getAsDouble(), 1e-9);
        assertEquals(Math.asin(0.6), result.headingAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("General arbitrary heading crossing calculation")
    void testGeneralHeadingCrossing() {
        // d = 100 m, v_r = 2 m/s, v_b = 4 m/s, theta = 30 deg (pi / 6)
        // vy = 4 * cos(30) = 4 * (sqrt(3)/2) = 2 * sqrt(3)
        // t = 100 / (2 * sqrt(3)) = 50 / sqrt(3) s
        // vx = 2 - 4 * sin(30) = 2 - 2 = 0 m/s -> drift = 0!
        RiverBoatResult result = RiverBoatSolver.solveForHeading(100.0, 2.0, 4.0, Math.PI / 6.0);

        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE, result.crossingType());
        assertTrue(result.reachesOppositeBank());
        assertEquals(50.0 / Math.sqrt(3.0), result.crossingTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.downstreamDrift().getAsDouble(), 1e-9);
    }
}

