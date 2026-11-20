package edu.physics.relative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LandingSolver - Analytical Landing and Surface Intersection Tests")
class LandingSolverTest {

    @Test
    @DisplayName("Elevated horizontal launch hits surface downward")
    void testElevatedHorizontalLaunch() {
        // y0 = 20 m, vy0 = 0, ySurface = 0, g = 10 -> t = sqrt(2 * 20 / 10) = 2.0 s
        LandingCalculationResult result = LandingSolver.solve(20.0, 0.0, 0.0, 10.0, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertTrue(result.physicalLandingTime().isPresent());
        assertEquals(2.0, result.physicalLandingTime().getAsDouble(), 1e-9);
        assertEquals(2.0, result.firstDownwardCrossingTime().getAsDouble(), 1e-9);
        assertEquals(2.0, result.firstFutureIntersectionTime().getAsDouble(), 1e-9);
        assertFalse(result.tangentialContactTime().isPresent());
    }

    @Test
    @DisplayName("Elevated downward launch hits surface downward")
    void testElevatedDownwardLaunch() {
        // y0 = 15 m, vy0 = -5 m/s, ySurface = 0, g = 10
        // 5 t^2 + 5 t - 15 = 0 -> t^2 + t - 3 = 0 -> t = (-1 + sqrt(1 + 12)) / 2 = (-1 + sqrt(13)) / 2 approx 1.3027756 s
        LandingCalculationResult result = LandingSolver.solve(15.0, -5.0, 0.0, 10.0, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertTrue(result.physicalLandingTime().isPresent());
        double expected = (-1.0 + Math.sqrt(13.0)) / 2.0;
        assertEquals(expected, result.physicalLandingTime().getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("Ground-level upward launch does not land at t = 0; lands at forward downward crossing")
    void testGroundLevelUpwardLaunch() {
        // y0 = 0, vy0 = 25 m/s, g = 9.81 -> t = 2 * 25 / 9.81 approx 5.096839959 s
        LandingCalculationResult result = LandingSolver.solve(0.0, 25.0, 0.0, 9.81, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertTrue(result.physicalLandingTime().isPresent());
        double expected = 50.0 / 9.81;
        assertEquals(expected, result.physicalLandingTime().getAsDouble(), 1e-9);
        assertEquals(expected, result.firstDownwardCrossingTime().getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("Ground-level downward or resting launch terminates immediately at t = 0")
    void testGroundLevelRestingLaunch() {
        LandingCalculationResult result = LandingSolver.solve(0.0, 0.0, 0.0, 9.81, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertEquals(0.0, result.physicalLandingTime().getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("Sub-surface launch with apex exactly touching surface is classified as TANGENTIAL_CONTACT, not downward crossing")
    void testSubSurfaceTangentialContact() {
        // y0 = -5 m, vy0 = 10 m/s, ySurface = 0, g = 10 m/s^2
        // delta = 100 - 2 * 10 * 5 = 0 -> touches at t = 1.0 s with v_y = 0
        LandingCalculationResult result = LandingSolver.solve(-5.0, 10.0, 0.0, 10.0, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.TANGENTIAL_CONTACT, result.classification());
        assertTrue(result.tangentialContactTime().isPresent());
        assertEquals(1.0, result.tangentialContactTime().getAsDouble(), 1e-9);
        assertTrue(result.physicalLandingTime().isPresent());
        assertEquals(1.0, result.physicalLandingTime().getAsDouble(), 1e-9);
        assertFalse(result.firstDownwardCrossingTime().isPresent()); // Not a downward crossing!
    }

    @Test
    @DisplayName("Sub-surface launch with sufficient speed crosses upward and lands downward")
    void testSubSurfaceEmergenceAndLanding() {
        // y0 = -5 m, vy0 = 12 m/s, ySurface = 0, g = 10 m/s^2
        // Delta = 144 - 100 = 44 -> sqrt(44) approx 6.63324958
        // t_up = (12 - sqrt(44)) / 10 approx 0.536675 s
        // t_down = (12 + sqrt(44)) / 10 approx 1.863325 s
        LandingCalculationResult result = LandingSolver.solve(-5.0, 12.0, 0.0, 10.0, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertTrue(result.firstFutureIntersectionTime().isPresent());
        assertEquals((12.0 - Math.sqrt(44.0)) / 10.0, result.firstFutureIntersectionTime().getAsDouble(), 1e-9);

        assertTrue(result.firstDownwardCrossingTime().isPresent());
        assertEquals((12.0 + Math.sqrt(44.0)) / 10.0, result.firstDownwardCrossingTime().getAsDouble(), 1e-9);
        assertEquals((12.0 + Math.sqrt(44.0)) / 10.0, result.physicalLandingTime().getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("Sub-surface launch with apex below surface plane produces NO_LANDING_APEX_BELOW")
    void testSubSurfaceApexBelow() {
        // y0 = -5 m, vy0 = 5 m/s, ySurface = 0, g = 10 m/s^2 -> max height = -5 + 25/20 = -3.75 m < 0
        LandingCalculationResult result = LandingSolver.solve(-5.0, 5.0, 0.0, 10.0, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.NO_LANDING_APEX_BELOW, result.classification());
        assertFalse(result.physicalLandingTime().isPresent());
    }

    @Test
    @DisplayName("Zero gravity motion: downward linear crossing vs parallel")
    void testZeroGravity() {
        // y0 = 10 m, vy0 = -2 m/s, g = 0 -> t = 5 s
        LandingCalculationResult down = LandingSolver.solve(10.0, -2.0, 0.0, 0.0, RelativeTolerancePolicy.DEFAULT);
        assertEquals(LandingClassification.DOWNWARD_CROSSING, down.classification());
        assertEquals(5.0, down.physicalLandingTime().getAsDouble(), 1e-9);

        // y0 = 10 m, vy0 = 0 m/s, g = 0 -> parallel
        LandingCalculationResult parallel = LandingSolver.solve(10.0, 0.0, 0.0, 0.0, RelativeTolerancePolicy.DEFAULT);
        assertEquals(LandingClassification.NO_LANDING_PARALLEL, parallel.classification());
        assertFalse(parallel.physicalLandingTime().isPresent());
    }

    @Test
    @DisplayName("Negative gravity returns INVALID_GRAVITY")
    void testNegativeGravity() {
        LandingCalculationResult result = LandingSolver.solve(10.0, 5.0, 0.0, -9.81, RelativeTolerancePolicy.DEFAULT);
        assertEquals(LandingClassification.INVALID_GRAVITY, result.classification());
        assertFalse(result.physicalLandingTime().isPresent());
    }

    @Test
    @DisplayName("Tiny elevated launch above ground falls under gravity and has positive landing time")
    void testTinyElevatedDropHasPositiveFallTime() {
        // y0 = 1e-10 m, vy0 = 0, g = 9.81
        // Fall time = sqrt(2 * 1e-10 / 9.81) approx 4.515236e-6 s > 0
        LandingCalculationResult result = LandingSolver.solve(1e-10, 0.0, 0.0, 9.81, RelativeTolerancePolicy.DEFAULT);

        assertEquals(LandingClassification.DOWNWARD_CROSSING, result.classification());
        assertTrue(result.physicalLandingTime().isPresent());
        double expected = Math.sqrt(2.0 * 1e-10 / 9.81);
        assertEquals(expected, result.physicalLandingTime().getAsDouble(), 1e-12);
    }
}

