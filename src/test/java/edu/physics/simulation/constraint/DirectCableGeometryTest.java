package edu.physics.simulation.constraint;

import edu.physics.simulation.PhysicalBodySpec;
import edu.physics.simulation.RigidBody;
import edu.physics.simulation.RigidBodyState;
import edu.physics.simulation.SimulationWorld;
import edu.physics.simulation.Vector2D;
import edu.physics.simulation.geometry.CircleShape;
import edu.physics.simulation.geometry.PointMassShape;
import edu.physics.simulation.geometry.RectangleShape;
import edu.physics.simulation.scene.AnchorRef;
import edu.physics.simulation.scene.AttachmentPoint;
import edu.physics.simulation.scene.CableTopology;
import edu.physics.simulation.scene.InitialPose;
import edu.physics.simulation.scene.LaboratoryScene;
import edu.physics.simulation.scene.SceneAnchor;
import edu.physics.simulation.scene.SceneBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three-Level Verification Test Suite for Stage 3C-2A.1: Cable Geometry and Kinematics.
 *
 * <p>Disciplined Verification Architecture:
 * <ul>
 *   <li>Level 1: Unit Tests (kinematics, finite-difference Jacobians, convective derivatives, parameter guards).</li>
 *   <li>Level 2: Integration Tests (scene adapter from CableTopology, CoordinateMapping DOF reductions, solver isolation).</li>
 *   <li>Level 3: Acceptance Tests (endpoint reversal invariance, pulling tension invariant, fixed-to-fixed rejection).</li>
 * </ul>
 */
public class DirectCableGeometryTest {

    private static final double EPSILON = 1.0e-9;
    private static final double NUMERICAL_DERIVATIVE_TOLERANCE = 1.0e-5;

    // =========================================================================
    // LEVEL 1: UNIT TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 1: Unit Tests — Kinematics, Jacobians, and Derivatives")
    class UnitTests {

        @Test
        @DisplayName("1.1 Stationary Anchor Kinematics: Analytical positions, length, gap, and direction")
        void stationaryAnchorKinematics() {
            // Body at (0, 2), Anchor at (0, 5), restLength = 3.0 m
            Vector2D bodyPos = new Vector2D(0.0, 2.0);
            Vector2D anchorPos = new Vector2D(0.0, 5.0);
            double restLength = 3.0;

            RigidBody body = RigidBody.dynamicDisc("ball", 1.5, 0.2, bodyPos);
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cable1", body, Vector2D.ZERO, anchorPos, restLength);

            Map<UUID, RigidBody> bodies = Map.of(body.id(), body);
            CableKinematics kin = cable.evaluateKinematics(bodies);

            assertEquals(new Vector2D(0.0, 2.0), kin.worldAttachmentA());
            assertEquals(new Vector2D(0.0, 5.0), kin.worldAttachmentB());
            assertEquals(new Vector2D(0.0, -3.0), kin.relativeDisplacement());
            assertEquals(3.0, kin.currentLength(), EPSILON);
            assertEquals(new Vector2D(0.0, -1.0), kin.unitDirection()); // Points anchor -> body
            assertEquals(0.0, kin.gap(), EPSILON); // Taut: 3.0 - 3.0 = 0
            assertEquals(0.0, kin.gapRate(), EPSILON);
            assertEquals(0.0, kin.convectiveAcceleration(), EPSILON);
        }

        @Test
        @DisplayName("1.2 Two-Body Offset Attachment Kinematics with Rigid Rotation")
        void twoBodyOffsetAttachmentKinematics() {
            // Body A at (1, 2) rotated 90 deg (pi/2 rad), local offset (0.5, 0.0) -> world offset (0, 0.5) -> pA = (1, 2.5)
            // Body B at (4, 6) rotated 0 deg, local offset (0.0, -1.0) -> pB = (4, 5.0)
            RigidBody bodyA = RigidBody.dynamicBlock("bodyA", 2.0, 1.0, 0.5, new Vector2D(1.0, 2.0));
            bodyA.state().setAngle(Math.PI / 2.0);

            RigidBody bodyB = RigidBody.dynamicBlock("bodyB", 3.0, 0.8, 2.0, new Vector2D(4.0, 6.0));
            bodyB.state().setAngle(0.0);

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "cableCoupled",
                    bodyA, new Vector2D(0.5, 0.0),
                    bodyB, new Vector2D(0.0, -1.0),
                    5.0);

