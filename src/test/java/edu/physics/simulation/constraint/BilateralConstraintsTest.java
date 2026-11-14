package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.SimulationWorld;
import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification test suite for Stage 3C-1 Bilateral Constraints and Core Solver Framework.
 *
 * <p>Validates:
 * <ul>
 *   <li>Revolute joint anchoring a physical solid disc pendulum under gravity over 5,000 steps (41.67 s).</li>
 *   <li>Distance link rigid length conservation under dynamic tension in zero gravity.</li>
 *   <li>Rejection of singular coincident endpoints at distance link creation.</li>
 *   <li>Unregularized rank-revealing redundancy detection without numerical singularity or force splitting.</li>
 *   <li>Active coordinate reduction with mixed 2-DOF point masses and 3-DOF rigid bodies.</li>
 *   <li>Linear and angular momentum conservation for internal bilateral body-to-body constraints.</li>
 *   <li>Rejection of subtle kinematic conflicts differing by 50 µm under the restored 1e-6 m tolerance.</li>
 *   <li>Handling of multiple simultaneous dependent rows (3+ constraints).</li>
 *   <li>Nearly dependent but genuinely independent constraints resolved without false redundancy.</li>
 *   <li>Position projection non-convergence detection.</li>
 *   <li>Velocity projection post-verification.</li>
 *   <li>Revolute joint with general 2D non-zero attachment offsets.</li>
 *   <li>Point mass constrained by multiple simultaneous links (triangulated suspension).</li>
 *   <li>Three-way LDLT pivot classification (positive, small negative roundoff, materially negative).</li>
 * </ul>
 */
public class BilateralConstraintsTest {

    private static final double POSITION_TOLERANCE = 1.0e-6; // metres
    private static final double VELOCITY_TOLERANCE = 1.0e-5; // metres / second
    private static final double MOMENTUM_TOLERANCE = 1.0e-10; // kg * m / s
    private static final double MAX_ACCEPTABLE_ENERGY_DRIFT = 0.005; // 0.5% relative drift
    private static final double ANGULAR_MOMENTUM_DRIFT_TOLERANCE = 1.0e-4; // kg * m^2 / s

    @Test
    @DisplayName("Revolute Joint: Solid disc pinned at rim maintains anchor to <= 1 µm and energy drift <= 0.5% over 5,000 steps")
    void revoluteJointMaintainsAnchorUnderGravity() {
        // Physical parameters:
        // Solid disc: m = 2.0 kg, R = 0.3 m, I_cm = 1/2 m R^2 = 0.09 kg m^2.
        // Pinned at rim: local anchor (-0.3, 0.0) relative to disc center.
        // World anchor: (0.0, 2.0).
        // Released from horizontal: initial center of mass at (0.3, 2.0), initial angle 0.0 rad.
        // Initial pin position: (0.3, 2.0) + (-0.3, 0.0) = (0.0, 2.0) (exact match).
        // Pivot moment of inertia by parallel axis theorem:
        // I_p = I_cm + m R^2 = 3/2 m R^2 = 0.27 kg m^2.
        // Small-oscillation period: T_0 = 2 * pi * sqrt(I_p / (m * g * R)) approx 1.3457 s.
        // Large-amplitude release (theta_0 = 90 deg = pi/2 rad):
        // Actual period T approx 1.1803 * T_0 approx 1.588 s.
        // Total duration: 5,000 steps at dt = 1/120 s => t = 41.667 s
        // (approx 31 small-angle benchmark periods, approx 26 actual large-amplitude periods).

        double mass = 2.0;
        double radius = 0.3;
        Vector2D worldAnchor = new Vector2D(0.0, 2.0);
        Vector2D localAnchor = new Vector2D(-radius, 0.0);
        Vector2D initialPos = new Vector2D(radius, 2.0);

        SimulationWorld world = SimulationWorld.withGravity(9.81);
        RigidBody disc = RigidBody.dynamicDisc("disc", mass, radius, initialPos);
        world.addBody(disc);

        RevoluteJointConstraint pin = RevoluteJointConstraint.pinToAnchor(
                "rimPin", disc.id(), localAnchor, worldAnchor);
        world.addConstraint(pin);

        double equilibriumY = worldAnchor.y() - radius; // 1.7 m
        double g = 9.81;
        double initialPotentialEnergy = mass * g * (disc.position().y() - equilibriumY);
        double initialTotalEnergy = disc.kineticEnergy() + initialPotentialEnergy;

        // Grounded characteristic energy scale: potential energy swing m * g * R
        double potentialRange = mass * g * radius;
        double eChar = Math.max(Math.abs(initialTotalEnergy), potentialRange);

        double maxPinError = 0.0;
        double maxRelativeEnergyDrift = 0.0;

        for (int step = 0; step < 5000; step++) {
            world.stepSingle();

            Vector2D currentPin = disc.position().add(localAnchor.rotate(disc.angle()));
            double pinError = currentPin.distanceTo(worldAnchor);
            maxPinError = Math.max(maxPinError, pinError);

            double pe = mass * g * (disc.position().y() - equilibriumY);
            double totalE = disc.kineticEnergy() + pe;
            double drift = Math.abs(totalE - initialTotalEnergy) / eChar;
            maxRelativeEnergyDrift = Math.max(maxRelativeEnergyDrift, drift);
        }

        assertTrue(maxPinError <= POSITION_TOLERANCE,
                "Maximum revolute pin drift must be <= 1 µm (1e-6 m), but was: " + maxPinError);
        assertTrue(maxRelativeEnergyDrift <= MAX_ACCEPTABLE_ENERGY_DRIFT,
                "Relative energy drift must remain <= 0.5% (0.005) over 5,000 steps, but was: "
                        + (maxRelativeEnergyDrift * 100.0) + "%");
    }

