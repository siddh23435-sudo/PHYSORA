package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainManSolver - Apparent Rain Velocity and Umbrella Tilt Tests")
class RainManSolverTest {

    @Test
    @DisplayName("Vertical rain observed by stationary person requires upright umbrella (phi = 0)")
    void testStationaryPersonVerticalRain() {
        Vector2D rain = new Vector2D(0.0, -10.0);
        Vector2D person = Vector2D.ZERO;

        RainManResult result = RainManSolver.solve(rain, person);

        assertEquals(0.0, result.apparentRainVelocity().x(), 1e-9);
        assertEquals(-10.0, result.apparentRainVelocity().y(), 1e-9);
        assertEquals(10.0, result.apparentRainSpeed(), 1e-9);
        assertEquals(0.0, result.umbrellaAngleRad(), 1e-9);

        Vector2D umbrellaDir = result.umbrellaOrientationVector();
        assertEquals(0.0, umbrellaDir.x(), 1e-9);
        assertEquals(1.0, umbrellaDir.y(), 1e-9); // Points straight up
    }

    @Test
    @DisplayName("Person running forward in vertical rain must tilt umbrella forward")
    void testMovingPersonVerticalRain() {
        // Rain falling vertically at 10 m/s: (0, -10)
        // Person walking forward at 10 m/s: (10, 0)
        // v_{r/m} = (-10, -10) -> apparent speed = 10 * sqrt(2) approx 14.142 m/s
        // Umbrella vector = (10, 10) -> angle from +j is +45 deg (pi / 4)
        Vector2D rain = new Vector2D(0.0, -10.0);
        Vector2D person = new Vector2D(10.0, 0.0);

        RainManResult result = RainManSolver.solve(rain, person);

        assertEquals(-10.0, result.apparentRainVelocity().x(), 1e-9);
        assertEquals(-10.0, result.apparentRainVelocity().y(), 1e-9);
        assertEquals(10.0 * Math.sqrt(2.0), result.apparentRainSpeed(), 1e-9);
        assertEquals(Math.PI / 4.0, result.umbrellaAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("Classic JEE Inverse Problem: Deducing actual rain velocity from 2 observations")
    void testClassicJeeInverseRainMan() {
        // Man walks at 3 m/s, rain appears vertical (phi1 = 0)
        // Man speeds up to 6 m/s, rain appears at 45 deg (phi2 = pi / 4, tan = 1)
        // Expected rain velocity: v_rx = 3 m/s, v_ry = -3 m/s
        Vector2D deducedRain = RainManSolver.solveInverseFromTwoObservations(3.0, 0.0, 6.0, Math.PI / 4.0);

        assertEquals(3.0, deducedRain.x(), 1e-9);
        assertEquals(-3.0, deducedRain.y(), 1e-9);

        // Verification: plug back into forward solver
        RainManResult obs1 = RainManSolver.solve(deducedRain, new Vector2D(3.0, 0.0));
        assertEquals(0.0, obs1.umbrellaAngleRad(), 1e-9);

        RainManResult obs2 = RainManSolver.solve(deducedRain, new Vector2D(6.0, 0.0));
        assertEquals(Math.PI / 4.0, obs2.umbrellaAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("JEE Inverse with horizontal wind: correctly recovers non-zero v_rx and v_ry")
    void testInverseWithHorizontalWind() {
        // True rain: v_rx = -2 m/s, v_ry = -4 m/s (v_down = 4 m/s)
        // Obs 1: v_person1 = 2 m/s -> tan(phi1) = (2 - (-2)) / 4 = 1.0 -> phi1 = pi/4 (45 deg)
        // Obs 2: v_person2 = 6 m/s -> tan(phi2) = (6 - (-2)) / 4 = 2.0 -> phi2 = atan(2)
        double phi1 = Math.PI / 4.0;
        double phi2 = Math.atan(2.0);

        Vector2D deducedRain = RainManSolver.solveInverseFromTwoObservations(2.0, phi1, 6.0, phi2);

        assertEquals(-2.0, deducedRain.x(), 1e-9);
        assertEquals(-4.0, deducedRain.y(), 1e-9);

        // Verify with forward solver
        RainManResult forward1 = RainManSolver.solve(deducedRain, new Vector2D(2.0, 0.0));
        assertEquals(phi1, forward1.umbrellaAngleRad(), 1e-9);

        RainManResult forward2 = RainManSolver.solve(deducedRain, new Vector2D(6.0, 0.0));
        assertEquals(phi2, forward2.umbrellaAngleRad(), 1e-9);
    }
}

