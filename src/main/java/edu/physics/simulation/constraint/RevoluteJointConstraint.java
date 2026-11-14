package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.Vector2D;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bilateral 2-DOF revolute joint (pin) constraint.
 *
 * <p>Pins a local point on Body A to a fixed world anchor, or pins a local point on Body A to a local point on Body B:
 * <pre>
 *   Φ(q) = p_A(q) - p_B(q) = 0 ∈ ℝ²
 * </pre>
 *
 * <p>Supports variable coordinate reductions (rigid body with 3 DOFs vs point mass with 2 DOFs).
 */
public final class RevoluteJointConstraint implements MechanicalConstraint {

    private final String constraintId;
    private final UUID bodyAId;
    private final Vector2D localAnchorA;
    private final UUID bodyBId; // null if pinned to fixed world anchor
    private final Vector2D localAnchorBOrWorld;

    private RevoluteJointConstraint(
            String constraintId,
            UUID bodyAId,
            Vector2D localAnchorA,
            UUID bodyBId,
            Vector2D localAnchorBOrWorld) {
        this.constraintId = Objects.requireNonNull(constraintId, "constraintId is required");
        this.bodyAId = Objects.requireNonNull(bodyAId, "bodyAId is required");
        this.localAnchorA = Objects.requireNonNull(localAnchorA, "localAnchorA is required");
        this.bodyBId = bodyBId;
        this.localAnchorBOrWorld = Objects.requireNonNull(localAnchorBOrWorld, "anchor is required");
    }

    public static RevoluteJointConstraint pinToAnchor(
            String constraintId, UUID bodyId, Vector2D localAnchor, Vector2D worldAnchor) {
        return new RevoluteJointConstraint(constraintId, bodyId, localAnchor, null, worldAnchor);
    }

    public static RevoluteJointConstraint pinBodies(
            String constraintId, UUID bodyAId, Vector2D localAnchorA, UUID bodyBId, Vector2D localAnchorB) {
        Objects.requireNonNull(bodyBId, "bodyBId is required");
        return new RevoluteJointConstraint(constraintId, bodyAId, localAnchorA, bodyBId, localAnchorB);
    }

    @Override
    public String constraintId() {
        return constraintId;
    }

    @Override
    public int dimension() {
        return 2;
    }

    public UUID bodyAId() {
        return bodyAId;
    }

    public UUID bodyBId() {
        return bodyBId;
    }

    public boolean isPinnedToWorld() {
        return bodyBId == null;
    }

    public Vector2D localAnchorA() {
        return localAnchorA;
    }

    public Vector2D localAnchorBOrWorld() {
        return localAnchorBOrWorld;
    }