    @Test
    @DisplayName("Distance Link: Rigid distance conserved to <= 1 µm and linear momentum conserved under dynamic tension")
    void distanceLinkConservesRigidDistanceUnderDynamicTension() {
        double dt = 1.0 / 120.0;
        SimulationWorld world = SimulationWorld.zeroGravity(dt);

        RigidBody b1 = RigidBody.dynamicBlock("b1", 3.0, 0.4, 0.4, new Vector2D(-1.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 5.0, 0.4, 0.4, new Vector2D(1.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        DistanceLinkConstraint link = DistanceLinkConstraint.betweenBodies(
                "link1", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);
        world.addConstraint(link);

        // Apply outward pulling forces via ForceGenerator
        world.addForceGenerator((body, time) -> {
            if (body.id().equals(b1.id())) {
                body.applyForce(new Vector2D(-150.0, 0.0));
            } else if (body.id().equals(b2.id())) {
                body.applyForce(new Vector2D(150.0, 0.0));
            }
        });

        double maxDistError = 0.0;
        for (int step = 0; step < 1000; step++) {
            world.stepSingle();

            double currentDist = b1.position().distanceTo(b2.position());
            double distErr = Math.abs(currentDist - 2.0);
            maxDistError = Math.max(maxDistError, distErr);

            Vector2D totalP = world.totalLinearMomentum();
            assertTrue(totalP.magnitude() <= MOMENTUM_TOLERANCE,
                    "Total linear momentum must be conserved to machine tolerance, got: " + totalP.magnitude());
        }

        assertTrue(maxDistError <= POSITION_TOLERANCE,
                "Distance link error must be <= 1 µm, but was: " + maxDistError);
    }

    @Test
    @DisplayName("Distance Link: Rejects coincident endpoints where direction normal is undefined")
    void distanceLinkRejectsCoincidentEndpoints() {
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(1.0, 1.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 2.0, 0.2, 0.2, new Vector2D(1.0, 1.0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                DistanceLinkConstraint.betweenBodies("badLink", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.0)
        );
        assertTrue(ex.getMessage().contains("endpoints cannot coincide"),
                "Exception message must explain undefined direction normal: " + ex.getMessage());

        IllegalArgumentException exAnchor = assertThrows(IllegalArgumentException.class, () ->
                DistanceLinkConstraint.toAnchor("badAnchorLink", b1, Vector2D.ZERO, new Vector2D(1.0, 1.0), 1.0)
        );
        assertTrue(exAnchor.getMessage().contains("endpoints cannot coincide"),
                "Exception message must explain undefined direction normal: " + exAnchor.getMessage());
    }

    @Test
    @DisplayName("Redundancy Detection: Duplicate constraints detected and handled without numerical singularity")
    void redundantConstraintsDetectedWithoutSingularity() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 3.0, 0.2, 0.2, new Vector2D(2.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        DistanceLinkConstraint link1 = DistanceLinkConstraint.betweenBodies(
                "link_primary", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);
        DistanceLinkConstraint link2 = DistanceLinkConstraint.betweenBodies(
                "link_duplicate", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);

        world.addConstraint(link1);
        world.addConstraint(link2);

        world.addForceGenerator((body, time) -> {
            if (body.id().equals(b1.id())) {
                body.applyForce(new Vector2D(-50.0, 20.0));
            } else if (body.id().equals(b2.id())) {
                body.applyForce(new Vector2D(50.0, -20.0));
            }
        });

        for (int step = 0; step < 200; step++) {
            world.stepSingle();
            double dist = b1.position().distanceTo(b2.position());
            assertTrue(Double.isFinite(dist), "Distance must be finite");
            assertTrue(Math.abs(dist - 2.0) <= POSITION_TOLERANCE,
                    "Distance error under duplicate constraint must be <= 1 µm, got: " + Math.abs(dist - 2.0));
        }
    }

    @Test
    @DisplayName("Active Coordinate Reduction: Point mass (2 DOFs) and rigid body (3 DOFs) successfully constrained")
    void pointMassParticipatesInDistanceLink() {
        SimulationWorld world = SimulationWorld.withGravity(9.81);

        RigidBody pointMass = RigidBody.pointMass("pm", 2.0, new Vector2D(0.0, 2.0), Vector2D.ZERO);
        RigidBody block = RigidBody.dynamicBlock("block", 4.0, 0.4, 0.4, new Vector2D(1.5, 2.0));

        world.addBody(pointMass);
        world.addBody(block);

        DistanceLinkConstraint link = DistanceLinkConstraint.betweenBodies(
                "pmLink", pointMass, Vector2D.ZERO, block, Vector2D.ZERO, 1.5);
        world.addConstraint(link);

        double maxErr = 0.0;
        for (int step = 0; step < 500; step++) {
            world.stepSingle();
            double dist = pointMass.position().distanceTo(block.position());
            maxErr = Math.max(maxErr, Math.abs(dist - 1.5));
            assertTrue(Double.isFinite(dist));
        }

        assertTrue(maxErr <= POSITION_TOLERANCE,
                "Point mass distance link error must remain <= 1 µm, but was: " + maxErr);
        assertEquals(0.0, pointMass.angularVelocity(), 1e-12);
        assertEquals(0.0, pointMass.state().angularAcceleration(), 1e-12);
    }

    @Test
    @DisplayName("Bilateral Body-to-Body Revolute Joint: Conserves total linear and angular momentum in zero gravity")
    void revoluteJointBetweenTwoDynamicBodies() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        RigidBody bA = RigidBody.dynamicBlock("bA", 2.0, 1.0, 0.2, new Vector2D(-0.5, 0.0));
        RigidBody bB = RigidBody.dynamicBlock("bB", 3.0, 2.0, 0.2, new Vector2D(1.0, 0.0));

        // Kinematically consistent initial velocities with hinge at (0, 0) (vPtA = vPtB = (0, 0)):
        bA.state().setVelocity(new Vector2D(0.0, -0.5));
        bA.state().setAngularVelocity(1.0);
        bB.state().setVelocity(new Vector2D(0.0, -1.0));
        bB.state().setAngularVelocity(-1.0);

        world.addBody(bA);
        world.addBody(bB);

        RevoluteJointConstraint hinge = RevoluteJointConstraint.pinBodies(
                "hingeAB", bA.id(), new Vector2D(0.5, 0.0), bB.id(), new Vector2D(-1.0, 0.0));
        world.addConstraint(hinge);

        Vector2D initialP = world.totalLinearMomentum();
        double initialL = world.totalAngularMomentumAbout(Vector2D.ZERO);

        double maxHingeDrift = 0.0;
        for (int step = 0; step < 1000; step++) {
            world.stepSingle();

            Vector2D ptA = bA.position().add(new Vector2D(0.5, 0.0).rotate(bA.angle()));
            Vector2D ptB = bB.position().add(new Vector2D(-1.0, 0.0).rotate(bB.angle()));
            double hingeDrift = ptA.distanceTo(ptB);
            maxHingeDrift = Math.max(maxHingeDrift, hingeDrift);

            Vector2D currentP = world.totalLinearMomentum();
            double pDrift = currentP.subtract(initialP).magnitude();
            assertTrue(pDrift <= MOMENTUM_TOLERANCE,
                    "Linear momentum drift exceeded machine tolerance at step " + step + ": " + pDrift);

            double currentL = world.totalAngularMomentumAbout(Vector2D.ZERO);
            double lDrift = Math.abs(currentL - initialL);
            assertTrue(lDrift <= ANGULAR_MOMENTUM_DRIFT_TOLERANCE,
                    "Angular momentum drift exceeded physical tolerance at step " + step + ": " + lDrift);
        }

        assertTrue(maxHingeDrift <= POSITION_TOLERANCE,
                "Hinge attachment drift must remain <= 1 µm, but was: " + maxHingeDrift);
    }

    @Test
    @DisplayName("Inconsistency Detection: Gross conflicting constraints throw IllegalStateException")
    void inconsistentConstraintsThrowException() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 2.0, 0.2, 0.2, new Vector2D(1.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        DistanceLinkConstraint link1 = DistanceLinkConstraint.betweenBodies(
                "link1", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.0);
        DistanceLinkConstraint link2 = DistanceLinkConstraint.betweenBodies(
                "link2", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);

        world.addConstraint(link1);
        world.addConstraint(link2);

        IllegalStateException ex = assertThrows(IllegalStateException.class, world::stepSingle);
        assertTrue(ex.getMessage().contains("Inconsistent conflicting constraint detected"),
                "Exception must describe kinematic inconsistency: " + ex.getMessage());
    }

    @Test
    @DisplayName("Inconsistency Detection: Conflicting constraints differing by only 50 µm are rejected under 1e-6 m tolerance")
    void conflictingConstraintsDifferingBy50MicrometresRejected() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 2.0, 0.2, 0.2, new Vector2D(1.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        // Link 1 requires d = 1.000000 m, Link 2 requires d = 1.000050 m (conflict = 50 µm).
        // Under the former 1e-3 tolerance, this would be falsely classified as redundant.
        // Under the restored 1e-6 m position consistency tolerance, this must be rejected!
        DistanceLinkConstraint link1 = DistanceLinkConstraint.betweenBodies(
                "link1", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.000000);
        DistanceLinkConstraint link2 = DistanceLinkConstraint.betweenBodies(
                "link2", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.000050);

        world.addConstraint(link1);
        world.addConstraint(link2);

        IllegalStateException ex = assertThrows(IllegalStateException.class, world::stepSingle);
        assertTrue(ex.getMessage().contains("Inconsistent conflicting constraint detected"),
                "50 µm conflict must be caught as inconsistent: " + ex.getMessage());
    }

    @Test
    @DisplayName("Multiple Dependent Rows: Three identical constraints excised cleanly without matrix singularity")
    void multipleDependentRowsHandledCorrectly() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 3.0, 0.2, 0.2, new Vector2D(2.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        // Three identical distance links between the same bodies (2 redundant rows)
        DistanceLinkConstraint l1 = DistanceLinkConstraint.betweenBodies("l1", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);
        DistanceLinkConstraint l2 = DistanceLinkConstraint.betweenBodies("l2", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);
        DistanceLinkConstraint l3 = DistanceLinkConstraint.betweenBodies("l3", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);

        world.addConstraint(l1);
        world.addConstraint(l2);
        world.addConstraint(l3);

        world.addForceGenerator((body, time) -> {
            if (body.id().equals(b1.id())) body.applyForce(new Vector2D(-20.0, 10.0));
        });

        // Step 100 times: must not throw exception, NaN, or crash
        for (int i = 0; i < 100; i++) {
            world.stepSingle();
            double dist = b1.position().distanceTo(b2.position());
            assertTrue(Double.isFinite(dist));
            assertTrue(Math.abs(dist - 2.0) <= POSITION_TOLERANCE);
        }
    }

    @Test
    @DisplayName("Nearly Dependent Constraints: Closely spaced attachments (2 mm apart) solved as independent")
    void nearlyDependentConstraintsSolvedAsIndependent() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.5, 0.5, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 2.0, 0.5, 0.5, new Vector2D(2.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        // Two parallel links with attachment points separated by 2 mm (0.002 m).
        // Link 1 at y = 0, Link 2 at y = 0.002.
        // Because attachment points differ, they independently constrain relative orientation.
        DistanceLinkConstraint l1 = DistanceLinkConstraint.betweenBodies(
                "linkA", b1, new Vector2D(0.0, 0.0), b2, new Vector2D(0.0, 0.0), 2.0);
        DistanceLinkConstraint l2 = DistanceLinkConstraint.betweenBodies(
                "linkB", b1, new Vector2D(0.0, 0.002), b2, new Vector2D(0.0, 0.002), 2.0);

        world.addConstraint(l1);
        world.addConstraint(l2);

        for (int i = 0; i < 50; i++) {
            world.stepSingle();
        }

        Vector2D pA1 = b1.position().add(new Vector2D(0.0, 0.0).rotate(b1.angle()));
        Vector2D pB1 = b2.position().add(new Vector2D(0.0, 0.0).rotate(b2.angle()));
        Vector2D pA2 = b1.position().add(new Vector2D(0.0, 0.002).rotate(b1.angle()));
        Vector2D pB2 = b2.position().add(new Vector2D(0.0, 0.002).rotate(b2.angle()));

        assertEquals(2.0, pA1.distanceTo(pB1), POSITION_TOLERANCE);
        assertEquals(2.0, pA2.distanceTo(pB2), POSITION_TOLERANCE);
    }

    @Test
    @DisplayName("Projection Failure: Incompatible kinematic configuration detected and throws exception")
    void positionProjectionNonConvergenceDetected() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.2, 0.2, new Vector2D(0.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 2.0, 0.2, 0.2, new Vector2D(10.0, 0.0));
        world.addBody(b1);
        world.addBody(b2);

        // Body 1 is pinned to (0, 0), Body 2 is pinned to (10, 0).
        // Distance between them is fixed at 10.0 m.
        // Link demands distance = 1.0 m. This is impossible and cannot converge!
        world.addConstraint(RevoluteJointConstraint.pinToAnchor("pin1", b1.id(), Vector2D.ZERO, new Vector2D(0.0, 0.0)));
        world.addConstraint(RevoluteJointConstraint.pinToAnchor("pin2", b2.id(), Vector2D.ZERO, new Vector2D(10.0, 0.0)));
        world.addConstraint(DistanceLinkConstraint.betweenBodies("linkImpossible", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 1.0));

        // Stepping must throw IllegalStateException due to projection failure or inconsistency
        assertThrows(IllegalStateException.class, world::stepSingle);
    }

    @Test
    @DisplayName("Velocity Projection: Verified that velocity error satisfies VELOCITY_TOLERANCE after step")
    void velocityProjectionResidualVerification() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);
        RigidBody b1 = RigidBody.dynamicBlock("b1", 2.0, 0.4, 0.4, new Vector2D(-1.0, 0.0));
        RigidBody b2 = RigidBody.dynamicBlock("b2", 3.0, 0.4, 0.4, new Vector2D(1.0, 0.0));

        // Non-zero velocities along link
        b1.state().setVelocity(new Vector2D(1.0, 0.5));
        b2.state().setVelocity(new Vector2D(-0.5, -0.2));

        world.addBody(b1);
        world.addBody(b2);

        DistanceLinkConstraint link = DistanceLinkConstraint.betweenBodies(
                "link", b1, Vector2D.ZERO, b2, Vector2D.ZERO, 2.0);
        world.addConstraint(link);

        world.stepSingle();

        // Check relative velocity along link
        Vector2D u = b2.position().subtract(b1.position()).normalize();
        double vRelAlongLink = u.dot(b1.velocity().subtract(b2.velocity()));
        assertTrue(Math.abs(vRelAlongLink) <= VELOCITY_TOLERANCE,
                "Relative velocity along link must satisfy VELOCITY_TOLERANCE, got: " + Math.abs(vRelAlongLink));
    }

