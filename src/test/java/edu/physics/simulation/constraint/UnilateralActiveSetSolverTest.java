package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.RigidBodyState;
import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Three-Level Verification Test Suite for Stage 3C-2A.3: Unilateral Active-Set Solver.
 *
 * <p>Disciplined Verification Architecture:
 * <ul>
 *   <li>Level 1: Unit Tests (boundary velocity gating, near-zero clamping, deep immutability, cycle fallback, residual aborts).</li>
 *   <li>Level 2: Integration Tests (bilateral coupling, canonical order invariance, Baumgarte stabilization, redundant cables).</li>
 *   <li>Level 3: Acceptance Tests (physical equilibrium, slack-falling, taut-to-slack transition, complementarity bounds).</li>
 * </ul>
 */
public class UnilateralActiveSetSolverTest {

    private static final double GRAVITY = 9.81; // m/s^2
    private static final double EPSILON = 1.0e-7;

    private RigidBody createPointMass(double mass, Vector2D pos, Vector2D vel) {
        return RigidBody.pointMass("pm", mass, pos, vel);
    }

    private RigidBody createRigidBlock(double mass, double width, double height, Vector2D pos) {
        return RigidBody.dynamicBlock("block", mass, width, height, pos);
    }

    // =========================================================================
    // LEVEL 1: UNIT TESTS — Regimes, Gating, Clamping, and Immutability
    // =========================================================================
    @Nested
    @DisplayName("Level 1: Unit Tests — State Transitions, Safeguards, and Immutability")
    class UnitTests {

        @Test
        @DisplayName("Test 3: Boundary Separating Velocity Remains Slack (Velocity Gating)")
        void testBoundarySeparatingVelocityRemainsSlack() {
            // Point mass at (0, 0), anchor at (0, 2), restLength = 2.0 m (gap = 0.0)
            // Endpoints are separating: mass moving downwards at 1.0 m/s -> length increasing -> gap rate = -1.0 m/s?
            // Wait: L = ||p_anchor - p_body||. If body is at (0, 0) and moving at (0, 1) towards anchor,
            // length is decreasing -> gap g = L_0 - L is increasing -> gapRate = -u · v_rel > 0.
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), new Vector2D(0.0, 1.0)); // moving up towards anchor
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            Vector2D anchor = new Vector2D(0.0, 2.0);
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableSeparating", body, Vector2D.ZERO, anchor, 2.0);

            // Kinematics: gap = 0, gapRate = +1.0 m/s (endpoints separating into slack!)
            CableKinematics kin = cable.evaluateKinematics(bodies);
            assertEquals(0.0, kin.gap(), 1e-9);
            assertTrue(kin.gapRate() > 0.5, "gapRate must be positive (separating)");

