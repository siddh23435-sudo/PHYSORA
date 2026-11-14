package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.Vector2D;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bilateral 1-DOF rigid distance link constraint.
 *
 * <p>Maintains an exact physical distance d > 0 between two attachment points:
 * <pre>
 *   Φ(q) = ||p_A(q) - p_B(q)|| - d = 0 ∈ ℝ¹
 * </pre>
 *
 * <p>Rejects coincident initial endpoints (||p_A - p_B|| < 10⁻⁶ m) at construction to guarantee
 * a well-defined constraint normal vector.
 */
public final class DistanceLinkConstraint implements MechanicalConstraint {

    public static final double MIN_INITIAL_SEPARATION = 1.0e-6; // 1 micrometre
    public static final double MIN_RUNTIME_SEPARATION = 1.0e-9; // 1 nanometre (numerical singularity floor)

    private final String constraintId;
    private final UUID bodyAId;
    private final Vector2D localAnchorA;
    private final UUID bodyBId; // null if linked to fixed world anchor
    private final Vector2D localAnchorBOrWorld;
    private final double targetDistance;

    private DistanceLinkConstraint(
            String constraintId,
            UUID bodyAId,
            Vector2D localAnchorA,
            UUID bodyBId,
            Vector2D localAnchorBOrWorld,
            double targetDistance) {

        this.constraintId = Objects.requireNonNull(constraintId, "constraintId is required");
        this.bodyAId = Objects.requireNonNull(bodyAId, "bodyAId is required");
        this.localAnchorA = Objects.requireNonNull(localAnchorA, "localAnchorA is required");
        this.bodyBId = bodyBId;
        this.localAnchorBOrWorld = Objects.requireNonNull(localAnchorBOrWorld, "anchor is required");

        if (!Double.isFinite(targetDistance) || targetDistance <= 0.0) {
            throw new IllegalArgumentException("targetDistance must be finite and > 0: " + targetDistance);
        }
        this.targetDistance = targetDistance;
    }

    public static DistanceLinkConstraint betweenBodies(
            String constraintId,
            RigidBody bodyA,
            Vector2D localAnchorA,
            RigidBody bodyB,
            Vector2D localAnchorB,
            double targetDistance) {

        Objects.requireNonNull(bodyA, "bodyA is required");
        Objects.requireNonNull(bodyB, "bodyB is required");

        Vector2D pA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
        Vector2D pB = bodyB.position().add(localAnchorB.rotate(bodyB.angle()));
        double initialDistance = pA.distanceTo(pB);

        if (initialDistance < MIN_INITIAL_SEPARATION) {
            throw new IllegalArgumentException(
                    "Distance link endpoints cannot coincide (distance=" + initialDistance + " < "
                            + MIN_INITIAL_SEPARATION + " m); constraint direction is undefined");
        }

        return new DistanceLinkConstraint(constraintId, bodyA.id(), localAnchorA, bodyB.id(), localAnchorB, targetDistance);
    }

    public static DistanceLinkConstraint toAnchor(
            String constraintId,
            RigidBody bodyA,
            Vector2D localAnchorA,
            Vector2D worldAnchor,
            double targetDistance) {

        Objects.requireNonNull(bodyA, "bodyA is required");
        Objects.requireNonNull(worldAnchor, "worldAnchor is required");

        Vector2D pA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
        double initialDistance = pA.distanceTo(worldAnchor);

        if (initialDistance < MIN_INITIAL_SEPARATION) {
            throw new IllegalArgumentException(
                    "Distance link endpoints cannot coincide (distance=" + initialDistance + " < "
                            + MIN_INITIAL_SEPARATION + " m); constraint direction is undefined");
        }

        return new DistanceLinkConstraint(constraintId, bodyA.id(), localAnchorA, null, worldAnchor, targetDistance);
    }

    @Override
    public String constraintId() {
        return constraintId;
    }

    @Override
    public int dimension() {
        return 1;
    }

    public UUID bodyAId() {
        return bodyAId;
    }

