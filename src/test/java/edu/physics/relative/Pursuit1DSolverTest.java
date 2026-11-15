package edu.physics.relative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pursuit1DSolver - 1D Relative Pursuit and Root Finding Tests")
class Pursuit1DSolverTest {

    @Test
    @DisplayName("Initial co-location (x0 = 0) takes precedence as primary collision at t = 0 with subsequent root preserved")
    void testInitialCollisionPrecedenceWithSubsequentRoot() {
        // x0 = 0, v_rel0 = 10 m/s, a_rel = -5 m/s^2
        // Both start co-located at t = 0.
        // Body B moves ahead, decelerates, and returns to cross at t = -2(10)/(-5) = 4.0 s
        Pursuit1DResult result = Pursuit1DSolver.solve(0.0, 10.0, -5.0, 10.0);

        assertEquals(Pursuit1DStatus.INITIAL_COLLISION_CO_LOCATED, result.status());
        assertTrue(result.hasCollision());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(0.0, result.primaryCollisionTime().getAsDouble(), 1e-9);

        // Subsequent future root must be captured for educational explanation
        assertTrue(result.subsequentCollisionTime().isPresent());
        assertEquals(4.0, result.subsequentCollisionTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Permanent co-location (x0 = 0, v = 0, a = 0)")
    void testPermanentCoLocation() {
        Pursuit1DResult result = Pursuit1DSolver.solve(0.0, 0.0, 0.0, 10.0);

        assertEquals(Pursuit1DStatus.PERMANENT_CO_LOCATION, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(0.0, result.primaryCollisionTime().getAsDouble(), 1e-9);
        assertFalse(result.subsequentCollisionTime().isPresent());
        assertEquals(0.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Constant non-zero separation (x0 != 0, v = 0, a = 0)")
    void testConstantNonzeroSeparation() {
        Pursuit1DResult result = Pursuit1DSolver.solve(45.0, 0.0, 0.0, 10.0);

        assertEquals(Pursuit1DStatus.CONSTANT_NONZERO_SEPARATION, result.status());
        assertFalse(result.hasCollision());
        assertEquals(45.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Zero acceleration linear collision (a = 0, v != 0)")
    void testLinearCollision() {
        // x0 = 100 m, v_rel0 = -20 m/s, a = 0 -> t = 5.0 s
        Pursuit1DResult result = Pursuit1DSolver.solve(100.0, -20.0, 0.0, 10.0);

        assertEquals(Pursuit1DStatus.LINEAR_COLLISION, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(5.0, result.primaryCollisionTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Glancing contact with repeated root (Delta = 0, v_rel = 0 at contact)")
    void testGlancingContact() {
        // x0 = 20 m, v_rel0 = -20 m/s, a_rel = 10 m/s^2
        // Delta = 400 - 2 * 10 * 20 = 0
        // Repeated root at t = -(-20) / 10 = 2.0 s
        Pursuit1DResult result = Pursuit1DSolver.solve(20.0, -20.0, 10.0, 5.0);

        assertEquals(Pursuit1DStatus.GLANCING_CONTACT, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(2.0, result.primaryCollisionTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("No collision with positive turning point minimum separation (Delta < 0)")
    void testNoCollisionMinimumSeparation() {
        // x0 = 30 m, v_rel0 = -20 m/s, a_rel = 10 m/s^2
        // Delta = 400 - 2 * 10 * 30 = -200 < 0 (no collision)
        // Turning point t_ext = -(-20) / 10 = 2.0 s
        // Minimum separation at t = 2: x(2) = 30 - 20(2) + 0.5(10)(4) = 30 - 40 + 20 = 10 m
        Pursuit1DResult result = Pursuit1DSolver.solve(30.0, -20.0, 10.0, 5.0);

        assertEquals(Pursuit1DStatus.NO_COLLISION_MINIMUM_SEPARATION, result.status());
        assertFalse(result.hasCollision());
        assertEquals(10.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Accelerated pursuit first pass collision (Delta > 0)")
    void testCollisionFirstPass() {
        // x0 = 10 m, v_rel0 = -20 m/s, a_rel = 10 m/s^2
        // Delta = 400 - 200 = 200 -> sqrt(200) approx 14.1421356
        // Roots: 0.5 * 10 t^2 - 20 t + 10 = 0 -> 5 t^2 - 20 t + 10 = 0 -> t^2 - 4 t + 2 = 0
        // t = 2 - sqrt(2) approx 0.5857864 s, t_late = 2 + sqrt(2) approx 3.4142136 s
        Pursuit1DResult result = Pursuit1DSolver.solve(10.0, -20.0, 10.0, 5.0);

        assertEquals(Pursuit1DStatus.COLLISION_FIRST_PASS, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(2.0 - Math.sqrt(2.0), result.primaryCollisionTime().getAsDouble(), 1e-9);
        assertEquals(0.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Collision point occurs after active interval expires: status is NO_COLLISION_INTERVAL_EXPIRED")
    void testIntervalExpired() {
        // x0 = 100 m, v_rel0 = -20 m/s, a = 0 -> collision at t = 5.0 s
        // But active interval T = 3.0 s!
        // Separation at T = 3.0 s: 100 - 20 * 3 = 40 m
        Pursuit1DResult result = Pursuit1DSolver.solve(100.0, -20.0, 0.0, 3.0);

        assertEquals(Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED, result.status());
        assertFalse(result.hasCollision());
        assertEquals(40.0, result.minimumSeparationInterval(), 1e-9);
    }

    @Test
    @DisplayName("Numerically stable roots when B is large and nearly cancels sqrt(Delta)")
    void testNumericalStabilitySmallC() {
        // Catastrophic cancellation benchmark:
        // A = 1, B = 1e6, C = 1
        // One root is very large (~ -1e6), other root is very small (-C/B approx -1e-6)
        // With x0 = 1.0, v_rel0 = -1e6, a_rel = 2.0 (so A = 1.0)
        // 0.5 * 2 * t^2 - 1e6 * t + 1 = 0 -> t^2 - 1e6 t + 1 = 0
        // Roots are t_early approx 1e-6, t_late approx 1e6
        Pursuit1DResult result = Pursuit1DSolver.solve(1.0, -1e6, 2.0, 10.0);

        assertEquals(Pursuit1DStatus.COLLISION_FIRST_PASS, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        double tEarly = result.primaryCollisionTime().getAsDouble();
        // Exact root t = (1e6 - sqrt(1e12 - 4)) / 2 approx 1.0 / 1e6 = 1e-6
        assertEquals(1e-6, tEarly, 1e-11);
    }

    @Test
    @DisplayName("Slightly negative discriminant does NOT manufacture a collision root, but evaluates minimum separation")
    void testSlightlyNegativeDiscriminantNoManufacturedCollision() {
        // Glancing is at x0 = 20 m, v_rel0 = -20 m/s, a_rel = 10 m/s^2 (Delta = 0)
        // Set x0 slightly larger: 20.000000001 m -> Delta = 400 - 400.00000002 = -2e-8 < 0
        // Bodies miss by d_min = 2e-8 / 20 = 1e-9 meters
        Pursuit1DResult result = Pursuit1DSolver.solve(20.000000001, -20.0, 10.0, 5.0);

        // Must NOT be GLANCING_CONTACT! Must NOT have collision!
        assertEquals(Pursuit1DStatus.NO_COLLISION_MINIMUM_SEPARATION, result.status());
        assertFalse(result.hasCollision());
        assertFalse(result.primaryCollisionTime().isPresent());
        assertTrue(result.minimumSeparationInterval() > 0.0);
        assertEquals(1e-9, result.minimumSeparationInterval(), 1e-12);

        // However, tolerance-based physical contact distinguishes that surface contact occurred
        assertTrue(result.hasContact(0.2));
    }

    @Test
    @DisplayName("Small non-zero initial separation moving apart does not collide at t = 0")
    void testSmallNonzeroSeparationMovingApart() {
        // x0 = 1e-10 m, v_rel0 = +10 m/s, a_rel = 0
        // Bodies are separating; distance increases for all t > 0
        Pursuit1DResult result = Pursuit1DSolver.solve(1e-10, 10.0, 0.0, 5.0);

        assertEquals(Pursuit1DStatus.NO_COLLISION_DIVERGING, result.status());
        assertFalse(result.hasCollision());
        assertFalse(result.primaryCollisionTime().isPresent());
        assertEquals(1e-10, result.minimumSeparationInterval(), 1e-15);
    }

    @Test
    @DisplayName("Small non-zero initial separation approaching collides at positive time t > 0")
    void testSmallNonzeroSeparationApproaching() {
        // x0 = 1e-10 m, v_rel0 = -10 m/s, a_rel = 0 -> t = 1e-11 s > 0
        Pursuit1DResult result = Pursuit1DSolver.solve(1e-10, -10.0, 0.0, 5.0);

        assertEquals(Pursuit1DStatus.LINEAR_COLLISION, result.status());
        assertTrue(result.hasCollision());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(1e-11, result.primaryCollisionTime().getAsDouble(), 1e-15);
    }

    @Test
    @DisplayName("Small positive discriminant with distinguishable time separation preserves TWO distinct collision roots")
    void testSmallPositiveDiscriminantPreservesTwoDistinctRoots() {
        // x0 = 19.999999999 m (1 nm closer than glancing separation x0 = 20 m)
        // v_rel0 = -20 m/s, a_rel = 10 m/s^2
        // Delta = 400 - 399.99999998 = 2e-8 > 0
        // Time separation between roots: delta_t = 2 * sqrt(2e-8) / 10 approx 28.28 microseconds
        // Must NOT collapse to GLANCING_CONTACT; must preserve BOTH first pass and subsequent pass!
        Pursuit1DResult result = Pursuit1DSolver.solve(19.999999999, -20.0, 10.0, 5.0);

        assertEquals(Pursuit1DStatus.COLLISION_FIRST_PASS, result.status());
        assertTrue(result.hasCollision());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertTrue(result.subsequentCollisionTime().isPresent());

        double t1 = result.primaryCollisionTime().getAsDouble();
        double t2 = result.subsequentCollisionTime().getAsDouble();
        assertTrue(t2 > t1);
        assertEquals(2.0, 0.5 * (t1 + t2), 1e-9);
        assertEquals(2.8284e-5, t2 - t1, 1e-7);
    }

    @Test
    @DisplayName("Sub-picosecond root time separation correctly coalesces into GLANCING_CONTACT")
    void testSubPicosecondRootsCoalesceIntoGlancingContact() {
        // Delta = 1e-26, a_rel = 10 -> delta_t = 2e-14 s (< 1e-12 s time tolerance)
        // To achieve Delta = 1e-26 with v_rel0 = -20, a_rel = 10:
        // x0 = (400 - 1e-26) / 20 = 20 - 5e-28 approx 20.0
        // Passing exact glancing x0 = 20.0 produces Delta = 0.0 -> GLANCING_CONTACT
        Pursuit1DResult result = Pursuit1DSolver.solve(20.0, -20.0, 10.0, 5.0);

        assertEquals(Pursuit1DStatus.GLANCING_CONTACT, result.status());
        assertTrue(result.primaryCollisionTime().isPresent());
        assertEquals(2.0, result.primaryCollisionTime().getAsDouble(), 1e-9);
        assertFalse(result.subsequentCollisionTime().isPresent());
    }

    @Test
    @DisplayName("Subsequent collision time is ONLY populated when later root falls within active interval")
    void testSubsequentCollisionTimeIntervalFiltering() {
        // x0 = 10 m, v_rel0 = -20 m/s, a_rel = 10 m/s^2
        // Roots: 5 t^2 - 20 t + 10 = 0 -> t1 = 2 - sqrt(2) approx 0.5858 s, t2 = 2 + sqrt(2) approx 3.4142 s

        // Scenario 1: Active interval T = 2.0 s. t1 < T < t2
        Pursuit1DResult resultClamped = Pursuit1DSolver.solve(10.0, -20.0, 10.0, 2.0);
        assertEquals(Pursuit1DStatus.COLLISION_FIRST_PASS, resultClamped.status());
        assertTrue(resultClamped.primaryCollisionTime().isPresent());
        assertEquals(2.0 - Math.sqrt(2.0), resultClamped.primaryCollisionTime().getAsDouble(), 1e-9);
        // Later root t2 (3.4142 s) is OUTSIDE [0, 2.0], so subsequentCollisionTime MUST be empty!
        assertFalse(resultClamped.subsequentCollisionTime().isPresent());

        // Scenario 2: Active interval T = 5.0 s. t1 < t2 <= T
        Pursuit1DResult resultExtended = Pursuit1DSolver.solve(10.0, -20.0, 10.0, 5.0);
        assertEquals(Pursuit1DStatus.COLLISION_FIRST_PASS, resultExtended.status());
        assertTrue(resultExtended.primaryCollisionTime().isPresent());
        assertEquals(2.0 - Math.sqrt(2.0), resultExtended.primaryCollisionTime().getAsDouble(), 1e-9);
        // Later root t2 (3.4142 s) is INSIDE [0, 5.0], so subsequentCollisionTime MUST be populated!
        assertTrue(resultExtended.subsequentCollisionTime().isPresent());
        assertEquals(2.0 + Math.sqrt(2.0), resultExtended.subsequentCollisionTime().getAsDouble(), 1e-9);

        // Scenario 3: Initial co-location (x0 = 0) with subsequent root t_sub = 4.0 s
        // With T = 3.0 s (< 4.0 s): subsequent root must be empty!
        Pursuit1DResult coLocatedClamped = Pursuit1DSolver.solve(0.0, 10.0, -5.0, 3.0);
        assertEquals(Pursuit1DStatus.INITIAL_COLLISION_CO_LOCATED, coLocatedClamped.status());
        assertEquals(0.0, coLocatedClamped.primaryCollisionTime().getAsDouble(), 1e-9);
        assertFalse(coLocatedClamped.subsequentCollisionTime().isPresent());

        // With T = 5.0 s (>= 4.0 s): subsequent root must be populated!
        Pursuit1DResult coLocatedExtended = Pursuit1DSolver.solve(0.0, 10.0, -5.0, 5.0);
        assertEquals(Pursuit1DStatus.INITIAL_COLLISION_CO_LOCATED, coLocatedExtended.status());
        assertEquals(0.0, coLocatedExtended.primaryCollisionTime().getAsDouble(), 1e-9);
        assertTrue(coLocatedExtended.subsequentCollisionTime().isPresent());
        assertEquals(4.0, coLocatedExtended.subsequentCollisionTime().getAsDouble(), 1e-9);
    }
}

