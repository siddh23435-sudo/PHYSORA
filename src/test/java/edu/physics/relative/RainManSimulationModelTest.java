package edu.physics.relative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainManSimulationModel - Physical State and Umbrella Alignment Tests")
class RainManSimulationModelTest {

    @Test
    @DisplayName("Default scenario has vDown=8, vRainX=0, vPerson=3, vertical umbrella")
    void testDefaultScenario() {
        RainManSimulationModel model = RainManSimulationModel.defaultScenario();

        assertEquals(8.0, model.vDown(), 1e-9);
        assertEquals(0.0, model.vRainX(), 1e-9);
        assertEquals(3.0, model.vPerson(), 1e-9);
        assertEquals(0.0, model.umbrellaAngleRad(), 1e-9);

        // Ground rain = (0, -8), person = (3, 0)
        // v_{r/m} = (-3, -8) -> speed = sqrt(9 + 64) = sqrt(73) approx 8.544 m/s
        assertEquals(-3.0, model.apparentRainVelocity().x(), 1e-9);
        assertEquals(-8.0, model.apparentRainVelocity().y(), 1e-9);
        assertEquals(Math.sqrt(73.0), model.apparentRainSpeed(), 1e-9);

        // Optimal umbrella tilt: atan2(3 - 0, 8) = atan2(3, 8) approx 0.35877 rad (20.556 deg)
        double expectedAngle = Math.atan2(3.0, 8.0);
        assertEquals(expectedAngle, model.optimalAngleRad(), 1e-9);
        assertEquals(Math.toDegrees(expectedAngle), model.optimalAngleDeg(), 1e-6);

        // With vertical umbrella (0 deg), error is approx 20.56 deg -> MISALIGNED (> 12 deg)
        assertEquals(model.optimalAngleRad(), model.angularErrorRad(), 1e-9);
        assertEquals(RainManSimulationModel.AlignmentStatus.MISALIGNED, model.alignmentStatus());
    }

    @Test
    @DisplayName("Stationary person in vertical rain: optimal angle is exactly 0 deg and vertical umbrella is WELL_ALIGNED")
    void testStationaryVerticalRain() {
        RainManSimulationModel model = RainManSimulationModel.presetStationaryVerticalRain();

        assertEquals(0.0, model.optimalAngleRad(), 1e-9);
        assertEquals(0.0, model.optimalAngleDeg(), 1e-9);
        assertEquals(0.0, model.angularErrorDeg(), 1e-9);
        assertEquals(RainManSimulationModel.AlignmentStatus.WELL_ALIGNED, model.alignmentStatus());
    }

    @Test
    @DisplayName("Forward walking in vertical rain: optimal angle tilts forward (+x)")
    void testForwardWalkingVerticalRain() {
        RainManSimulationModel model = RainManSimulationModel.presetForwardWalkingVerticalRain();

        assertTrue(model.optimalAngleRad() > 0.0);
        assertTrue(model.optimalAngleDeg() > 0.0);
    }

    @Test
    @DisplayName("Backward walking in vertical rain: optimal angle tilts backward (-x)")
    void testBackwardWalkingVerticalRain() {
        RainManSimulationModel model = RainManSimulationModel.presetBackwardWalking();

        // vPerson = -4, vDown = 7 -> optimal tilt atan2(-4, 7) < 0
        assertTrue(model.optimalAngleRad() < 0.0);
        assertTrue(model.optimalAngleDeg() < 0.0);
        assertEquals(Math.atan2(-4.0, 7.0), model.optimalAngleRad(), 1e-9);
    }

    @Test
    @DisplayName("Tailwind matching walking speed: horizontal relative velocity is 0 and rain appears vertical")
    void testTailwindZeroRelativeHorizontal() {
        // vRainX = 4, vPerson = 4 -> relative horizontal velocity is 0
        RainManSimulationModel model = RainManSimulationModel.presetTailwindZeroRelativeHorizontal();

        assertEquals(0.0, model.apparentRainVelocity().x(), 1e-9);
        assertEquals(-6.0, model.apparentRainVelocity().y(), 1e-9);
        assertEquals(0.0, model.optimalAngleRad(), 1e-9);
        assertEquals(RainManSimulationModel.AlignmentStatus.WELL_ALIGNED, model.alignmentStatus());
    }

    @Test
    @DisplayName("Snap to optimal aligns umbrella perfectly with zero angular error")
    void testSnapToOptimal() {
        RainManSimulationModel model = RainManSimulationModel.defaultScenario();
        assertNotEquals(RainManSimulationModel.AlignmentStatus.WELL_ALIGNED, model.alignmentStatus());

        RainManSimulationModel snapped = model.snapToOptimal();
        assertEquals(snapped.optimalAngleRad(), snapped.umbrellaAngleRad(), 1e-9);
        assertEquals(0.0, snapped.angularErrorDeg(), 1e-9);
        assertEquals(RainManSimulationModel.AlignmentStatus.WELL_ALIGNED, snapped.alignmentStatus());
    }

    @Test
    @DisplayName("Alignment thresholds: <= 3 deg is WELL_ALIGNED, <= 12 deg is SLIGHTLY_MISALIGNED, > 12 deg is MISALIGNED")
    void testAlignmentThresholds() {
        // Model with optimal angle = 20.0 deg
        // tan(phi) = (vm - vrx) / vd -> set vm = 8 * tan(20 deg), vrx = 0, vd = 8
        double optimalRad = Math.toRadians(20.0);
        double vm = 8.0 * Math.tan(optimalRad);

        RainManSimulationModel well = new RainManSimulationModel(8.0, 0.0, vm, Math.toRadians(22.5)); // error = 2.5 deg
        assertEquals(RainManSimulationModel.AlignmentStatus.WELL_ALIGNED, well.alignmentStatus());

        RainManSimulationModel slight = new RainManSimulationModel(8.0, 0.0, vm, Math.toRadians(28.0)); // error = 8.0 deg
        assertEquals(RainManSimulationModel.AlignmentStatus.SLIGHTLY_MISALIGNED, slight.alignmentStatus());

        RainManSimulationModel misaligned = new RainManSimulationModel(8.0, 0.0, vm, Math.toRadians(40.0)); // error = 20.0 deg
        assertEquals(RainManSimulationModel.AlignmentStatus.MISALIGNED, misaligned.alignmentStatus());
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testInvalidParameters() {
        // vDown <= 0
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(0.0, 0.0, 3.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(-5.0, 0.0, 3.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(Double.NaN, 0.0, 3.0, 0.0));

        // Non-finite parameters
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(8.0, Double.NaN, 3.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(8.0, 0.0, Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(8.0, 0.0, 3.0, Double.NaN));

        // Angle out of [-pi/2, pi/2]
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(8.0, 0.0, 3.0, Math.toRadians(95.0)));
        assertThrows(IllegalArgumentException.class, () -> new RainManSimulationModel(8.0, 0.0, 3.0, Math.toRadians(-95.0)));
    }
}