    public UUID bodyBId() {
        return bodyBId;
    }

    public boolean isLinkedToWorld() {
        return bodyBId == null;
    }

    public double targetDistance() {
        return targetDistance;
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
            pB = localAnchorBOrWorld;
            vPtB = Vector2D.ZERO;
        }

        Vector2D deltaP = pA.subtract(pB);
        double currentDistance = deltaP.magnitude();
        if (currentDistance < MIN_RUNTIME_SEPARATION) {
            throw new IllegalStateException("Distance link endpoints collapsed (dist < "
                    + MIN_RUNTIME_SEPARATION + " m); direction is singular");
        }

        Vector2D u = deltaP.multiply(1.0 / currentDistance);
        double positionError = currentDistance - targetDistance;

        Vector2D vRel = vPtA.subtract(vPtB);
        double velocityError = u.dot(vRel);

        // Centripetal acceleration of relative motion along link
        double centripetal = (vRel.magnitudeSquared() - velocityError * velocityError) / currentDistance;
        centripetal -= omegaA * omegaA * rA.dot(u);
        if (bodyB != null && !bodyB.isStatic()) {
            centripetal += omegaB * omegaB * rB.dot(u);
        }

        int totalDof = mapping.totalDof();
        ConstraintRow row = new ConstraintRow(constraintId, 0, totalDof);

        if (mapping.isDynamic(bodyAId)) {
            int idxA = mapping.startIndex(bodyAId);
            row.setJacobian(idxA, u.x());
            row.setJacobian(idxA + 1, u.y());

            if (mapping.hasRotation(bodyAId)) {
                // rA × u = rA_x * u_y - rA_y * u_x
                double torqueArmA = rA.x() * u.y() - rA.y() * u.x();
                row.setJacobian(idxA + 2, torqueArmA);
            }
        }

        if (bodyBId != null && mapping.isDynamic(bodyBId)) {
            int idxB = mapping.startIndex(bodyBId);
            row.setJacobian(idxB, -u.x());
            row.setJacobian(idxB + 1, -u.y());

            if (mapping.hasRotation(bodyBId)) {
                // -(rB × u) = -(rB_x * u_y - rB_y * u_x)
                double torqueArmB = -(rB.x() * u.y() - rB.y() * u.x());
                row.setJacobian(idxB + 2, torqueArmB);
            }
        }

        row.setPositionError(positionError);
        row.setCentripetalAccel(centripetal);
        row.setVelocityError(velocityError);

        rows.add(row);
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

        double currentDistance = pA.distanceTo(pB);
        errors[offset] = currentDistance - targetDistance;
    }

    @Override
    public void evaluateVelocityErrors(
            CoordinateMapping mapping, Map<UUID, RigidBody> bodies, double[] errors, int offset) {

        RigidBody bodyA = bodies.get(bodyAId);
        Vector2D rA = localAnchorA.rotate(bodyA.angle());
        Vector2D vPtA = bodyA.velocity().add(new Vector2D(-bodyA.angularVelocity() * rA.y(), bodyA.angularVelocity() * rA.x()));

        Vector2D pA = bodyA.position().add(rA);
        Vector2D pB;
        Vector2D vPtB;

        if (bodyBId != null) {
            RigidBody bodyB = bodies.get(bodyBId);
            Vector2D rB = localAnchorBOrWorld.rotate(bodyB.angle());
            pB = bodyB.position().add(rB);
            vPtB = bodyB.velocity().add(new Vector2D(-bodyB.angularVelocity() * rB.y(), bodyB.angularVelocity() * rB.x()));
        } else {
            pB = localAnchorBOrWorld;
            vPtB = Vector2D.ZERO;
        }

        Vector2D deltaP = pA.subtract(pB);
        double dist = deltaP.magnitude();
        if (dist > MIN_RUNTIME_SEPARATION) {
            Vector2D u = deltaP.multiply(1.0 / dist);
            errors[offset] = u.dot(vPtA.subtract(vPtB));
        } else {
            errors[offset] = 0.0;
        }
    }
}

