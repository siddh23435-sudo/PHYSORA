package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.Vector2D;
import edu.physics.simulation.scene.AnchorRef;
import edu.physics.simulation.scene.AttachmentPoint;
import edu.physics.simulation.scene.CableTopology;
import edu.physics.simulation.scene.LaboratoryScene;
import edu.physics.simulation.scene.SceneAnchor;
import edu.physics.simulation.scene.SceneBody;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Geometric and kinematic evaluator for direct, point-to-point inextensible cables.
 *
 * <p>Authoritatively manages attachment coordinate evaluation, cable length L,
 * gap function g(q) = L_0 - L(q), unit direction vector u (pointing from anchor/body B toward body A),
 * gap rate g_dot = -u · v_rel, convective acceleration gamma_conv = J_dot · v, and constraint Jacobian rows.
 *
 * <p>Endpoint Normalization Policy:
 * <ul>
 *   <li>Dynamic Body ↔ Dynamic Body: Preserved in canonical topology order.</li>
 *   <li>Dynamic Body → World Anchor: Assigned to Slot A (Dynamic) and Slot B (World Anchor).</li>
 *   <li>World Anchor → Dynamic Body: Automatically swapped to Slot A (Dynamic) and Slot B (World Anchor).</li>
 *   <li>Dynamic Body ↔ Static Body: Static body's attachment point is resolved to world space and treated as fixed world anchor.</li>
 *   <li>World Anchor ↔ World Anchor (or Static ↔ Static): Rejected at construction (zero dynamic degrees of freedom).</li>
 * </ul>
 *
 * <p>Pulling Tension Invariant:
 * The generalized constraint force is f = J^T lambda with lambda >= 0.
 * For Slot A (dynamic body), f_A = -u lambda, which pulls toward Slot B.
 * For Slot B (if dynamic), f_B = +u lambda, which pulls toward Slot A.
 */
public final class DirectCableGeometry {

    public static final double MIN_INITIAL_SEPARATION = 1.0e-6; // 1 micrometre
    public static final double MIN_RUNTIME_SEPARATION = 1.0e-9; // 1 nanometre

    private final String cableId;
    private final AnchorRef startAnchorRef;
    private final AnchorRef endAnchorRef;
    private final UUID bodyAId;
    private final Vector2D localAnchorA;
    private final UUID bodyBId; // null if linked to stationary world anchor
    private final Vector2D localAnchorBOrWorld;
    private final double restLength; // L_0

    private DirectCableGeometry(
            String cableId,
            AnchorRef startAnchorRef,
            AnchorRef endAnchorRef,
            UUID bodyAId,
            Vector2D localAnchorA,
            UUID bodyBId,
            Vector2D localAnchorBOrWorld,
            double restLength) {

        this.cableId = Objects.requireNonNull(cableId, "cableId is required");
        if (cableId.isBlank()) {
            throw new IllegalArgumentException("cableId cannot be blank");
        }
        this.startAnchorRef = startAnchorRef;
        this.endAnchorRef = endAnchorRef;
        this.bodyAId = Objects.requireNonNull(bodyAId, "bodyAId is required (Slot A must be dynamic body)");
        this.localAnchorA = Objects.requireNonNull(localAnchorA, "localAnchorA is required");
        this.bodyBId = bodyBId;
        this.localAnchorBOrWorld = Objects.requireNonNull(localAnchorBOrWorld, "localAnchorBOrWorld is required");

        if (!Double.isFinite(restLength) || restLength <= 0.0) {
            throw new IllegalArgumentException("restLength must be finite and > 0: " + restLength);
        }
        this.restLength = restLength;
    }