            Map<UUID, RigidBody> bodies = Map.of(bodyA.id(), bodyA, bodyB.id(), bodyB);
            CableKinematics kin = cable.evaluateKinematics(bodies);

            // pA = (1.0, 2.5), pB = (4.0, 5.0)
            assertEquals(1.0, kin.worldAttachmentA().x(), EPSILON);
            assertEquals(2.5, kin.worldAttachmentA().y(), EPSILON);
            assertEquals(4.0, kin.worldAttachmentB().x(), EPSILON);
            assertEquals(5.0, kin.worldAttachmentB().y(), EPSILON);

            // r = pA - pB = (-3.0, -2.5)
            assertEquals(-3.0, kin.relativeDisplacement().x(), EPSILON);
            assertEquals(-2.5, kin.relativeDisplacement().y(), EPSILON);

            double expectedLength = Math.hypot(-3.0, -2.5); // sqrt(9 + 6.25) = sqrt(15.25) approx 3.9051248
            assertEquals(expectedLength, kin.currentLength(), EPSILON);
            assertEquals(5.0 - expectedLength, kin.gap(), EPSILON); // Slack: gap > 0
        }

        @Test
        @DisplayName("1.3 Angular Velocity and Convective Centripetal Velocity Contribution")
        void angularVelocityAttachmentKinematics() {
            // Disc rotating at omega = 4 rad/s with attachment at rim r_local = (0.3, 0)
            // Linear velocity of CM = (2.0, 1.0)
            // Tangential attachment velocity = omega * (-ry, rx) = 4 * (0, 0.3) = (0, 1.2)
            // Total attachment velocity = (2.0, 2.2)
            RigidBody disc = RigidBody.dynamicDisc("disc", 2.0, 0.3, new Vector2D(0.0, 0.0));
            disc.state().setVelocity(new Vector2D(2.0, 1.0));
            disc.state().setAngularVelocity(4.0);

            Vector2D worldAnchor = new Vector2D(0.0, 3.0);
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "cableSwing", disc, new Vector2D(0.3, 0.0), worldAnchor, 3.5);

            Map<UUID, RigidBody> bodies = Map.of(disc.id(), disc);
            CableKinematics kin = cable.evaluateKinematics(bodies);

            assertEquals(2.0, kin.worldVelocityA().x(), EPSILON);
            assertEquals(2.2, kin.worldVelocityA().y(), EPSILON);

            // Relative velocity = (2.0, 2.2)
            assertEquals(new Vector2D(2.0, 2.2), kin.relativeVelocity());

