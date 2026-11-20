package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainInvestigatorEvaluator - Two-Observation Inverse Reconstruction Tests")
class RainInvestigatorEvaluatorTest {

    @Nested
    @DisplayName("1. Benchmark & Solvable Cases")
    class SolvableCasesTests {

        @Test
        @DisplayName("Test 1: Classic JEE VALID benchmark reconstructs (3, -3) with kappa_down = sqrt(5) and ~3.90% uncertainty")
        void testClassicValidBenchmark() {
            // Man walks at 3 m/s, rain appears vertical (phi1 = 0)
            // Man walks at 6 m/s, rain appears at 45 deg (phi2 = pi / 4)
            double v1 = 3.0;
            double phi1 = 0.0;
            double v2 = 6.0;
            double phi2 = Math.PI / 4.0;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());
            assertTrue(result.reconstructedRain().isPresent());
            assertNotNull(result.reconstructedRainVelocity());

            Vector2D rain = result.reconstructedRainVelocity();
            assertEquals(3.0, rain.x(), 1e-9);
            assertEquals(-3.0, rain.y(), 1e-9);

            // Downward condition number kappa_down = sqrt(sec^4(0) + sec^4(45)) / |tan(45) - tan(0)| = sqrt(1 + 4) / 1 = sqrt(5)
            double expectedKappa = Math.sqrt(5.0);
            assertEquals(expectedKappa, result.downwardConditionNumber(), 1e-9);

            // Estimated downward relative uncertainty = kappa * (pi / 180) approx 0.039027 (3.90%)
            double expectedRelUncertainty = expectedKappa * Math.toRadians(1.0);
            assertEquals(expectedRelUncertainty, result.estimatedDownwardRelError(), 1e-9);
            assertEquals(3.90, result.estimatedDownwardRelError() * 100.0, 0.01);

            assertTrue(result.diagnosticMessage().contains("resolved reliably"));
        }

        @Test
        @DisplayName("Test 2: ILL_CONDITIONED case with close angles (30 deg vs 30.8 deg) is solvable with kappa > 25")
        void testIllConditionedBenchmark() {
            double v1 = 3.0;
            double phi1 = Math.toRadians(30.0);
            double v2 = 3.2;
            double phi2 = Math.toRadians(30.8);

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.ILL_CONDITIONED, result.status());
            assertTrue(result.isSolvable());
            assertTrue(result.reconstructedRain().isPresent());
            assertNotNull(result.reconstructedRainVelocity());

            // Calculated condition number must exceed the threshold of 25.0
            assertTrue(result.downwardConditionNumber() > 25.0,
                    "Expected kappa > 25.0 but was: " + result.downwardConditionNumber());
            assertTrue(Double.isFinite(result.downwardConditionNumber()));
            assertTrue(Double.isFinite(result.estimatedDownwardRelError()));