            // External acceleration: gravity pulling downwards (-9.81), which would tend to increase length in future (w < 0)
            double[] extAcc = new double[]{0.0, -GRAVITY};

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), extAcc, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.SLACK_SEPARATING, res.cableStates().get("cableSeparating"));
            assertEquals(0.0, res.cableTensions().get("cableSeparating"), 1e-12);
            assertEquals(0.0, res.algebraicMultipliers().get("cableSeparating"), 1e-12);
            assertFalse(res.activeSetBitSet().get(0), "Cable must not be in active set");
            assertEquals(-GRAVITY, res.generalizedAccelerations()[1], 1e-9);
        }

        @Test
        @DisplayName("Test 4: Boundary Resting Inactive When Accelerating Into Slack")
        void testBoundaryRestingInactiveWhenAcceleratingIntoSlack() {
            // Mass at (0, 0), anchor at (0, 2), restLength = 2.0 m, vel = 0 (gap = 0, gapRate = 0)
            // External force pushes upwards (+15 m/s^2), which accelerates body towards anchor (gap increasing, w > 0)
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            Vector2D anchor = new Vector2D(0.0, 2.0);
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableResting", body, Vector2D.ZERO, anchor, 2.0);

            double[] extAcc = new double[]{0.0, 15.0}; // pushing into slack

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), extAcc, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.SLACK_FREE, res.cableStates().get("cableResting"));
            assertEquals(0.0, res.cableTensions().get("cableResting"), 1e-12);
            assertFalse(res.activeSetBitSet().get(0));
            assertEquals(15.0, res.generalizedAccelerations()[1], 1e-9);
        }

        @Test
        @DisplayName("Test 6: Near-Zero Negative Multiplier Clamping and Public Residual Check")
        void testNearZeroNegativeMultiplierClampingAndPublicResidualCheck() {
            // Synthetic setup: 1 DOF system with tiny negative algebraic multiplier chatter (-2.5e-9 N)
            // within [-epsLambda, 0).
            CoordinateMapping mapping = CoordinateMapping.of(List.of(
                    createPointMass(1.0, Vector2D.ZERO, Vector2D.ZERO)));

            ConstraintRow row = new ConstraintRow("cableChatter", 0, 2);
            row.setJacobian(1, 1.0); // y direction

            // gap = 0, gapRate = 0, convective = 0
            CableKinematics kin = new CableKinematics(
                    Vector2D.ZERO, new Vector2D(0, 1),
                    Vector2D.ZERO, Vector2D.ZERO,
                    Vector2D.ZERO, Vector2D.ZERO,
                    new Vector2D(0, -1), Vector2D.ZERO, Vector2D.ZERO,
                    1.0, new Vector2D(0, -1), 1.0, 0.0, 0.0, 0.0);

            // We want Delassus solve W lambda = b with b = -2.5e-9 -> lambda = -2.5e-9
            // With extAcc = [0, 2.5e-9], b = -J extAcc = -2.5e-9
            double[] extAcc = new double[]{0.0, 2.5e-9};

            UnilateralActiveSetSolver.UnilateralCableEntry entry =
                    new UnilateralActiveSetSolver.UnilateralCableEntry("cableChatter", row, kin);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solveDirect(
                    mapping, List.of(), List.of(entry), extAcc, 0.0, 0.0, Set.of("cableChatter"));

            assertTrue(res.converged());
            assertEquals(CableState.TAUT, res.cableStates().get("cableChatter"));
            assertEquals(0.0, res.cableTensions().get("cableChatter"), 1e-12, "Public tension must be clamped to 0.0");
            assertEquals(-2.5e-9, res.algebraicMultipliers().get("cableChatter"), 1e-14, "Algebraic multiplier preserved");
            assertEquals(0.0, res.generalizedAccelerations()[1], 1e-8);
        }

        @Test
        @DisplayName("Test 6B: Clamping Perturbation Exceeding Tolerance Aborts with Diagnostic Exception")
        void testClampingPerturbationExceedingToleranceAborts() {
            // Synthetic setup where delta_a exceeds epsATrans
            // epsATrans = 1.0e-6. If we set epsLambda = 1.0e-2 and body mass = 1.0 kg,
            // a negative multiplier of -1.0e-3 is inside [-epsLambda, 0), but produces delta_a = 1.0e-3 m/s^2 > epsATrans!
            CoordinateMapping mapping = CoordinateMapping.of(List.of(
                    createPointMass(1.0, Vector2D.ZERO, Vector2D.ZERO)));

            ConstraintRow row = new ConstraintRow("cableExcessive", 0, 2);
            row.setJacobian(1, 1.0);

            CableKinematics kin = new CableKinematics(
                    Vector2D.ZERO, new Vector2D(0, 1),
                    Vector2D.ZERO, Vector2D.ZERO,
                    Vector2D.ZERO, Vector2D.ZERO,
                    new Vector2D(0, -1), Vector2D.ZERO, Vector2D.ZERO,
                    1.0, new Vector2D(0, -1), 1.0, 0.0, 0.0, 0.0);

            double[] extAcc = new double[]{0.0, 1.0e-3};

            UnilateralActiveSetSolver.UnilateralCableEntry entry =
                    new UnilateralActiveSetSolver.UnilateralCableEntry("cableExcessive", row, kin);

            // Configure solver with large epsLambda (0.01) but tight epsATrans (1e-6)
            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver(
                    1.0e-2, 1.0e-7, 1.0e-5, 1.0e-5, 1.0e-6, 1.0e-5, 2.0e-4);

            ActiveSetConvergenceException ex = assertThrows(
                    ActiveSetConvergenceException.class,
                    () -> solver.solveDirect(mapping, List.of(), List.of(entry), extAcc, 0.0, 0.0, Set.of("cableExcessive")));

            assertTrue(ex.getMessage().contains("Translational acceleration perturbation"));
            assertEquals("Translational acceleration perturbation tolerance exceeded", ex.failureReason());
        }

        @Test
        @DisplayName("Test 16: Result Deep Immutability")
        void testResultDeepImmutability() {
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            Vector2D anchor = new Vector2D(0.0, 2.0);
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableImmutable", body, Vector2D.ZERO, anchor, 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            // Mutate returned array references
            double[] acc = res.generalizedAccelerations();
            acc[0] = 99999.9;
            acc[1] = -88888.8;

            double[] algAcc = res.algebraicAccelerations();
            algAcc[0] = 77777.7;

            BitSet bits = res.activeSetBitSet();
            bits.clear();

            // Verify original result is completely untouched
            assertNotEquals(99999.9, res.generalizedAccelerations()[0]);
            assertNotEquals(-88888.8, res.generalizedAccelerations()[1]);
            assertNotEquals(77777.7, res.algebraicAccelerations()[0]);
            assertTrue(res.activeSetBitSet().get(0), "Original bitset must remain set");
        }
    }

    // =========================================================================
    // LEVEL 2: INTEGRATION TESTS — Bilateral Coupling, Ordering, and Anti-Cycling
    // =========================================================================
    @Nested
    @DisplayName("Level 2: Integration Tests — Bilateral Constraints, Pivoting, and Determinism")
    class IntegrationTests {

        @Test
        @DisplayName("Test 7: Two Parallel Redundant Cables Supporting Hanging Mass")
        void testTwoCablesParallelRedundant() {
            // Mass m = 4.0 kg hanging from two identical vertical cables at (0, 2)
            RigidBody body = createPointMass(4.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry c1 = DirectCableGeometry.toAnchor(
                    "cable1", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);
            DirectCableGeometry c2 = DirectCableGeometry.toAnchor(
                    "cable2", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(c1, c2), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            assertTrue(res.converged());
            double totalTension = res.cableTensions().get("cable1") + res.cableTensions().get("cable2");
            double expectedWeight = 4.0 * GRAVITY; // 39.24 N
            assertEquals(expectedWeight, totalTension, 1e-4, "Combined tension must equal weight");
            assertEquals(0.0, res.generalizedAccelerations()[1], 1e-4, "Net vertical acceleration must be zero");
        }

        @Test
        @DisplayName("Test 9: Cycle Trap Safeguard and Single-Pivot Fallback")
        void testCycleTrapFallback() {
            // 2-cable coupled system designed to oscillate between {0} and {1} if multi-pivot is naive,
            // but resolved via single-pivot fallback.
            CoordinateMapping mapping = CoordinateMapping.of(List.of(
                    createPointMass(1.0, Vector2D.ZERO, Vector2D.ZERO)));

            ConstraintRow r1 = new ConstraintRow("cableOsc1", 0, 2);
            r1.setJacobian(1, 1.0);

            ConstraintRow r2 = new ConstraintRow("cableOsc2", 0, 2);
            r2.setJacobian(1, 1.0);

            CableKinematics kin = new CableKinematics(
                    Vector2D.ZERO, new Vector2D(0, 1),
                    Vector2D.ZERO, Vector2D.ZERO,
                    Vector2D.ZERO, Vector2D.ZERO,
                    new Vector2D(0, -1), Vector2D.ZERO, Vector2D.ZERO,
                    1.0, new Vector2D(0, -1), 1.0, 0.0, 0.0, 0.0);

            UnilateralActiveSetSolver.UnilateralCableEntry e1 =
                    new UnilateralActiveSetSolver.UnilateralCableEntry("cableOsc1", r1, kin);
            UnilateralActiveSetSolver.UnilateralCableEntry e2 =
                    new UnilateralActiveSetSolver.UnilateralCableEntry("cableOsc2", r2, kin);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            // Start with only cableOsc1 active; solver smoothly manages redundant basis
            UnilateralSolveResult res = solver.solveDirect(
                    mapping, List.of(), List.of(e1, e2), new double[]{0.0, -GRAVITY}, 0.0, 0.0, Set.of("cableOsc1"));

            assertTrue(res.converged());
            assertTrue(res.iterations() <= 4, "Must converge within few iterations without cycling");
        }

        @Test
        @DisplayName("Test 10: Bilateral Residual Tolerance Failure Aborts Without False Convergence")
        void testResidualFailureDoesNotReleaseUnrelatedConstraints() {
            CoordinateMapping mapping = CoordinateMapping.of(List.of(
                    createPointMass(1.0, Vector2D.ZERO, Vector2D.ZERO)));

            // Bilateral constraint: J = [1, 1]
            ConstraintRow bilateral = new ConstraintRow("bilateralDiag", 0, 2);
            bilateral.setJacobian(0, 1.0);
            bilateral.setJacobian(1, 1.0);

            // Cable constraint: J = [0, 1]
            ConstraintRow cableRow = new ConstraintRow("cableY", 0, 2);
            cableRow.setJacobian(1, 1.0);

            CableKinematics kin = new CableKinematics(
                    Vector2D.ZERO, new Vector2D(0, 1),
                    Vector2D.ZERO, Vector2D.ZERO,
                    Vector2D.ZERO, Vector2D.ZERO,
                    new Vector2D(0, -1), Vector2D.ZERO, Vector2D.ZERO,
                    1.0, new Vector2D(0, -1), 1.0, 0.0, 0.0, 0.0);

            UnilateralActiveSetSolver.UnilateralCableEntry cableEntry =
                    new UnilateralActiveSetSolver.UnilateralCableEntry("cableY", cableRow, kin);

            // Desired lambda: [lambda_bilateral, lambda_cable] = [1.0, -2.5e-8]
            // W = [2 1; 1 1]
            // b = W lambda = [2 - 2.5e-8, 1 - 2.5e-8]
            // Solving J extAcc = -b:
            // extAcc_y = -(1.0 - 2.5e-8)
            // extAcc_x = -1.0
            double[] extAcc = new double[]{-1.0, -(1.0 - 2.5e-8)};

            // Set epsBilateral = 1.0e-9 (< 2.5e-8)
            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver(
                    1.0e-7, 1.0e-7, 1.0e-5, 1.0e-5, 1.0e-6, 1.0e-5, 1.0e-9);

            ActiveSetConvergenceException ex = assertThrows(
                    ActiveSetConvergenceException.class,
                    () -> solver.solveDirect(
                            mapping, List.of(bilateral), List.of(cableEntry),
                            extAcc, 0.0, 0.0, Set.of("cableY")));

            assertTrue(ex.getMessage().contains("Bilateral constraint residual"));
            assertEquals("Bilateral residual tolerance exceeded under public acceleration", ex.failureReason());
        }

        @Test
        @DisplayName("Test 11: Mixed Revolute Joint (Bilateral) and Cable System")
        void testMixedJointAndCableSystem() {
            // Body 1 is pinned at origin by a revolute joint
            // Body 2 is attached to Body 1 by a cable
            RigidBody body1 = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            RigidBody body2 = createPointMass(2.0, new Vector2D(0.0, -2.0), Vector2D.ZERO);

            Map<UUID, RigidBody> bodies = Map.of(body1.id(), body1, body2.id(), body2);
            CoordinateMapping mapping = CoordinateMapping.of(List.of(body1, body2));

            RevoluteJointConstraint pin = RevoluteJointConstraint.pinToAnchor(
                    "pinBody1", body1.id(), Vector2D.ZERO, Vector2D.ZERO);
            List<ConstraintRow> bilateralRows = new java.util.ArrayList<>();
            pin.evaluate(mapping, bodies, bilateralRows, 0.01);

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "cable12", body1, Vector2D.ZERO, body2, Vector2D.ZERO, 2.0);

            // Gravity acts on both bodies: extAcc = [0, -g, 0, -g]
            double[] extAcc = new double[]{0.0, -GRAVITY, 0.0, -GRAVITY};

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, bilateralRows, List.of(cable), extAcc, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.TAUT, res.cableStates().get("cable12"));
            // Cable tension must support body 2 (m2 * g = 2 * 9.81 = 19.62 N)
            assertEquals(2.0 * GRAVITY, res.cableTensions().get("cable12"), 1e-4);
            // Pin joint on body 1 must support both bodies (4 * 9.81 = 39.24 N)
            double[] pinMults = res.bilateralMultiplier("pinBody1");
            assertNotNull(pinMults);
            assertEquals(39.24, pinMults[1], 1e-4, "Pin joint Y reaction must support total weight");
            // Both bodies in static equilibrium
            assertEquals(0.0, res.generalizedAccelerations()[1], 1e-4);
            assertEquals(0.0, res.generalizedAccelerations()[3], 1e-4);
        }

        @Test
        @DisplayName("Test 12: Zero Mass Cable Attachment Rejection")
        void testZeroMassRejection() {
            RigidBody staticBody = RigidBody.staticAnchor("staticAnchor", 0.1, Vector2D.ZERO);

            // DirectCableGeometry factory rejects static-to-anchor connection upfront
            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.toAnchor("badCable", staticBody, Vector2D.ZERO, new Vector2D(0, 1), 1.0));
        }

        @Test
        @DisplayName("Test 13: Canonical Order Independence")
        void testCanonicalOrderIndependence() {
            // Pass cables in order [c1, c2] vs [c2, c1]
            RigidBody body = createPointMass(4.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cA = DirectCableGeometry.toAnchor("cableA", body, Vector2D.ZERO, new Vector2D(-1, 2), Math.sqrt(5));
            DirectCableGeometry cB = DirectCableGeometry.toAnchor("cableB", body, Vector2D.ZERO, new Vector2D(1, 2), Math.sqrt(5));

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res1 = solver.solve(
                    mapping, bodies, List.of(), List.of(cA, cB), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);
            UnilateralSolveResult res2 = solver.solve(
                    mapping, bodies, List.of(), List.of(cB, cA), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            assertEquals(res1.cableTensions().get("cableA"), res2.cableTensions().get("cableA"), 1e-9);
            assertEquals(res1.cableTensions().get("cableB"), res2.cableTensions().get("cableB"), 1e-9);
            assertEquals(res1.generalizedAccelerations()[1], res2.generalizedAccelerations()[1], 1e-9);
        }

        @Test
        @DisplayName("Test 14: Baumgarte Stabilization on Penetrating Cable")
        void testBaumgarteStabilization() {
            // Cable with slight penetration: restLength = 2.0, actual length = 2.0001 -> gap = -0.0001 m
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, -0.0001), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cablePenetrated", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);

            double alpha = 5.0;
            double beta = 10.0;
            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), new double[]{0.0, -GRAVITY}, alpha, beta, null);

            assertTrue(res.converged());
            // Target acceleration = -beta^2 * gap = -(100) * (-0.0001) = +0.01 m/s^2 upwards
            // Resulting cable acceleration should pull upwards to restore position
            assertTrue(res.generalizedAccelerations()[1] > 0.0, "Stabilization must produce positive restorative acceleration");
        }
    }

    // =========================================================================
    // LEVEL 3: ACCEPTANCE TESTS — End-to-End Physics Scenarios
    // =========================================================================
    @Nested
    @DisplayName("Level 3: Acceptance Tests — Physical Benchmarks and Complementarity")
    class AcceptanceTests {

        @Test
        @DisplayName("Test 1: Single Vertical Cable in Taut Static Equilibrium")
        void testSingleCableTautEquilibrium() {
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableTaut", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(1, res.iterations());
            assertEquals(CableState.TAUT, res.cableStates().get("cableTaut"));
            assertEquals(2.0 * GRAVITY, res.cableTensions().get("cableTaut"), 1e-6);
            assertEquals(0.0, res.generalizedAccelerations()[1], 1e-6);
            assertEquals(0.0, res.maxComplementaritySlack(), 1e-9);
        }

        @Test
        @DisplayName("Test 2: Single Cable Slack Falling Point Mass")
        void testSingleCableSlackFalling() {
            // Mass is at (0, 0.5), anchor at (0, 2), length = 1.5 m, restLength = 2.0 m -> gap = 0.5 m (SLACK)
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.5), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableSlack", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.SLACK_INTERIOR, res.cableStates().get("cableSlack"));
            assertEquals(0.0, res.cableTensions().get("cableSlack"), 1e-12);
            assertEquals(-GRAVITY, res.generalizedAccelerations()[1], 1e-6);
            assertEquals(0.0, res.maxComplementaritySlack(), 1e-9);
        }

        @Test
        @DisplayName("Test 5: Taut to Slack Active-Set Transition Pivoting")
        void testTautToSlackTransitionPivoting() {
            // Cable warm-started as taut, but external acceleration pushes upwards (+15 m/s^2)
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cablePivoting", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            // Pass "cablePivoting" in initialTautCables
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cable), new double[]{0.0, 15.0}, 0.0, 0.0, Set.of("cablePivoting"));

            assertTrue(res.converged());
            assertEquals(2, res.iterations(), "Must take 2 iterations to release active set");
            assertEquals(CableState.SLACK_FREE, res.cableStates().get("cablePivoting"));
            assertEquals(0.0, res.cableTensions().get("cablePivoting"), 1e-12);
            assertEquals(15.0, res.generalizedAccelerations()[1], 1e-6);
        }

        @Test
        @DisplayName("Test 8: Two Cables One Taut One Slack Partitioning")
        void testTwoCablesOneTautOneSlack() {
            // Body at (0, 0).
            // Cable 1 anchor at (0, 2), restLength = 2.0 -> gap = 0.0 (TAUT under gravity)
            // Cable 2 anchor at (0, 1), restLength = 2.0 -> gap = 1.0 (SLACK)
            RigidBody body = createPointMass(2.0, new Vector2D(0.0, 0.0), Vector2D.ZERO);
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry c1 = DirectCableGeometry.toAnchor(
                    "cableTaut", body, Vector2D.ZERO, new Vector2D(0.0, 2.0), 2.0);
            DirectCableGeometry c2 = DirectCableGeometry.toAnchor(
                    "cableSlack", body, Vector2D.ZERO, new Vector2D(0.0, 1.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(c1, c2), new double[]{0.0, -GRAVITY}, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.TAUT, res.cableStates().get("cableTaut"));
            assertEquals(CableState.SLACK_INTERIOR, res.cableStates().get("cableSlack"));
            assertEquals(2.0 * GRAVITY, res.cableTensions().get("cableTaut"), 1e-6);
            assertEquals(0.0, res.cableTensions().get("cableSlack"), 1e-12);
        }

        @Test
        @DisplayName("Test 15: Complementarity Slack Tolerance Bound Across Multi-Cable System")
        void testComplementarityToleranceBound() {
            RigidBody body = createRigidBlock(3.0, 1.0, 0.5, new Vector2D(0.0, 0.0));
            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CoordinateMapping mapping = CoordinateMapping.of(bodies.values());

            DirectCableGeometry cLeft = DirectCableGeometry.toAnchor(
                    "cLeft", body, new Vector2D(-0.5, 0.0), new Vector2D(-0.5, 2.0), 2.0);
            DirectCableGeometry cRight = DirectCableGeometry.toAnchor(
                    "cRight", body, new Vector2D(0.5, 0.0), new Vector2D(0.5, 2.0), 2.0);

            UnilateralActiveSetSolver solver = new UnilateralActiveSetSolver();
            UnilateralSolveResult res = solver.solve(
                    mapping, bodies, List.of(), List.of(cLeft, cRight),
                    new double[]{0.0, -GRAVITY, 0.0}, 0.0, 0.0, null);

            assertTrue(res.converged());
            assertEquals(CableState.TAUT, res.cableStates().get("cLeft"));
            assertEquals(CableState.TAUT, res.cableStates().get("cRight"));
            assertEquals(1.5 * GRAVITY, res.cableTensions().get("cLeft"), 1e-4);
            assertEquals(1.5 * GRAVITY, res.cableTensions().get("cRight"), 1e-4);
            assertTrue(res.maxComplementaritySlack() <= 1.0e-10, "maxComplementaritySlack must be near zero");
        }
    }
}