            // Gap rate g_dot = -u · v_rel
            double expectedGapRate = -kin.unitDirection().dot(kin.relativeVelocity());
            assertEquals(expectedGapRate, kin.gapRate(), EPSILON);
        }

        @Test
        @DisplayName("1.4 Central Finite-Difference Jacobian Verification: J matches numerical gradient of g(q)")
        void jacobianFiniteDifferenceVerification() {
            // Setup two dynamic bodies with non-trivial positions and angles
            RigidBody bodyA = RigidBody.dynamicBlock("bodyA", 2.0, 0.6, 0.4, new Vector2D(1.2, 2.5));
            bodyA.state().setAngle(0.7);
            bodyA.state().setVelocity(new Vector2D(0.5, -0.3));
            bodyA.state().setAngularVelocity(1.5);

            RigidBody bodyB = RigidBody.dynamicBlock("bodyB", 3.0, 0.8, 0.5, new Vector2D(3.8, 4.2));
            bodyB.state().setAngle(-0.4);
            bodyB.state().setVelocity(new Vector2D(-0.2, 0.8));
            bodyB.state().setAngularVelocity(-2.0);

            Vector2D localAnchorA = new Vector2D(0.2, -0.15);
            Vector2D localAnchorB = new Vector2D(-0.3, 0.2);
            double restLength = 4.0;

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "cableDiff", bodyA, localAnchorA, bodyB, localAnchorB, restLength);

            CoordinateMapping mapping = CoordinateMapping.of(List.of(bodyA, bodyB));
            ConstraintRow row = new ConstraintRow("cableDiff", 0, mapping.totalDof());
            Map<UUID, RigidBody> bodies = Map.of(bodyA.id(), bodyA, bodyB.id(), bodyB);

            cable.populateJacobian(mapping, bodies, row);

            // Compute numerical gradient of g(q) = L_0 - L(q) via central difference:
            double h = 1.0e-7;

            // Coordinate indices: bodyA has idx 0,1,2 (x, y, theta); bodyB has idx 3,4,5 (x, y, theta)
            int idxA = mapping.startIndex(bodyA.id());
            int idxB = mapping.startIndex(bodyB.id());

            // 1. d(g)/dx_A
            double gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, h, 0, 0, 0, 0, 0);
            double gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, -h, 0, 0, 0, 0, 0);
            double numGradAx = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradAx, row.getJacobian(idxA), NUMERICAL_DERIVATIVE_TOLERANCE);

            // 2. d(g)/dy_A
            gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, h, 0, 0, 0, 0);
            gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, -h, 0, 0, 0, 0);
            double numGradAy = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradAy, row.getJacobian(idxA + 1), NUMERICAL_DERIVATIVE_TOLERANCE);

            // 3. d(g)/dtheta_A
            gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, h, 0, 0, 0);
            gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, -h, 0, 0, 0);
            double numGradAtheta = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradAtheta, row.getJacobian(idxA + 2), NUMERICAL_DERIVATIVE_TOLERANCE);

            // 4. d(g)/dx_B
            gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, h, 0, 0);
            gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, -h, 0, 0);
            double numGradBx = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradBx, row.getJacobian(idxB), NUMERICAL_DERIVATIVE_TOLERANCE);

            // 5. d(g)/dy_B
            gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, 0, h, 0);
            gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, 0, -h, 0);
            double numGradBy = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradBy, row.getJacobian(idxB + 1), NUMERICAL_DERIVATIVE_TOLERANCE);

            // 6. d(g)/dtheta_B
            gPlus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, 0, 0, h);
            gMinus = computePerturbedGap(bodyA, localAnchorA, bodyB, localAnchorB, restLength, 0, 0, 0, 0, 0, -h);
            double numGradBtheta = (gPlus - gMinus) / (2.0 * h);
            assertEquals(numGradBtheta, row.getJacobian(idxB + 2), NUMERICAL_DERIVATIVE_TOLERANCE);
        }

        private double computePerturbedGap(
                RigidBody bodyA, Vector2D localA,
                RigidBody bodyB, Vector2D localB,
                double restLength,
                double dxA, double dyA, double dthA,
                double dxB, double dyB, double dthB) {

            Vector2D posA = bodyA.position().add(new Vector2D(dxA, dyA));
            double thA = bodyA.angle() + dthA;
            Vector2D pA = posA.add(localA.rotate(thA));

            Vector2D posB = bodyB.position().add(new Vector2D(dxB, dyB));
            double thB = bodyB.angle() + dthB;
            Vector2D pB = posB.add(localB.rotate(thB));

            return restLength - pA.distanceTo(pB);
        }

        @Test
        @DisplayName("1.5 Numerical Time-Differentiation of Convective Acceleration gamma_conv")
        void convectiveAccelerationTimeDerivative() {
            // Particle swinging as a pendulum: L = 2.0 m, speed v = 3.0 m/s perpendicular to cable
            // Convective orbital swing acceleration: -||v_perp||^2 / L = -(9.0) / 2.0 = -4.5 m/s^2
            Vector2D anchor = new Vector2D(0.0, 2.0);
            RigidBody particle = RigidBody.pointMass("p", 1.0, new Vector2D(0.0, 0.0), new Vector2D(3.0, 0.0));

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "swing", particle, Vector2D.ZERO, anchor, 2.0);

            CableKinematics kin = cable.evaluateKinematics(Map.of(particle.id(), particle));

            // u = (0, -2) / 2 = (0, -1)
            // v_rel = (3, 0)
            // v_perp = (3, 0)
            // gamma_conv = -9 / 2 = -4.5
            assertEquals(-4.5, kin.convectiveAcceleration(), EPSILON);
        }

        @Test
        @DisplayName("1.6 Parameter Guard: Coincident endpoints reject at construction")
        void coincidentEndpointsRejection() {
            RigidBody b1 = RigidBody.dynamicDisc("b1", 1.0, 0.2, new Vector2D(1.0, 1.0));
            RigidBody b2 = RigidBody.dynamicDisc("b2", 1.0, 0.2, new Vector2D(1.0, 1.0));

            // Exactly coincident
            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("bad1", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.0));

            // Within 1 µm threshold
            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("bad2", b1, Vector2D.ZERO, b2, new Vector2D(5.0e-7, 0.0), 1.0));

            // Same for anchor
            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.toAnchor("badAnchor", b1, Vector2D.ZERO, new Vector2D(1.0, 1.0), 1.0));
        }

        @Test
        @DisplayName("1.7 Parameter Guard: Non-positive rest length rejects at construction")
        void nonPositiveRestLengthRejection() {
            RigidBody b1 = RigidBody.dynamicDisc("b1", 1.0, 0.2, new Vector2D(0.0, 0.0));
            RigidBody b2 = RigidBody.dynamicDisc("b2", 1.0, 0.2, new Vector2D(2.0, 0.0));

            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("zeroLen", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 0.0));

            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("negLen", b1, Vector2D.ZERO, b2, Vector2D.ZERO, -1.0));

            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("nanLen", b1, Vector2D.ZERO, b2, Vector2D.ZERO, Double.NaN));
        }

        @Test
        @DisplayName("1.8 Runtime Singularity Floor: Distance below 1 nm throws IllegalStateException")
        void runtimeSingularityFloor() {
            RigidBody b1 = RigidBody.dynamicDisc("b1", 1.0, 0.2, new Vector2D(0.0, 0.0));
            RigidBody b2 = RigidBody.dynamicDisc("b2", 1.0, 0.2, new Vector2D(1.0, 0.0));

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "collapse", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.0);

            // Move b2 to collapse with b1 (distance = 5e-10 m < 1e-9 m)
            b2.state().setPosition(new Vector2D(5.0e-10, 0.0));

            assertThrows(IllegalStateException.class, () ->
                    cable.evaluateKinematics(Map.of(b1.id(), b1, b2.id(), b2)));
        }
    }

    // =========================================================================
    // LEVEL 2: INTEGRATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 2: Integration Tests — Scene Topology Adapter and Coordinate Mapping")
    class IntegrationTests {

        @Test
        @DisplayName("2.1 CableTopology Adapter correctly resolves AttachmentPoint and SceneAnchor")
        void topologyAdapterResolution() {
            UUID bodyId = UUID.randomUUID();
            AttachmentPoint rimPoint = AttachmentPoint.of("rim", "Rim Mount", new Vector2D(0.2, 0.0));
            PhysicalBodySpec spec = PhysicalBodySpec.createDisc(bodyId, "bob", 2.0, 0.2);
            SceneBody sceneBody = SceneBody.of(
                    bodyId,
                    "pendulumBob",
                    spec,
                    new CircleShape(0.2),
                    InitialPose.atRest(new Vector2D(0.0, 1.0)),
                    List.of(rimPoint));

            SceneAnchor ceilingAnchor = SceneAnchor.of("ceilingMount", new Vector2D(0.0, 4.0));

            CableTopology topology = CableTopology.of(
                    "mainCable",
                    AnchorRef.body(bodyId, "rim"),
                    AnchorRef.anchor("ceilingMount"),
                    3.5);

            LaboratoryScene scene = new LaboratoryScene.Builder("scene1", "Cable Lab")
                    .addBody(sceneBody)
                    .addAnchor(ceilingAnchor)
                    .addRelationship(topology)
                    .build();

            // Instantiate runtime rigid body
            RigidBody runtimeBody = new RigidBody(
                    sceneBody.spec(),
                    sceneBody.shape(),
                    RigidBodyState.atRest(sceneBody.initialPose().position()));
            Map<UUID, RigidBody> runtimeBodies = Map.of(bodyId, runtimeBody);

            DirectCableGeometry cable = DirectCableGeometry.fromTopology(topology, scene, runtimeBodies);

            assertEquals("mainCable", cable.cableId());
            assertEquals(bodyId, cable.bodyAId());
            assertEquals(new Vector2D(0.2, 0.0), cable.localAnchorA());
            assertTrue(cable.isLinkedToWorld());
            assertEquals(new Vector2D(0.0, 4.0), cable.localAnchorBOrWorld());
            assertEquals(3.5, cable.restLength(), EPSILON);

            CableKinematics kin = cable.evaluateKinematics(runtimeBodies);
            assertEquals(new Vector2D(0.2, 1.0), kin.worldAttachmentA());
            assertEquals(new Vector2D(0.0, 4.0), kin.worldAttachmentB());
        }

        @Test
        @DisplayName("2.2 Intermediate Pulley Rejection in Direct Cable Adapter")
        void intermediatePulleyRejection() {
            UUID bodyId = UUID.randomUUID();
            AttachmentPoint pt = AttachmentPoint.of("pt", Vector2D.ZERO);
            SceneBody body = SceneBody.of(
                    bodyId, "box", PhysicalBodySpec.createBlock("box", 1.0, 0.5, 0.5),
                    new RectangleShape(0.5, 0.5), InitialPose.atRest(new Vector2D(0.0, 0.0)), List.of(pt));
            SceneAnchor anchor = SceneAnchor.of("anchor", new Vector2D(0.0, 5.0));
            SceneAnchor pulley = SceneAnchor.of("pulleySheave", new Vector2D(0.0, 2.5));

            CableTopology topologyWithPulleys = CableTopology.of(
                    "pulleyCable",
                    AnchorRef.body(bodyId, "pt"),
                    AnchorRef.anchor("anchor"),
                    List.of(AnchorRef.anchor("pulleySheave")),
                    5.0);

            LaboratoryScene scene = new LaboratoryScene.Builder("scene2", "Pulley Lab")
                    .addBody(body)
                    .addAnchor(anchor)
                    .addAnchor(pulley)
                    .addRelationship(topologyWithPulleys)
                    .build();

            RigidBody runtimeBody = RigidBody.dynamicBlock("box", 1.0, 0.5, 0.5, new Vector2D(0.0, 0.0));

            assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.fromTopology(topologyWithPulleys, scene, Map.of(bodyId, runtimeBody)));
        }

        @Test
        @DisplayName("2.3 CoordinateMapping Active Reduction: 3-DOF Rigid Body vs 2-DOF Point Mass")
        void coordinateMappingDofReduction() {
            // Rigid body (3 DOFs: x, y, theta)
            RigidBody disc = RigidBody.dynamicDisc("disc", 2.0, 0.3, new Vector2D(0.0, 0.0));
            // Point mass (2 DOFs: x, y, no theta)
            RigidBody point = RigidBody.pointMass("point", 1.0, new Vector2D(2.0, 0.0), Vector2D.ZERO);

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "link", disc, new Vector2D(0.3, 0.0), point, Vector2D.ZERO, 2.0);

            CoordinateMapping mapping = CoordinateMapping.of(List.of(disc, point));
            assertEquals(5, mapping.totalDof()); // 3 + 2 = 5 DOFs total

            ConstraintRow row = new ConstraintRow("link", 0, mapping.totalDof());
            cable.populateJacobian(mapping, Map.of(disc.id(), disc, point.id(), point), row);

            // Disc has translation (idx 0, 1) and rotation (idx 2)
            assertEquals(1.0, row.getJacobian(0), EPSILON);  // -ux = -(-1.0) = +1.0 (pulls right toward point)
            assertEquals(0.0, row.getJacobian(1), EPSILON);  // -uy
            assertEquals(0.0, row.getJacobian(2), EPSILON);  // -(rA × u)_z

            // Point mass has translation (idx 3, 4) and NO rotational entry
            assertEquals(-1.0, row.getJacobian(3), EPSILON); // +ux = -1.0 (pulls left toward disc)
            assertEquals(0.0, row.getJacobian(4), EPSILON);  // +uy
        }

        @Test
        @DisplayName("2.4 Strict Isolation: Cables are not registered with bilateral constraint solver")
        void bilateralSolverIsolation() {
            SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
            RigidBody body = RigidBody.dynamicDisc("bob", 1.0, 0.2, new Vector2D(0.0, 1.0));
            world.addBody(body);

            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "unilateralCable", body, Vector2D.ZERO, new Vector2D(0.0, 3.0), 2.5);

            // Advance simulation step
            world.stepSingle();

            // Verify the world constraint list is strictly empty
            assertTrue(world.constraints().isEmpty(),
                    "Cables must NOT be added to SimulationWorld.constraints() bilateral solver list");
        }
    }

    // =========================================================================
    // LEVEL 3: ACCEPTANCE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Level 3: Acceptance Tests — Normalization, Invariance, and Workflows")
    class AcceptanceTests {

        @Test
        @DisplayName("3.1 Reversed Endpoint Ordering (Anchor -> Body) Normalization and Invariance")
        void reversedEndpointOrderingInvariance() {
            // Anchor at (0, 4), Body at (0, 1), restLength = 3.5 m
            UUID bodyId = UUID.randomUUID();
            AttachmentPoint pt = AttachmentPoint.of("hook", Vector2D.ZERO);
            PhysicalBodySpec spec = PhysicalBodySpec.createBlock(bodyId, "crate", 2.0, 0.4, 0.4);
            SceneBody sceneBody = SceneBody.of(
                    bodyId, "crate", spec,
                    new RectangleShape(0.4, 0.4), InitialPose.atRest(new Vector2D(0.0, 1.0)), List.of(pt));
            SceneAnchor anchor = SceneAnchor.of("ceiling", new Vector2D(0.0, 4.0));

            // Forward configuration: Body -> Anchor
            CableTopology forwardTopology = CableTopology.of(
                    "cableForward",
                    AnchorRef.body(bodyId, "hook"),
                    AnchorRef.anchor("ceiling"),
                    3.5);

            // Reversed configuration: Anchor -> Body
            CableTopology reversedTopology = CableTopology.of(
                    "cableReversed",
                    AnchorRef.anchor("ceiling"),
                    AnchorRef.body(bodyId, "hook"),
                    3.5);

            LaboratoryScene scene = new LaboratoryScene.Builder("sceneRev", "Reversal Lab")
                    .addBody(sceneBody)
                    .addAnchor(anchor)
                    .addRelationship(forwardTopology)
                    .addRelationship(reversedTopology)
                    .build();

            RigidBody runtimeBody = new RigidBody(
                    sceneBody.spec(),
                    sceneBody.shape(),
                    RigidBodyState.atRest(sceneBody.initialPose().position()));
            Map<UUID, RigidBody> bodies = Map.of(bodyId, runtimeBody);

            DirectCableGeometry geomForward = DirectCableGeometry.fromTopology(forwardTopology, scene, bodies);
            DirectCableGeometry geomReversed = DirectCableGeometry.fromTopology(reversedTopology, scene, bodies);

            // 1. Both normalize to bodyId as bodyAId and world anchor as bodyBId = null
            assertEquals(bodyId, geomForward.bodyAId());
            assertEquals(bodyId, geomReversed.bodyAId());
            assertTrue(geomForward.isLinkedToWorld());
            assertTrue(geomReversed.isLinkedToWorld());

            // 2. Both report identical kinematics
            CableKinematics kinForward = geomForward.evaluateKinematics(bodies);
            CableKinematics kinReversed = geomReversed.evaluateKinematics(bodies);

            assertEquals(kinForward.currentLength(), kinReversed.currentLength(), EPSILON);
            assertEquals(kinForward.gap(), kinReversed.gap(), EPSILON);
            assertEquals(kinForward.unitDirection(), kinReversed.unitDirection());
            assertEquals(kinForward.gapRate(), kinReversed.gapRate(), EPSILON);
            assertEquals(kinForward.convectiveAcceleration(), kinReversed.convectiveAcceleration(), EPSILON);

            // 3. Both produce identical Jacobian rows
            CoordinateMapping mapping = CoordinateMapping.of(List.of(runtimeBody));
            ConstraintRow rowForward = new ConstraintRow("fwd", 0, mapping.totalDof());
            ConstraintRow rowReversed = new ConstraintRow("rev", 0, mapping.totalDof());

            geomForward.populateJacobian(mapping, bodies, rowForward);
            geomReversed.populateJacobian(mapping, bodies, rowReversed);

            assertArrayEquals(rowForward.jacobian(), rowReversed.jacobian(), EPSILON);
            assertEquals(rowForward.positionError(), rowReversed.positionError(), EPSILON);
            assertEquals(rowForward.velocityError(), rowReversed.velocityError(), EPSILON);
            assertEquals(rowForward.centripetalAccel(), rowReversed.centripetalAccel(), EPSILON);
        }

        @Test
        @DisplayName("3.2 Pulling Tension Invariant: Positive multiplier pulls inward toward anchor")
        void pullingTensionInvariant() {
            // Body at (0, 1), Anchor at (0, 4)
            // Cable vector points from Anchor (0, 4) to Body (0, 1): r = (0, -3), u = (0, -1)
            // Jacobian for body translation: J = -u = (0, +1)
            // Generalized force: f = J^T * lambda = (0, +1) * lambda
            // With lambda >= 0 (positive multiplier): f_y = +lambda > 0 (pulls UPWARD toward anchor)
            RigidBody body = RigidBody.dynamicDisc("bob", 1.0, 0.2, new Vector2D(0.0, 1.0));
            DirectCableGeometry cable = DirectCableGeometry.toAnchor(
                    "pullCheck", body, Vector2D.ZERO, new Vector2D(0.0, 4.0), 3.0);

            CoordinateMapping mapping = CoordinateMapping.of(List.of(body));
            ConstraintRow row = new ConstraintRow("pullCheck", 0, mapping.totalDof());
            cable.populateJacobian(mapping, Map.of(body.id(), body), row);

            double jx = row.getJacobian(0);
            double jy = row.getJacobian(1);

            assertEquals(0.0, jx, EPSILON);
            assertEquals(1.0, jy, EPSILON); // +1.0

            // Apply a test multiplier lambda = 15.0 N
            double lambda = 15.0;
            Vector2D constraintForce = new Vector2D(jx * lambda, jy * lambda);

            // Force must point directly UPWARD toward the anchor at (0, 4)
            assertTrue(constraintForce.approximatelyEquals(new Vector2D(0.0, 15.0), EPSILON));
            assertEquals(0.0, constraintForce.x(), EPSILON);
            assertEquals(15.0, constraintForce.y(), EPSILON);
            assertTrue(constraintForce.y() > 0.0, "Constraint force with lambda > 0 must pull toward the anchor");
        }

        @Test
        @DisplayName("3.3 Rejection of World Anchor ↔ World Anchor (Fixed-to-Fixed Cable)")
        void worldToWorldRejection() {
            SceneAnchor a1 = SceneAnchor.of("wallLeft", new Vector2D(-2.0, 3.0));
            SceneAnchor a2 = SceneAnchor.of("wallRight", new Vector2D(2.0, 3.0));

            CableTopology fixedTopology = CableTopology.of(
                    "fixedCable",
                    AnchorRef.anchor("wallLeft"),
                    AnchorRef.anchor("wallRight"),
                    4.0);

            LaboratoryScene scene = new LaboratoryScene.Builder("fixedScene", "Fixed Lab")
                    .addAnchor(a1)
                    .addAnchor(a2)
                    .addRelationship(fixedTopology)
                    .build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.fromTopology(fixedTopology, scene, Map.of()));

            assertTrue(ex.getMessage().contains("zero dynamic degrees of freedom"));
        }

        @Test
        @DisplayName("3.4 Rejection of Static Body ↔ Static Body Cable")
        void staticToStaticBodyRejection() {
            RigidBody static1 = RigidBody.staticBox("s1", 1.0, 1.0, new Vector2D(-1.0, 0.0));
            RigidBody static2 = RigidBody.staticBox("s2", 1.0, 1.0, new Vector2D(1.0, 0.0));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    DirectCableGeometry.betweenBodies("staticCable", static1, Vector2D.ZERO, static2, Vector2D.ZERO, 2.0));

            assertTrue(ex.getMessage().contains("zero dynamic degrees of freedom"));
        }

        @Test
        @DisplayName("3.5 Dynamic Body ↔ Static Body Normalization")
        void dynamicToStaticBodyNormalization() {
            // Dynamic body at (0, 0), Static body at (0, 5) with local anchor (0, -1) -> world anchor (0, 4)
            RigidBody dynamicBody = RigidBody.dynamicDisc("dyn", 1.0, 0.2, new Vector2D(0.0, 0.0));
            RigidBody staticBody = RigidBody.staticBox("stat", 2.0, 2.0, new Vector2D(0.0, 5.0));

            DirectCableGeometry cable = DirectCableGeometry.betweenBodies(
                    "dynStat", dynamicBody, Vector2D.ZERO, staticBody, new Vector2D(0.0, -1.0), 4.0);

            // Normalized to dynamic body as Slot A, fixed world anchor as Slot B
            assertEquals(dynamicBody.id(), cable.bodyAId());
            assertTrue(cable.isLinkedToWorld());
            assertEquals(new Vector2D(0.0, 4.0), cable.localAnchorBOrWorld());

            CableKinematics kin = cable.evaluateKinematics(Map.of(dynamicBody.id(), dynamicBody, staticBody.id(), staticBody));
            assertEquals(new Vector2D(0.0, 4.0), kin.worldAttachmentB());
            assertEquals(4.0, kin.currentLength(), EPSILON);
        }
    }
}