    @Override
    public void evaluate(
            CoordinateMapping mapping, Map<UUID, RigidBody> bodies, List<ConstraintRow> rows, double dt) {

        RigidBody bodyA = bodies.get(bodyAId);
        if (bodyA == null) {
            throw new IllegalStateException("Body A does not exist: " + bodyAId);
        }

        Vector2D posA = bodyA.position();
        double angleA = bodyA.angle();
        Vector2D velA = bodyA.velocity();
        double omegaA = bodyA.angularVelocity();

        Vector2D rA = localAnchorA.rotate(angleA);
        Vector2D pA = posA.add(rA);
        Vector2D vPtA = velA.add(new Vector2D(-omegaA * rA.y(), omegaA * rA.x()));

        Vector2D pB;
        Vector2D vPtB;
        Vector2D rB = Vector2D.ZERO;
        double omegaB = 0.0;
        RigidBody bodyB = null;

        if (bodyBId != null) {
            bodyB = bodies.get(bodyBId);
            if (bodyB == null) {
                throw new IllegalStateException("Body B does not exist: " + bodyBId);
            }
            Vector2D posB = bodyB.position();
            double angleB = bodyB.angle();
            Vector2D velB = bodyB.velocity();
            omegaB = bodyB.angularVelocity();

            rB = localAnchorBOrWorld.rotate(angleB);
            pB = posB.add(rB);
            vPtB = velB.add(new Vector2D(-omegaB * rB.y(), omegaB * rB.x()));
        } else {
            pB = localAnchorBOrWorld; // Fixed world point
            vPtB = Vector2D.ZERO;
        }

        Vector2D phi = pA.subtract(pB);
        Vector2D vRel = vPtA.subtract(vPtB);

        // Centripetal accelerations: -omegaA² rA + (omegaB² rB if dynamic)
        double centripetalX = -omegaA * omegaA * rA.x();
        double centripetalY = -omegaA * omegaA * rA.y();
        if (bodyB != null && !bodyB.isStatic()) {
            centripetalX += omegaB * omegaB * rB.x();
            centripetalY += omegaB * omegaB * rB.y();
        }

        int totalDof = mapping.totalDof();
        ConstraintRow rowX = new ConstraintRow(constraintId, 0, totalDof);
        ConstraintRow rowY = new ConstraintRow(constraintId, 1, totalDof);

        // Body A Jacobian
        if (mapping.isDynamic(bodyAId)) {
            int idxA = mapping.startIndex(bodyAId);
            rowX.setJacobian(idxA, 1.0);
            rowX.setJacobian(idxA + 1, 0.0);
            rowY.setJacobian(idxA, 0.0);
            rowY.setJacobian(idxA + 1, 1.0);

            if (mapping.hasRotation(bodyAId)) {
                rowX.setJacobian(idxA + 2, -rA.y());
                rowY.setJacobian(idxA + 2, rA.x());
            }
        }

        // Body B Jacobian
        if (bodyBId != null && mapping.isDynamic(bodyBId)) {
            int idxB = mapping.startIndex(bodyBId);
            rowX.setJacobian(idxB, -1.0);
            rowX.setJacobian(idxB + 1, 0.0);
            rowY.setJacobian(idxB, 0.0);
            rowY.setJacobian(idxB + 1, -1.0);

            if (mapping.hasRotation(bodyBId)) {
                rowX.setJacobian(idxB + 2, rB.y());
                rowY.setJacobian(idxB + 2, -rB.x());
            }
        }

        rowX.setPositionError(phi.x());
        rowX.setCentripetalAccel(centripetalX);
        rowX.setVelocityError(vRel.x());

        rowY.setPositionError(phi.y());
        rowY.setCentripetalAccel(centripetalY);
        rowY.setVelocityError(vRel.y());

        rows.add(rowX);
        rows.add(rowY);
    }

    @Override
    public void evaluatePositionErrors(Map<UUID, RigidBody> bodies, double[] errors, int offset) {
        RigidBody bodyA = bodies.get(bodyAId);
        Vector2D pA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));

        Vector2D pB;
        if (bodyBId != null) {
            RigidBody bodyB = bodies.get(bodyBId);
            pB = bodyB.position().add(localAnchorBOrWorld.rotate(bodyB.angle()));
        } else {
            pB = localAnchorBOrWorld;
        }

        Vector2D phi = pA.subtract(pB);
        errors[offset] = phi.x();
        errors[offset + 1] = phi.y();
    }

    @Override
    public void evaluateVelocityErrors(
            CoordinateMapping mapping, Map<UUID, RigidBody> bodies, double[] errors, int offset) {

        RigidBody bodyA = bodies.get(bodyAId);
        Vector2D rA = localAnchorA.rotate(bodyA.angle());
        Vector2D vPtA = bodyA.velocity().add(new Vector2D(-bodyA.angularVelocity() * rA.y(), bodyA.angularVelocity() * rA.x()));

        Vector2D vPtB;
        if (bodyBId != null) {
            RigidBody bodyB = bodies.get(bodyBId);
            Vector2D rB = localAnchorBOrWorld.rotate(bodyB.angle());
            vPtB = bodyB.velocity().add(new Vector2D(-bodyB.angularVelocity() * rB.y(), bodyB.angularVelocity() * rB.x()));
        } else {
            vPtB = Vector2D.ZERO;
        }

        Vector2D vRel = vPtA.subtract(vPtB);
        errors[offset] = vRel.x();
        errors[offset + 1] = vRel.y();
    }
}