    @Test
    @DisplayName("Revolute Joint: 2D non-zero attachment offsets on both bodies conserve momentum and anchor")
    void revoluteJointWithNonZero2DAttachmentOffsets() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        // Body A: local anchor (0.3, 0.2)
        // Body B: local anchor (-0.2, 0.4)
        // Let hinge be at world origin (0.0, 0.0).
        // Then posA = (0, 0) - (0.3, 0.2) = (-0.3, -0.2).
        // Then posB = (0, 0) - (-0.2, 0.4) = (0.2, -0.4).
        Vector2D localAnchorA = new Vector2D(0.3, 0.2);
        Vector2D localAnchorB = new Vector2D(-0.2, 0.4);

        RigidBody bA = RigidBody.dynamicBlock("bA", 2.0, 0.8, 0.6, new Vector2D(-0.3, -0.2));
        RigidBody bB = RigidBody.dynamicBlock("bB", 3.0, 0.8, 1.0, new Vector2D(0.2, -0.4));

        // Kinematically consistent angular velocities about hinge (0, 0)
        // omegaA = 1.0 rad/s => vA = (-omegaA * rA_y, omegaA * rA_x) with rA = posA = (-0.3, -0.2)
        // vA = (-1.0 * (-0.2), 1.0 * (-0.3)) = (0.2, -0.3)
        // omegaB = 1.0 rad/s => vB = (-1.0 * (-0.4), 1.0 * 0.2) = (0.4, 0.2)
        bA.state().setAngularVelocity(1.0);
        bA.state().setVelocity(new Vector2D(0.2, -0.3));
        bB.state().setAngularVelocity(1.0);
        bB.state().setVelocity(new Vector2D(0.4, 0.2));

