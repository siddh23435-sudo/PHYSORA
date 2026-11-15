package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiverBoatSimulationModel Domain Contract Tests")
class RiverBoatSimulationModelTest {

    private static final double EPSILON = 1e-6;

    @Test
    @DisplayName("Shortest time preset directs boat perpendicular to flow (theta = 0)")
    void testShortestTimePreset() {
        RiverBoatSimulationModel model = RiverBoatSimulationModel.presetShortestTime(100.0, 3.0, 5.0);

        assertEquals(0.0, model.headingAngleRad(), EPSILON);
        assertEquals(0.0, model.headingAngleDeg(), EPSILON);

        // Relative velocity: purely in +y
        Vector2D vRel = model.velocityBoatRelative();
        assertEquals(0.0, vRel.x(), EPSILON);
        assertEquals(5.0, vRel.y(), EPSILON);

        // Ground velocity: (3, 5)
        Vector2D vGround = model.velocityBoatGround();
        assertEquals(3.0, vGround.x(), EPSILON);
        assertEquals(5.0, vGround.y(), EPSILON);

        // Analytical crossing: t_min = 100 / 5 = 20 s, drift = 3 * 20 = 60 m
        assertTrue(model.analyticalResult().crossingTime().isPresent());
        assertEquals(20.0, model.analyticalResult().crossingTime().getAsDouble(), EPSILON);
        assertEquals(60.0, model.analyticalResult().downstreamDrift().getAsDouble(), EPSILON);
        assertEquals(RiverBoatCrossingType.SHORTEST_TIME, model.analyticalResult().crossingType());

        // Mid-crossing position at t = 10 s: (30, 50)
        Vector2D midPos = model.boatPosition(10.0);
        assertEquals(30.0, midPos.x(), EPSILON);
        assertEquals(50.0, midPos.y(), EPSILON);
        assertFalse(model.isLanded(10.0));

        // Landing position at t = 20 s: (60, 100)
        Vector2D landPos = model.boatPosition(20.0);
        assertEquals(60.0, landPos.x(), EPSILON);
        assertEquals(100.0, landPos.y(), EPSILON);
        assertTrue(model.isLanded(20.0));

        // Post-landing clamp at t = 25 s: stays at (60, 100)
        Vector2D postLandPos = model.boatPosition(25.0);
        assertEquals(60.0, postLandPos.x(), EPSILON);
        assertEquals(100.0, postLandPos.y(), EPSILON);
        assertTrue(model.isLanded(25.0));
    }

    @Test
    @DisplayName("Zero drift preset when boat speed exceeds current (vb > u)")
    void testZeroDriftAchievablePreset() {
        // w = 100 m, u = 3.0 m/s, vb = 5.0 m/s -> sin(theta) = 3/5 = 0.6
        RiverBoatSimulationModel model = RiverBoatSimulationModel.presetShortestPathOrMinDrift(100.0, 3.0, 5.0);

        double expectedTheta = Math.asin(3.0 / 5.0);
        assertEquals(expectedTheta, model.headingAngleRad(), EPSILON);
        assertEquals(Math.toDegrees(expectedTheta), model.headingAngleDeg(), EPSILON);

        // Relative velocity: vx_rel = -5 * 0.6 = -3, vy_rel = 5 * 0.8 = 4
        Vector2D vRel = model.velocityBoatRelative();
        assertEquals(-3.0, vRel.x(), EPSILON);
        assertEquals(4.0, vRel.y(), EPSILON);

        // Ground velocity: vx = 3 - 3 = 0, vy = 4
        Vector2D vGround = model.velocityBoatGround();
        assertEquals(0.0, vGround.x(), EPSILON);
        assertEquals(4.0, vGround.y(), EPSILON);

        // Analytical crossing: t = 100 / 4 = 25 s, drift = 0 m
        assertTrue(model.analyticalResult().crossingTime().isPresent());
        assertEquals(25.0, model.analyticalResult().crossingTime().getAsDouble(), EPSILON);
        assertEquals(0.0, model.analyticalResult().downstreamDrift().getAsDouble(), EPSILON);
        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE, model.analyticalResult().crossingType());