            assertTrue(result.diagnosticMessage().contains("highly sensitive"));
        }

        @Test
        @DisplayName("Reversed observation order produces identical reconstructed rain velocity and condition number")
        void testReversedObservationOrder() {
            double v1 = 6.0;
            double phi1 = Math.PI / 4.0;
            double v2 = 3.0;
            double phi2 = 0.0;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());
            assertEquals(3.0, result.reconstructedRainVelocity().x(), 1e-9);
            assertEquals(-3.0, result.reconstructedRainVelocity().y(), 1e-9);
            assertEquals(Math.sqrt(5.0), result.downwardConditionNumber(), 1e-9);
        }

        @Test
        @DisplayName("Negative observer walking speeds correctly reconstruct ground rain")
        void testNegativeWalkingSpeeds() {
            // True rain: v_rx = -2.0, v_ry = -4.0 (v_down = 4.0)
            // Obs 1: v1 = -6.0 m/s -> tan(phi1) = (-6 - (-2)) / 4 = -4 / 4 = -1.0 -> phi1 = -pi / 4
            // Obs 2: v2 = -2.0 m/s -> tan(phi2) = (-2 - (-2)) / 4 = 0 -> phi2 = 0
            double v1 = -6.0;
            double phi1 = -Math.PI / 4.0;
            double v2 = -2.0;
            double phi2 = 0.0;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());
            assertEquals(-2.0, result.reconstructedRainVelocity().x(), 1e-9);
            assertEquals(-4.0, result.reconstructedRainVelocity().y(), 1e-9);
        }

        @Test
        @DisplayName("Zero walking velocity as first observation reconstructs true rain")
        void testZeroWalkingVelocity() {
            // True rain: v_rx = 2.0, v_ry = -4.0
            // Obs 1: v1 = 0.0 -> tan(phi1) = (0 - 2) / 4 = -0.5 -> phi1 = atan(-0.5)
            // Obs 2: v2 = 4.0 -> tan(phi2) = (4 - 2) / 4 = +0.5 -> phi2 = atan(0.5)
            double v1 = 0.0;
            double phi1 = Math.atan(-0.5);
            double v2 = 4.0;
            double phi2 = Math.atan(0.5);

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());
            assertEquals(2.0, result.reconstructedRainVelocity().x(), 1e-9);
            assertEquals(-4.0, result.reconstructedRainVelocity().y(), 1e-9);
        }
    }

    @Nested
    @DisplayName("2. Degeneracies & Physical Inconsistencies")
    class DegenerateAndPhysicalTests {

        @Test
        @DisplayName("Test 3: Identical walking speeds return DEGENERATE_IDENTICAL_SPEEDS")
        void testIdenticalWalkingSpeeds() {
            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(4.0, 0.2, 4.0, 0.5);

            assertEquals(RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());
            assertNull(result.reconstructedRainVelocity());
            assertTrue(Double.isNaN(result.downwardConditionNumber()));
            assertTrue(Double.isNaN(result.estimatedDownwardRelError()));
        }

        @Test
        @DisplayName("Walking speeds equal within scale-aware tolerance return DEGENERATE_IDENTICAL_SPEEDS")
        void testSpeedsEqualWithinTolerance() {
            double v1 = 5.0;
            double v2 = 5.0 + 1e-13; // Within 1e-12 velocity tolerance

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, 0.2, v2, 0.5);
            assertEquals(RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS, result.status());
            assertFalse(result.isSolvable());
        }

        @Test
        @DisplayName("Test 4: Parallel observations return DEGENERATE_PARALLEL_OBSERVATIONS")
        void testParallelObservations() {
            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(2.0, 0.3, 6.0, 0.3);

            assertEquals(RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());
            assertNull(result.reconstructedRainVelocity());
            assertTrue(Double.isNaN(result.downwardConditionNumber()));
            assertTrue(Double.isNaN(result.estimatedDownwardRelError()));
        }

        @Test
        @DisplayName("Observation angles resulting in tan difference <= 1e-12 return DEGENERATE_PARALLEL_OBSERVATIONS")
        void testAnglesWithinParallelTolerance() {
            double phi1 = 0.4;
            double phi2 = phi1 + 1e-13;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(2.0, phi1, 6.0, phi2);
            assertEquals(RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS, result.status());
            assertFalse(result.isSolvable());
        }

        @Test
        @DisplayName("Test 5: Inconsistent apparent direction (calculated v_down <= 0) returns INVALID_INCONSISTENT_DIRECTION")
        void testInconsistentDirection() {
            // v2 > v1 but tan(phi2) < tan(phi1) -> v_down < 0
            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(2.0, 0.5, 6.0, 0.2);

            assertEquals(RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());
            assertNull(result.reconstructedRainVelocity());
            assertTrue(Double.isNaN(result.downwardConditionNumber()));
            assertTrue(Double.isNaN(result.estimatedDownwardRelError()));
        }
    }

    @Nested
    @DisplayName("3. Input Validation & Strict Angle Boundaries")
    class InputAndBoundaryTests {

        @ParameterizedTest(name = "Non-finite input parameter #{index}")
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        @DisplayName("Test 6: Non-finite inputs return INVALID_NON_FINITE")
        void testNonFiniteInputs(double nonFiniteVal) {
            assertEquals(RainInvestigatorStatus.INVALID_NON_FINITE,
                    RainInvestigatorEvaluator.evaluate(nonFiniteVal, 0.0, 6.0, 0.5).status());
            assertEquals(RainInvestigatorStatus.INVALID_NON_FINITE,
                    RainInvestigatorEvaluator.evaluate(3.0, nonFiniteVal, 6.0, 0.5).status());
            assertEquals(RainInvestigatorStatus.INVALID_NON_FINITE,
                    RainInvestigatorEvaluator.evaluate(3.0, 0.0, nonFiniteVal, 0.5).status());
            assertEquals(RainInvestigatorStatus.INVALID_NON_FINITE,
                    RainInvestigatorEvaluator.evaluate(3.0, 0.0, 6.0, nonFiniteVal).status());
        }

        @Test
        @DisplayName("Test 7A: Exactly at MAX_SUPPORTED_ANGLE_RAD is rejected as INVALID_ANGLE_RANGE")
        void testExactAngleBoundaryRejected() {
            double exactBoundary = RainInvestigatorEvaluator.MAX_SUPPORTED_ANGLE_RAD;

            RainInvestigatorResult res1 = RainInvestigatorEvaluator.evaluate(3.0, exactBoundary, 6.0, 0.5);
            assertEquals(RainInvestigatorStatus.INVALID_ANGLE_RANGE, res1.status());

            RainInvestigatorResult res2 = RainInvestigatorEvaluator.evaluate(3.0, -exactBoundary, 6.0, 0.5);
            assertEquals(RainInvestigatorStatus.INVALID_ANGLE_RANGE, res2.status());
        }

        @Test
        @DisplayName("Test 7B: Immediately above MAX_SUPPORTED_ANGLE_RAD is rejected as INVALID_ANGLE_RANGE")
        void testAboveAngleBoundaryRejected() {
            double aboveBoundary = RainInvestigatorEvaluator.MAX_SUPPORTED_ANGLE_RAD + 1e-7;

            RainInvestigatorResult res = RainInvestigatorEvaluator.evaluate(3.0, aboveBoundary, 6.0, 0.5);
            assertEquals(RainInvestigatorStatus.INVALID_ANGLE_RANGE, res.status());
        }

        @Test
        @DisplayName("Test 7C: Immediately below MAX_SUPPORTED_ANGLE_RAD is accepted by angle-range check")
        void testBelowAngleBoundaryAccepted() {
            double belowBoundary = RainInvestigatorEvaluator.MAX_SUPPORTED_ANGLE_RAD - 1e-7;

            RainInvestigatorResult res = RainInvestigatorEvaluator.evaluate(3.0, belowBoundary, 6.0, 0.5);
            // Must not be rejected as INVALID_ANGLE_RANGE; proceeds downstream
            assertNotEquals(RainInvestigatorStatus.INVALID_ANGLE_RANGE, res.status());
        }
    }

    @Nested
    @DisplayName("4. Numerical Failure & Overflow Protection")
    class NumericalFailureTests {

        @Test
        @DisplayName("Test 8A: Walking speed subtraction overflow produces NUMERICAL_FAILURE")
        void testSubtractionOverflowProducesNumericalFailure() {
            double v1 = -1.79e308;
            double v2 = 1.79e308;
            double phi1 = 0.1;
            double phi2 = 0.4;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.NUMERICAL_FAILURE, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());
            assertNull(result.reconstructedRainVelocity());
            assertTrue(Double.isNaN(result.downwardConditionNumber()));
            assertTrue(Double.isNaN(result.estimatedDownwardRelError()));
            assertTrue(result.diagnosticMessage().contains("overflowed"));
        }

        @Test
        @DisplayName("Test 8B: Downward-speed quotient overflow produces NUMERICAL_FAILURE (finiteness precedes sign check)")
        void testDownwardSpeedOverflowProducesNumericalFailure() {
            // v2 - v1 = 1e308 (finite)
            // D = tan(1e-8) - tan(0) approx 1e-8 > 1e-12 (passes denominator threshold)
            // vDown = 1e308 / 1e-8 = 1e316 > Double.MAX_VALUE -> Positive Infinity
            double v1 = 0.0;
            double v2 = 1.0e308;
            double phi1 = 0.0;
            double phi2 = 1.0e-8;

            RainInvestigatorResult result = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

            assertEquals(RainInvestigatorStatus.NUMERICAL_FAILURE, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());
            assertNull(result.reconstructedRainVelocity());
            assertTrue(Double.isNaN(result.downwardConditionNumber()));
            assertTrue(Double.isNaN(result.estimatedDownwardRelError()));
            assertTrue(result.diagnosticMessage().contains("Calculated downward speed overflowed"));
        }

        @Test
        @DisplayName("Test 9: Passing null RelativeTolerancePolicy throws NullPointerException at method entry")
        void testNullPolicyThrows() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                    RainInvestigatorEvaluator.evaluate(3.0, 0.0, 6.0, 0.5, null));
            assertTrue(ex.getMessage().contains("policy must not be null"));
        }
    }

    @Nested
    @DisplayName("5. Result Invariants & Record Contract Enforcement")
    class ResultInvariantsTests {

        @Test
        @DisplayName("Test 10A: Solvable result constructor enforces finite vector and non-negative condition numbers")
        void testSolvableInvariants() {
            Vector2D rain = new Vector2D(3.0, -3.0);
            RainInvestigatorResult result = RainInvestigatorResult.solvable(
                    RainInvestigatorStatus.VALID,
                    rain,
                    Math.sqrt(5.0),
                    Math.sqrt(5.0) * Math.toRadians(1.0),
                    "Valid",
                    3.0, 0.0, 6.0, Math.PI / 4.0
            );

            assertTrue(result.isSolvable());
            assertEquals(rain, result.reconstructedRainVelocity());
            assertEquals(rain, result.reconstructedRain().orElseThrow());
            assertEquals(Math.sqrt(5.0), result.downwardConditionNumber());
            assertFalse(Double.isNaN(result.estimatedDownwardRelError()));

            // Invariant violations must throw appropriate exceptions
            assertThrows(NullPointerException.class, () ->
                    new RainInvestigatorResult(
                            RainInvestigatorStatus.VALID,
                            null, // Null vector not allowed for solvable status
                            2.0, 0.1, "msg", 1.0, 0.0, 2.0, 0.1));

            assertThrows(IllegalArgumentException.class, () ->
                    new RainInvestigatorResult(
                            RainInvestigatorStatus.VALID,
                            rain,
                            -1.0, // Negative condition number not allowed
                            0.1, "msg", 1.0, 0.0, 2.0, 0.1));

            assertThrows(IllegalArgumentException.class, () ->
                    new RainInvestigatorResult(
                            RainInvestigatorStatus.VALID,
                            rain,
                            Double.NaN, // NaN condition number not allowed for solvable
                            0.1, "msg", 1.0, 0.0, 2.0, 0.1));
        }

        @Test
        @DisplayName("Test 10B: Unsolvable result constructor enforces null vector and NaN condition numbers")
        void testUnsolvableInvariants() {
            for (RainInvestigatorStatus status : RainInvestigatorStatus.values()) {
                if (status.isSolvable()) {
                    continue;
                }

                RainInvestigatorResult result = RainInvestigatorResult.unsolvable(
                        status, "Unsolvable error", 1.0, 0.0, 2.0, 0.1);

                assertFalse(result.isSolvable());
                assertNull(result.reconstructedRainVelocity());
                assertTrue(result.reconstructedRain().isEmpty());
                assertTrue(Double.isNaN(result.downwardConditionNumber()));
                assertTrue(Double.isNaN(result.estimatedDownwardRelError()));

                // Invariant violations: non-null vector or non-NaN numbers
                Vector2D dummy = new Vector2D(1.0, -1.0);
                assertThrows(IllegalArgumentException.class, () ->
                        new RainInvestigatorResult(
                                status,
                                dummy, // Non-null vector not allowed for unsolvable
                                Double.NaN, Double.NaN, "msg", 1.0, 0.0, 2.0, 0.1));

                assertThrows(IllegalArgumentException.class, () ->
                        new RainInvestigatorResult(
                                status,
                                null,
                                5.0, // Non-NaN condition number not allowed for unsolvable
                                Double.NaN, "msg", 1.0, 0.0, 2.0, 0.1));

                assertThrows(IllegalArgumentException.class, () ->
                        new RainInvestigatorResult(
                                status,
                                null,
                                Double.NaN,
                                0.05, // Non-NaN error not allowed for unsolvable
                                "msg", 1.0, 0.0, 2.0, 0.1));
            }
        }
    }
}