        world.addBody(bA);
        world.addBody(bB);

        RevoluteJointConstraint hinge = RevoluteJointConstraint.pinBodies(
                "hinge2D", bA.id(), localAnchorA, bB.id(), localAnchorB);
        world.addConstraint(hinge);

        Vector2D initialP = world.totalLinearMomentum();

        for (int step = 0; step < 500; step++) {
            world.stepSingle();

            Vector2D ptA = bA.position().add(localAnchorA.rotate(bA.angle()));
            Vector2D ptB = bB.position().add(localAnchorB.rotate(bB.angle()));
            double hingeDrift = ptA.distanceTo(ptB);
            assertTrue(hingeDrift <= POSITION_TOLERANCE,
                    "Hinge drift with 2D offsets must be <= 1 µm, got: " + hingeDrift);

            Vector2D currentP = world.totalLinearMomentum();
            assertTrue(currentP.subtract(initialP).magnitude() <= MOMENTUM_TOLERANCE,
                    "Linear momentum must be conserved");
        }
    }

    @Test
    @DisplayName("Point Mass: Multiple simultaneous constraints (triangulated suspension) maintained under gravity")
    void pointMassWithMultipleConstraints() {
        SimulationWorld world = SimulationWorld.withGravity(9.81);

        // Triangulated point mass hanging from two anchors:
        // Anchor 1 at (-1.0, 0.0), Anchor 2 at (1.0, 0.0)
        // Point mass initially at (0.0, -1.0).
        // Target distance to Anchor 1: sqrt(1^2 + 1^2) = sqrt(2) m.
        // Target distance to Anchor 2: sqrt(2) m.
        double targetDist = Math.sqrt(2.0);
        RigidBody pm = RigidBody.pointMass("pm", 2.0, new Vector2D(0.0, -1.0), Vector2D.ZERO);
        world.addBody(pm);

        DistanceLinkConstraint link1 = DistanceLinkConstraint.toAnchor(
                "linkLeft", pm, Vector2D.ZERO, new Vector2D(-1.0, 0.0), targetDist);
        DistanceLinkConstraint link2 = DistanceLinkConstraint.toAnchor(
                "linkRight", pm, Vector2D.ZERO, new Vector2D(1.0, 0.0), targetDist);

        world.addConstraint(link1);
        world.addConstraint(link2);

        for (int step = 0; step < 500; step++) {
            world.stepSingle();

            double d1 = pm.position().distanceTo(new Vector2D(-1.0, 0.0));
            double d2 = pm.position().distanceTo(new Vector2D(1.0, 0.0));

            assertEquals(targetDist, d1, POSITION_TOLERANCE, "Link 1 must maintain target distance");
            assertEquals(targetDist, d2, POSITION_TOLERANCE, "Link 2 must maintain target distance");
        }
    }

    @Test
    @DisplayName("LDLT Pivot Classification: Distinguishes independent, small roundoff negative, and materially negative pivots")
    void negativePivotHandlingDistinguishesRoundoffFromMaterial() {
        // Construct mock 2x2 system
        List<ConstraintRow> rows = new ArrayList<>();
        ConstraintRow r1 = new ConstraintRow("c1", 0, 2);
        r1.setJacobian(0, 1.0);
        r1.setEffectiveMass(1.0);
        ConstraintRow r2 = new ConstraintRow("c2", 0, 2);
        r2.setJacobian(1, 1.0);
        r2.setEffectiveMass(1.0);
        rows.add(r1);
        rows.add(r2);

        double[] s = new double[]{1.0, 1.0};
        double[] b = new double[]{0.0, 0.0};

        // Case 1: Positive pivot d_k >= RANK_TOLERANCE (1e-8) => independent
        double[][] A_pos = new double[][]{
                {1.0, 0.0},
                {0.0, 1.0e-7}
        };
        DirectLinearConstraintSolver.FactorizationResult factPos =
                DirectLinearConstraintSolver.factorizeUnregularized(
                        A_pos, b, s, rows, 2, DirectLinearConstraintSolver.ACCELERATION_CONSISTENCY_TOLERANCE);
        assertFalse(factPos.isRedundant()[1], "Pivot >= 1e-8 must be classified as independent");
        assertEquals(1.0e-7, factPos.D()[1], 1e-15);

        // Case 2: Small negative roundoff pivot within [-NEGATIVE_PIVOT_TOLERANCE, RANK_TOLERANCE)
        // e.g. d_2 = -5.0e-11 (where NEGATIVE_PIVOT_TOLERANCE = 1.0e-10) => rank-deficient / redundant
        double[][] A_roundoff = new double[][]{
                {1.0, 0.0},
                {0.0, -5.0e-11}
        };
        DirectLinearConstraintSolver.FactorizationResult factRoundoff =
                DirectLinearConstraintSolver.factorizeUnregularized(
                        A_roundoff, b, s, rows, 2, DirectLinearConstraintSolver.ACCELERATION_CONSISTENCY_TOLERANCE);
        assertTrue(factRoundoff.isRedundant()[1],
                "Small negative roundoff pivot (-5e-11) within [-1e-10, 1e-8) must be classified as rank-deficient");
        assertEquals(0.0, factRoundoff.D()[1], "Redundant pivot must be clamped to 0.0");

        // Case 3: Materially negative pivot d_k < -NEGATIVE_PIVOT_TOLERANCE (-1.0e-10)
        // e.g. d_2 = -1.0e-8 => mathematical/numerical failure, must throw IllegalStateException
        double[][] A_materialNegative = new double[][]{
                {1.0, 0.0},
                {0.0, -1.0e-8}
        };
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DirectLinearConstraintSolver.factorizeUnregularized(
                        A_materialNegative, b, s, rows, 2, DirectLinearConstraintSolver.ACCELERATION_CONSISTENCY_TOLERANCE)
        );
        assertTrue(ex.getMessage().contains("Materially negative pivot"),
                "Exception message must report materially negative pivot: " + ex.getMessage());
    }

    @Test
    @DisplayName("Delassus Mobility Threshold: Zero mobility row (w_ii <= MIN_DELASSUS_MOBILITY) throws IllegalStateException")
    void zeroDelassusMobilityThrowsIllegalStateException() {
        DirectLinearConstraintSolver solver = new DirectLinearConstraintSolver();
        CoordinateMapping mapping = CoordinateMapping.of(List.of(
                RigidBody.dynamicDisc("b1", 1.0, 0.5, new Vector2D(0, 0))
        ));

        // Create a row with all-zero Jacobian entries -> w_ii = 0.0 <= MIN_DELASSUS_MOBILITY
        ConstraintRow zeroMobilityRow = new ConstraintRow("zero_mob", 0, mapping.totalDof());
        List<ConstraintRow> rows = List.of(zeroMobilityRow);
        double[] extAcc = new double[mapping.totalDof()];

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                solver.solve(mapping, rows, extAcc, 5.0, 5.0)
        );
        assertTrue(ex.getMessage().contains("Delassus mobility"),
                "Exception message must mention Delassus mobility: " + ex.getMessage());
    }
}