        // Landed point is exactly at (0, 100)
        Vector2D landPos = model.boatPosition(25.0);
        assertEquals(0.0, landPos.x(), EPSILON);
        assertEquals(100.0, landPos.y(), EPSILON);
    }

    @Test
    @DisplayName("Minimum drift preset when boat speed is less than current (vb < u)")
    void testMinDriftWhenBoatSlowerThanCurrent() {
        // w = 100 m, u = 5.0 m/s, vb = 3.0 m/s -> sin(theta) = 3/5 = 0.6
        RiverBoatSimulationModel model = RiverBoatSimulationModel.presetShortestPathOrMinDrift(100.0, 5.0, 3.0);

        double expectedTheta = Math.asin(3.0 / 5.0);
        assertEquals(expectedTheta, model.headingAngleRad(), EPSILON);

        // Relative velocity: vx_rel = -3 * 0.6 = -1.8, vy_rel = 3 * 0.8 = 2.4
        Vector2D vRel = model.velocityBoatRelative();
        assertEquals(-1.8, vRel.x(), EPSILON);
        assertEquals(2.4, vRel.y(), EPSILON);

        // Ground velocity: vx = 5 - 1.8 = 3.2, vy = 2.4
        Vector2D vGround = model.velocityBoatGround();
        assertEquals(3.2, vGround.x(), EPSILON);
        assertEquals(2.4, vGround.y(), EPSILON);

        // Analytical: t = 100 / 2.4 = 41.666667 s
        // minDrift = 100 * sqrt((5/3)^2 - 1) = 100 * 4/3 = 133.333333 m
        assertTrue(model.analyticalResult().crossingTime().isPresent());
        assertEquals(100.0 / 2.4, model.analyticalResult().crossingTime().getAsDouble(), EPSILON);
        assertEquals(100.0 * (4.0 / 3.0), model.analyticalResult().downstreamDrift().getAsDouble(), EPSILON);
        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_UNACHIEVABLE, model.analyticalResult().crossingType());
    }

    @Test
    @DisplayName("Equal speed boundary preset (vb = u) shows stationary upstream hold")
    void testEqualSpeedBoundary() {
        RiverBoatSimulationModel model = RiverBoatSimulationModel.presetEqualSpeedBoundary(100.0, 4.0);

        assertEquals(Math.PI / 2.0, model.headingAngleRad(), EPSILON);
        assertEquals(90.0, model.headingAngleDeg(), EPSILON);

        // Heading directly upstream: vx_rel = -4, vy_rel = 0
        Vector2D vRel = model.velocityBoatRelative();
        assertEquals(-4.0, vRel.x(), EPSILON);
        assertEquals(0.0, vRel.y(), EPSILON);

        // Ground velocity: vx = 4 - 4 = 0, vy = 0 -> stationary hold!
        Vector2D vGround = model.velocityBoatGround();
        assertEquals(0.0, vGround.x(), EPSILON);
        assertEquals(0.0, vGround.y(), EPSILON);

        // Crossing never completes in finite time
        assertFalse(model.analyticalResult().crossingTime().isPresent());
        assertFalse(model.analyticalResult().downstreamDrift().isPresent());
        assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC, model.analyticalResult().crossingType());
        assertFalse(model.isLanded(100.0));
    }

    @Test
    @DisplayName("Input validations reject invalid parameters")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new RiverBoatSimulationModel(-10.0, 3.0, 5.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RiverBoatSimulationModel(100.0, -1.0, 5.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RiverBoatSimulationModel(100.0, 3.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RiverBoatSimulationModel(100.0, 3.0, 5.0, Double.NaN));
    }
}
