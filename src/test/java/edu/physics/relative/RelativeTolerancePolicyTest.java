package edu.physics.relative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RelativeTolerancePolicy - Scale-Aware Numerical Policy Tests")
class RelativeTolerancePolicyTest {

    private final RelativeTolerancePolicy policy = RelativeTolerancePolicy.DEFAULT;

    @Test
    @DisplayName("Position tolerance tests values immediately above and below threshold")
    void testPositionToleranceBoundary() {
        double tol = policy.positionTolerance(); // 1e-9

        // Below threshold -> considered zero (with unit characteristic scale)
        assertTrue(policy.isZeroPosition(tol * 0.999, 1.0));
        assertTrue(policy.isZeroPosition(-tol * 0.999, 1.0));

        // Exactly at threshold -> considered zero
        assertTrue(policy.isZeroPosition(tol, 1.0));

        // Above threshold -> not zero
        assertFalse(policy.isZeroPosition(tol * 1.001, 1.0));
        assertFalse(policy.isZeroPosition(-tol * 1.001, 1.0));
    }

    @Test
    @DisplayName("Position tolerance scales with large characteristic scale")
    void testPositionToleranceScaleAwareness() {
        double largeScale = 1e6; // 1 million meters
        double relativeTol = policy.relativeTolerance(); // 1e-12
        double expectedThreshold = largeScale * relativeTol; // 1e-6 meters

        assertTrue(policy.isZeroPosition(expectedThreshold * 0.99, largeScale));
        assertFalse(policy.isZeroPosition(expectedThreshold * 1.01, largeScale));
    }

    @Test
    @DisplayName("Velocity tolerance tests values immediately above and below threshold")
    void testVelocityToleranceBoundary() {
        double tol = policy.velocityTolerance(); // 1e-12

        // Absolute tolerance with characteristic scale 1.0
        assertTrue(policy.isZeroVelocity(tol * 0.999, 1.0));
        assertTrue(policy.isZeroVelocity(tol, 1.0));
        assertFalse(policy.isZeroVelocity(tol * 1.001, 1.0));

        // Scaled tolerance with characteristic scale 100.0 (threshold becomes 1e-10)
        double scaledTol = 100.0 * policy.relativeTolerance();
        assertTrue(policy.isZeroVelocity(scaledTol * 0.999, 100.0));
        assertFalse(policy.isZeroVelocity(scaledTol * 1.001, 100.0));
    }

    @Test
    @DisplayName("Acceleration tolerance boundary")
    void testAccelerationToleranceBoundary() {
        double tol = policy.accelerationTolerance(); // 1e-12

        // Absolute tolerance with unit scale
        assertTrue(policy.isZeroAcceleration(tol * 0.999, 1.0));
        assertTrue(policy.isZeroAcceleration(tol, 1.0));
        assertFalse(policy.isZeroAcceleration(tol * 1.001, 1.0));

        // Scaled tolerance with typical gravity scale ~10 m/s^2 (threshold = 10 * 1e-12 = 1e-11)
        double scaledTol = 10.0 * policy.relativeTolerance();
        assertTrue(policy.isZeroAcceleration(scaledTol * 0.999, 10.0));
        assertFalse(policy.isZeroAcceleration(scaledTol * 1.001, 10.0));
    }

    @Test
    @DisplayName("Discriminant tolerance scale-awareness near zero and genuine negative rejection")
    void testDiscriminantTolerance() {
        double b = 20.0;
        double fourAc = 400.0;

        // Exact zero is repeated root
        assertTrue(policy.isZeroDiscriminant(0.0, b, fourAc));

        // Positive delta within scale-aware tolerance (4e-10) is repeated root
        assertTrue(policy.isZeroDiscriminant(1e-11, b, fourAc));

        // Negative delta within machine roundoff (scale * 1e-15 = 4e-13) is indistinguishable from zero
        assertTrue(policy.isZeroDiscriminant(-1e-16, b, fourAc));

        // Genuinely negative delta beyond machine epsilon must NEVER be zero (no real roots!)
        assertFalse(policy.isZeroDiscriminant(-1e-11, b, fourAc));
        assertFalse(policy.isZeroDiscriminant(-1e-8, b, fourAc));

        // Positive delta significantly larger than threshold is distinct roots
        assertFalse(policy.isZeroDiscriminant(1e-8, b, fourAc));
    }

    @Test
    @DisplayName("Machine zero distinguishes double-precision jitter from physical scales")
    void testMachineZero() {
        assertTrue(policy.isMachineZero(1e-16, 1.0));
        assertTrue(policy.isMachineZero(-1e-16, 1.0));
        assertFalse(policy.isMachineZero(1e-10, 1.0)); // 0.1 nanometer is not machine zero
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                new RelativeTolerancePolicy(-1.0, 1e-12, 1e-12, 1e-12, 0.2, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new RelativeTolerancePolicy(1e-9, -1e-12, 1e-12, 1e-12, 0.2, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new RelativeTolerancePolicy(1e-9, 1e-12, 1e-12, 1e-12, -0.1, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new RelativeTolerancePolicy(1e-9, 1e-12, 1e-12, 1e-12, 2.0, 1.0)); // proximity < contact
    }
}