    /**
     * Adapts an authoritative {@link CableTopology} from a {@link LaboratoryScene} into a runtime geometry evaluator.
     *
     * @param topology declarative cable topology blueprint
     * @param scene laboratory scene owning bodies and anchors
     * @param runtimeBodies active runtime rigid bodies in the simulation world
     * @return normalized DirectCableGeometry evaluator
     */
    public static DirectCableGeometry fromTopology(
            CableTopology topology,
            LaboratoryScene scene,
            Map<UUID, RigidBody> runtimeBodies) {

        Objects.requireNonNull(topology, "topology is required");
        Objects.requireNonNull(scene, "scene is required");
        Objects.requireNonNull(runtimeBodies, "runtimeBodies is required");

        if (topology.pulleyPath() != null && !topology.pulleyPath().isEmpty()) {
            throw new IllegalArgumentException("Direct cable " + topology.relationshipId()
                    + " cannot specify intermediate pulleys in pulleyPath: " + topology.pulleyPath());
        }

        Double specifiedLength = topology.specifiedLength();
        if (specifiedLength == null || !Double.isFinite(specifiedLength) || specifiedLength <= 0.0) {
            throw new IllegalArgumentException("Cable rest length must be finite and > 0: " + specifiedLength);
        }

        AnchorRef startRef = topology.startAnchor();
        AnchorRef endRef = topology.endAnchor();

        // Case 4: World Anchor ↔ World Anchor
        if (startRef instanceof AnchorRef.WorldAnchorRef && endRef instanceof AnchorRef.WorldAnchorRef) {
            throw new IllegalArgumentException("Cable " + topology.relationshipId()
                    + " connects two stationary anchors with zero dynamic degrees of freedom");
        }

        // Case 1: Body ↔ Body
        if (startRef instanceof AnchorRef.BodyAttachmentRef startBodyRef
                && endRef instanceof AnchorRef.BodyAttachmentRef endBodyRef) {

            RigidBody bodyA = runtimeBodies.get(startBodyRef.bodyId());
            if (bodyA == null) {
                throw new IllegalArgumentException("Body not found in runtime world: " + startBodyRef.bodyId());
            }
            RigidBody bodyB = runtimeBodies.get(endBodyRef.bodyId());
            if (bodyB == null) {
                throw new IllegalArgumentException("Body not found in runtime world: " + endBodyRef.bodyId());
            }

            SceneBody sceneBodyA = scene.findBody(startBodyRef.bodyId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneBody not found: " + startBodyRef.bodyId()));
            SceneBody sceneBodyB = scene.findBody(endBodyRef.bodyId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneBody not found: " + endBodyRef.bodyId()));

            AttachmentPoint ptA = sceneBodyA.attachmentPoints().get(startBodyRef.pointId());
            if (ptA == null) {
                throw new IllegalArgumentException("AttachmentPoint not found on body A: " + startBodyRef.pointId());
            }
            AttachmentPoint ptB = sceneBodyB.attachmentPoints().get(endBodyRef.pointId());
            if (ptB == null) {
                throw new IllegalArgumentException("AttachmentPoint not found on body B: " + endBodyRef.pointId());
            }

            // Both bodies static -> reject
            if (bodyA.isStatic() && bodyB.isStatic()) {
                throw new IllegalArgumentException("Cable " + topology.relationshipId()
                        + " connects two static bodies with zero dynamic degrees of freedom");
            }

            // Body A dynamic, Body B static -> normalize Body B into world anchor
            if (bodyA.isDynamic() && bodyB.isStatic()) {
                Vector2D worldPosB = bodyB.position().add(ptB.localPosition().rotate(bodyB.angle()));
                validateEndpoints(bodyA.position().add(ptA.localPosition().rotate(bodyA.angle())), worldPosB);
                return new DirectCableGeometry(
                        topology.relationshipId(), startRef, endRef,
                        bodyA.id(), ptA.localPosition(), null, worldPosB, specifiedLength);
            }

            // Body A static, Body B dynamic -> normalize Body B to Slot A, Body A to Slot B (World Anchor)
            if (bodyA.isStatic() && bodyB.isDynamic()) {
                Vector2D worldPosA = bodyA.position().add(ptA.localPosition().rotate(bodyA.angle()));
                validateEndpoints(bodyB.position().add(ptB.localPosition().rotate(bodyB.angle())), worldPosA);
                return new DirectCableGeometry(
                        topology.relationshipId(), startRef, endRef,
                        bodyB.id(), ptB.localPosition(), null, worldPosA, specifiedLength);
            }

            // Both dynamic
            Vector2D pA = bodyA.position().add(ptA.localPosition().rotate(bodyA.angle()));
            Vector2D pB = bodyB.position().add(ptB.localPosition().rotate(bodyB.angle()));
            validateEndpoints(pA, pB);
            return new DirectCableGeometry(
                    topology.relationshipId(), startRef, endRef,
                    bodyA.id(), ptA.localPosition(), bodyB.id(), ptB.localPosition(), specifiedLength);
        }

        // Case 2: Body ↔ World Anchor (Forward)
        if (startRef instanceof AnchorRef.BodyAttachmentRef bodyRef
                && endRef instanceof AnchorRef.WorldAnchorRef worldRef) {

            RigidBody body = runtimeBodies.get(bodyRef.bodyId());
            if (body == null) {
                throw new IllegalArgumentException("Body not found in runtime world: " + bodyRef.bodyId());
            }
            if (body.isStatic()) {
                throw new IllegalArgumentException("Cable " + topology.relationshipId()
                        + " connects static body to world anchor with zero dynamic degrees of freedom");
            }

            SceneBody sceneBody = scene.findBody(bodyRef.bodyId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneBody not found: " + bodyRef.bodyId()));
            AttachmentPoint pt = sceneBody.attachmentPoints().get(bodyRef.pointId());
            if (pt == null) {
                throw new IllegalArgumentException("AttachmentPoint not found on body: " + bodyRef.pointId());
            }

            SceneAnchor anchor = scene.findAnchor(worldRef.anchorId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneAnchor not found: " + worldRef.anchorId()));

            Vector2D pA = body.position().add(pt.localPosition().rotate(body.angle()));
            Vector2D pB = anchor.worldPosition();
            validateEndpoints(pA, pB);

            return new DirectCableGeometry(
                    topology.relationshipId(), startRef, endRef,
                    body.id(), pt.localPosition(), null, pB, specifiedLength);
        }

        // Case 3: World Anchor ↔ Body (Reversed) -> Normalized by swapping slots
        if (startRef instanceof AnchorRef.WorldAnchorRef worldRef
                && endRef instanceof AnchorRef.BodyAttachmentRef bodyRef) {

            RigidBody body = runtimeBodies.get(bodyRef.bodyId());
            if (body == null) {
                throw new IllegalArgumentException("Body not found in runtime world: " + bodyRef.bodyId());
            }
            if (body.isStatic()) {
                throw new IllegalArgumentException("Cable " + topology.relationshipId()
                        + " connects static body to world anchor with zero dynamic degrees of freedom");
            }

            SceneBody sceneBody = scene.findBody(bodyRef.bodyId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneBody not found: " + bodyRef.bodyId()));
            AttachmentPoint pt = sceneBody.attachmentPoints().get(bodyRef.pointId());
            if (pt == null) {
                throw new IllegalArgumentException("AttachmentPoint not found on body: " + bodyRef.pointId());
            }

            SceneAnchor anchor = scene.findAnchor(worldRef.anchorId())
                    .orElseThrow(() -> new IllegalArgumentException("SceneAnchor not found: " + worldRef.anchorId()));

            Vector2D pBody = body.position().add(pt.localPosition().rotate(body.angle()));
            Vector2D pAnchor = anchor.worldPosition();
            validateEndpoints(pBody, pAnchor);

            // Normalized assignment: Slot A is the dynamic body, Slot B is the fixed world anchor
            return new DirectCableGeometry(
                    topology.relationshipId(), startRef, endRef,
                    body.id(), pt.localPosition(), null, pAnchor, specifiedLength);
        }

        throw new IllegalArgumentException("Unsupported anchor reference configuration: "
                + startRef.getClass().getSimpleName() + " to " + endRef.getClass().getSimpleName());
    }

    /**
     * Programmatic factory for a direct cable between two rigid bodies.
     */
    public static DirectCableGeometry betweenBodies(
            String cableId,
            RigidBody bodyA,
            Vector2D localAnchorA,
            RigidBody bodyB,
            Vector2D localAnchorB,
            double restLength) {

        Objects.requireNonNull(bodyA, "bodyA is required");
        Objects.requireNonNull(bodyB, "bodyB is required");
        Objects.requireNonNull(localAnchorA, "localAnchorA is required");
        Objects.requireNonNull(localAnchorB, "localAnchorB is required");

        if (bodyA.isStatic() && bodyB.isStatic()) {
            throw new IllegalArgumentException("Cable " + cableId
                    + " connects two static bodies with zero dynamic degrees of freedom");
        }

        // Body A dynamic, Body B static -> normalize Body B into world anchor
        if (bodyA.isDynamic() && bodyB.isStatic()) {
            Vector2D worldPosB = bodyB.position().add(localAnchorB.rotate(bodyB.angle()));
            Vector2D worldPosA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
            validateEndpoints(worldPosA, worldPosB);
            return new DirectCableGeometry(
                    cableId, null, null,
                    bodyA.id(), localAnchorA, null, worldPosB, restLength);
        }

        // Body A static, Body B dynamic -> normalize Body B to Slot A, Body A to Slot B (World Anchor)
        if (bodyA.isStatic() && bodyB.isDynamic()) {
            Vector2D worldPosA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
            Vector2D worldPosB = bodyB.position().add(localAnchorB.rotate(bodyB.angle()));
            validateEndpoints(worldPosB, worldPosA);
            return new DirectCableGeometry(
                    cableId, null, null,
                    bodyB.id(), localAnchorB, null, worldPosA, restLength);
        }

        // Both dynamic
        Vector2D pA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
        Vector2D pB = bodyB.position().add(localAnchorB.rotate(bodyB.angle()));
        validateEndpoints(pA, pB);
        return new DirectCableGeometry(
                cableId, null, null,
                bodyA.id(), localAnchorA, bodyB.id(), localAnchorB, restLength);
    }

    /**
     * Programmatic factory for a direct cable between a rigid body and a fixed world anchor.
     */
    public static DirectCableGeometry toAnchor(
            String cableId,
            RigidBody bodyA,
            Vector2D localAnchorA,
            Vector2D worldAnchor,
            double restLength) {

        Objects.requireNonNull(bodyA, "bodyA is required");
        Objects.requireNonNull(localAnchorA, "localAnchorA is required");
        Objects.requireNonNull(worldAnchor, "worldAnchor is required");

        if (bodyA.isStatic()) {
            throw new IllegalArgumentException("Cable " + cableId
                    + " connects static body to world anchor with zero dynamic degrees of freedom");
        }

        Vector2D pA = bodyA.position().add(localAnchorA.rotate(bodyA.angle()));
        validateEndpoints(pA, worldAnchor);

        return new DirectCableGeometry(
                cableId, null, null,
                bodyA.id(), localAnchorA, null, worldAnchor, restLength);
    }

    private static void validateEndpoints(Vector2D pA, Vector2D pB) {
        double dist = pA.distanceTo(pB);
        if (dist < MIN_INITIAL_SEPARATION) {
            throw new IllegalArgumentException("Cable endpoints cannot coincide (distance = " + dist + " < "
                    + MIN_INITIAL_SEPARATION + " m); cable direction is undefined");
        }
    }

    /**
     * Evaluates the complete instantaneous kinematics of the cable.
     *
     * @param bodies map of active bodies in the simulation world
     * @return instantaneous CableKinematics snapshot
     */
    public CableKinematics evaluateKinematics(Map<UUID, RigidBody> bodies) {
        Objects.requireNonNull(bodies, "bodies map is required");

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

        Vector2D aCmA = bodyA.state().acceleration();
        double alphaA = bodyA.state().angularAcceleration();
        Vector2D aPtA = aCmA.add(new Vector2D(-alphaA * rA.y(), alphaA * rA.x())).subtract(rA.multiply(omegaA * omegaA));

        Vector2D pB;
        Vector2D vPtB;
        Vector2D aPtB;
        Vector2D rB = Vector2D.ZERO;
        double omegaB = 0.0;

        if (bodyBId != null) {
            RigidBody bodyB = bodies.get(bodyBId);
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

            Vector2D aCmB = bodyB.state().acceleration();
            double alphaB = bodyB.state().angularAcceleration();
            aPtB = aCmB.add(new Vector2D(-alphaB * rB.y(), alphaB * rB.x())).subtract(rB.multiply(omegaB * omegaB));
        } else {
            pB = localAnchorBOrWorld;
            vPtB = Vector2D.ZERO;
            aPtB = Vector2D.ZERO;
        }

        Vector2D deltaP = pA.subtract(pB);
        double currentLength = deltaP.magnitude();
        if (currentLength < MIN_RUNTIME_SEPARATION) {
            throw new IllegalStateException("Direct cable " + cableId + " collapsed (length = " + currentLength
                    + " < " + MIN_RUNTIME_SEPARATION + " m); cable direction is singular");
        }

        double ux = deltaP.x() / currentLength;
        if (ux == 0.0) ux = 0.0;
        double uy = deltaP.y() / currentLength;
        if (uy == 0.0) uy = 0.0;
        Vector2D u = new Vector2D(ux, uy);
        Vector2D vRel = vPtA.subtract(vPtB);
        Vector2D aRel = aPtA.subtract(aPtB);

        double gap = restLength - currentLength;
        double gapRate = -u.dot(vRel);
        if (gapRate == 0.0) gapRate = 0.0;

        // Velocity decomposition: v_rel = (u · v_rel) u + v_perp
        double vParallel = u.dot(vRel);
        Vector2D vPerp = vRel.subtract(u.multiply(vParallel));
        double vPerpSquared = vPerp.magnitudeSquared();

        // Convective acceleration: gamma_conv = J_dot · v
        // gamma_conv = -||v_perp||^2 / L + omegaA^2 (rA · u) - omegaB^2 (rB · u)
        double convective = -vPerpSquared / currentLength;
        convective += (omegaA * omegaA) * rA.dot(u);
        if (bodyBId != null) {
            convective -= (omegaB * omegaB) * rB.dot(u);
        }

        return new CableKinematics(
                pA, pB,
                vPtA, vPtB,
                aPtA, aPtB,
                deltaP, vRel, aRel,
                currentLength, u,
                restLength, gap, gapRate, convective);
    }

    /**
     * Populates a linearized constraint row with J = \nabla g = -\nabla L and convective acceleration.
     *
     * <p>Jacobian Convention:
     * <ul>
     *   <li>Body A: J_x = -u_x, J_y = -u_y, J_theta = -(r_A × u)_z</li>
     *   <li>Body B: J_x = +u_x, J_y = +u_y, J_theta = +(r_B × u)_z</li>
     * </ul>
     *
     * @param mapping coordinate mapping of dynamic degrees of freedom
     * @param bodies active bodies map
     * @param row target ConstraintRow
     */
    public void populateJacobian(
            CoordinateMapping mapping,
            Map<UUID, RigidBody> bodies,
            ConstraintRow row) {

        Objects.requireNonNull(mapping, "mapping is required");
        Objects.requireNonNull(bodies, "bodies is required");
        Objects.requireNonNull(row, "row is required");

        CableKinematics kin = evaluateKinematics(bodies);
        Vector2D u = kin.unitDirection();

        RigidBody bodyA = bodies.get(bodyAId);
        Vector2D rA = localAnchorA.rotate(bodyA.angle());

        if (mapping.isDynamic(bodyAId)) {
            int idxA = mapping.startIndex(bodyAId);
            // J = -u
            double jax = -u.x();
            if (jax == 0.0) jax = 0.0;
            double jay = -u.y();
            if (jay == 0.0) jay = 0.0;
            row.setJacobian(idxA, jax);
            row.setJacobian(idxA + 1, jay);

            if (mapping.hasRotation(bodyAId)) {
                // -(rA × u)_z = -(rAx * uy - rAy * ux)
                double torqueArmA = -rA.cross(u);
                if (torqueArmA == 0.0) torqueArmA = 0.0;
                row.setJacobian(idxA + 2, torqueArmA);
            }
        }

        if (bodyBId != null && mapping.isDynamic(bodyBId)) {
            RigidBody bodyB = bodies.get(bodyBId);
            Vector2D rB = localAnchorBOrWorld.rotate(bodyB.angle());
            int idxB = mapping.startIndex(bodyBId);

            // J = +u
            double jbx = u.x();
            if (jbx == 0.0) jbx = 0.0;
            double jby = u.y();
            if (jby == 0.0) jby = 0.0;
            row.setJacobian(idxB, jbx);
            row.setJacobian(idxB + 1, jby);

            if (mapping.hasRotation(bodyBId)) {
                // +(rB × u)_z = +(rBx * uy - rBy * ux)
                double torqueArmB = rB.cross(u);
                if (torqueArmB == 0.0) torqueArmB = 0.0;
                row.setJacobian(idxB + 2, torqueArmB);
            }
        }

        row.setPositionError(kin.gap());
        row.setVelocityError(kin.gapRate());
        row.setCentripetalAccel(kin.convectiveAcceleration());
    }

    public String cableId() {
        return cableId;
    }

    public Optional<AnchorRef> startAnchorRef() {
        return Optional.ofNullable(startAnchorRef);
    }

    public Optional<AnchorRef> endAnchorRef() {
        return Optional.ofNullable(endAnchorRef);
    }

    public UUID bodyAId() {
        return bodyAId;
    }

    public Vector2D localAnchorA() {
        return localAnchorA;
    }

    public UUID bodyBId() {
        return bodyBId;
    }

    public Vector2D localAnchorBOrWorld() {
        return localAnchorBOrWorld;
    }

    public boolean isLinkedToWorld() {
        return bodyBId == null;
    }

    public double restLength() {
        return restLength;
    }
}
