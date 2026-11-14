package edu.physics.simulation;

import edu.physics.simulation.constraint.ConstraintRow;
import edu.physics.simulation.constraint.CoordinateMapping;
import edu.physics.simulation.constraint.DirectLinearConstraintSolver;
import edu.physics.simulation.constraint.MechanicalConstraint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Velocity Verlet (Symplectic Störmer-Verlet) numerical integrator for rigid-body planar dynamics.
 *
 * <p>Velocity Verlet is symplectic, time-reversible, and second-order accurate:
 * <ul>
 *   <li>Half-step velocity: v(t + ½Δt) = v(t) + ½ a(t) Δt</li>
 *   <li>Full-step position: x(t + Δt) = x(t) + v(t + ½Δt) Δt</li>
 *   <li>Force evaluation: Compute a(t + Δt) from forces at new position x(t + Δt) and time t + Δt</li>
 *   <li>Final half-step velocity: v(t + Δt) = v(t + ½Δt) + ½ a(t + Δt) Δt</li>
 * </ul>
 *
 * <p>When bilateral mechanical constraints are active, this integrator executes a 12-step Velocity-Impulse
 * Verlet cycle with normalized Delassus constraint acceleration solving, Newton-Raphson position error projection,
 * and linear velocity GGL projection.
 */
public final class SymplecticIntegrator {

    public static final double DEFAULT_BAUMGARTE_ALPHA = 5.0; // velocity stabilization frequency (s⁻¹)
    public static final double DEFAULT_BAUMGARTE_BETA = 5.0;  // position stabilization frequency (s⁻¹)
    public static final int DEFAULT_MAX_PROJECTION_ITERATIONS = 5;
    public static final double DEFAULT_POSITION_TOLERANCE = 5.0e-7; // 0.5 micrometres
    public static final double DEFAULT_VELOCITY_TOLERANCE = 1.0e-5; // 10 micrometres / second

    private final DirectLinearConstraintSolver constraintSolver = new DirectLinearConstraintSolver();

    public SymplecticIntegrator() {
    }

    /**
     * Advances all bodies in the world by the fixed time step dt.
     *
     * @param bodies list of rigid bodies in the world
     * @param forceGenerators active force fields acting on bodies
     * @param simulationTime current simulation time in seconds
     * @param dt fixed time step in seconds (must be finite and > 0)
     */
    public void step(List<RigidBody> bodies, List<ForceGenerator> forceGenerators, double simulationTime, double dt) {
        Objects.requireNonNull(bodies, "Bodies list is required");
        Objects.requireNonNull(forceGenerators, "Force generators list is required");
        if (!Double.isFinite(dt) || dt <= 0.0) {
            throw new IllegalArgumentException("Integration timestep dt must be finite and > 0: " + dt);
        }

        // 1. Evaluate forces and accelerations a(t) at beginning of time step
        evaluateForces(bodies, forceGenerators, simulationTime);

        // 2. Step A: Update velocities by half-step and positions/angles by full-step
        for (RigidBody body : bodies) {
            if (body.isStatic()) {
                continue;
            }
            RigidBodyState state = body.state();
            Vector2D a0 = state.acceleration();
            double alpha0 = state.angularAcceleration();

            // v(t + 1/2 dt) = v(t) + 1/2 a(t) dt
            Vector2D halfVelocity = state.velocity().add(a0.multiply(0.5 * dt));
            double halfAngularVelocity = state.angularVelocity() + 0.5 * alpha0 * dt;

            // x(t + dt) = x(t) + v(t + 1/2 dt) dt
            Vector2D newPosition = state.position().add(halfVelocity.multiply(dt));
            double newAngle = state.angle() + halfAngularVelocity * dt;

            state.setVelocity(halfVelocity);
            state.setAngularVelocity(halfAngularVelocity);
            state.setPosition(newPosition);
            state.setAngle(newAngle);
        }

        // 3. Step B: Evaluate forces and accelerations a(t + dt) at the new positions
        double nextTime = simulationTime + dt;
        evaluateForces(bodies, forceGenerators, nextTime);

        // 4. Step C: Complete the velocity update with the second half-step
        for (RigidBody body : bodies) {
            if (body.isStatic()) {
                continue;
            }
            RigidBodyState state = body.state();
            Vector2D a1 = state.acceleration();
            double alpha1 = state.angularAcceleration();

            // v(t + dt) = v(t + 1/2 dt) + 1/2 a(t + dt) dt
            Vector2D finalVelocity = state.velocity().add(a1.multiply(0.5 * dt));
            double finalAngularVelocity = state.angularVelocity() + 0.5 * alpha1 * dt;

            state.setVelocity(finalVelocity);
            state.setAngularVelocity(finalAngularVelocity);
        }
    }

    /**
     * Advances all bodies in the world by the fixed time step dt in the presence of bilateral mechanical constraints.
     *
     * @param bodies list of rigid bodies in the world
     * @param forceGenerators active force fields acting on bodies
     * @param constraints active mechanical constraints
     * @param simulationTime current simulation time in seconds
     * @param dt fixed time step in seconds (must be finite and > 0)
     */
    public void step(
            List<RigidBody> bodies,
            List<ForceGenerator> forceGenerators,
            List<MechanicalConstraint> constraints,
            double simulationTime,
            double dt) {

        Objects.requireNonNull(bodies, "Bodies list is required");
        Objects.requireNonNull(forceGenerators, "Force generators list is required");
        if (constraints == null || constraints.isEmpty()) {
            step(bodies, forceGenerators, simulationTime, dt);
            return;
        }
        if (!Double.isFinite(dt) || dt <= 0.0) {
            throw new IllegalArgumentException("Integration timestep dt must be finite and > 0: " + dt);
        }

        stepConstrained(bodies, forceGenerators, constraints, simulationTime, dt);
    }

    private void stepConstrained(
            List<RigidBody> bodies,
            List<ForceGenerator> forceGenerators,
            List<MechanicalConstraint> constraints,
            double simulationTime,
            double dt) {

        // 1. Evaluate external forces and accelerations a_ext(t) at beginning of time step
        evaluateForces(bodies, forceGenerators, simulationTime);

        CoordinateMapping mapping = CoordinateMapping.of(bodies);
        int d = mapping.totalDof();
        if (d == 0) {
            return;
        }

        Map<UUID, RigidBody> bodyMap = new LinkedHashMap<>();
        for (RigidBody b : bodies) {
            bodyMap.put(b.id(), b);
        }

        double[] aExt0 = new double[d];
        for (RigidBody b : mapping.dynamicBodies()) {
            int idx = mapping.startIndex(b.id());
            aExt0[idx] = b.state().acceleration().x();
            aExt0[idx + 1] = b.state().acceleration().y();
            if (mapping.hasRotation(b.id())) {
                aExt0[idx + 2] = b.state().angularAcceleration();
            }
        }

        // 2. Evaluate constraint rows at t (q(t), v(t))
        List<ConstraintRow> rows0 = new ArrayList<>();
        for (MechanicalConstraint c : constraints) {
            c.evaluate(mapping, bodyMap, rows0, dt);
        }

        // 3. Solve constraint accelerations a_c(t)
        DirectLinearConstraintSolver.Solution sol0 =
                constraintSolver.solve(mapping, rows0, aExt0, DEFAULT_BAUMGARTE_ALPHA, DEFAULT_BAUMGARTE_BETA);
        double[] aConstr0 = sol0.constraintAccelerations();

        // 4. Step A: Update velocities by half-step and positions/angles by full-step
        for (RigidBody b : mapping.dynamicBodies()) {
            int idx = mapping.startIndex(b.id());
            double netAx = aExt0[idx] + aConstr0[idx];
            double netAy = aExt0[idx + 1] + aConstr0[idx + 1];

            RigidBodyState state = b.state();
            Vector2D halfV = state.velocity().add(new Vector2D(netAx, netAy).multiply(0.5 * dt));
            Vector2D newPos = state.position().add(halfV.multiply(dt));
            state.setVelocity(halfV);
            state.setPosition(newPos);

            if (mapping.hasRotation(b.id())) {
                double netAlpha = aExt0[idx + 2] + aConstr0[idx + 2];
                double halfOmega = state.angularVelocity() + 0.5 * netAlpha * dt;
                double newAngle = state.angle() + halfOmega * dt;
                state.setAngularVelocity(halfOmega);
                state.setAngle(newAngle);
            }
        }

        // 5. Newton-Raphson position projection at t + dt
        projectPositions(mapping, bodyMap, constraints, dt);

        // 6. Step B: Evaluate external forces at the new positions at t + dt
        double nextTime = simulationTime + dt;
        evaluateForces(bodies, forceGenerators, nextTime);

        double[] aExt1 = new double[d];
        for (RigidBody b : mapping.dynamicBodies()) {
            int idx = mapping.startIndex(b.id());
            aExt1[idx] = b.state().acceleration().x();
            aExt1[idx + 1] = b.state().acceleration().y();
            if (mapping.hasRotation(b.id())) {
                aExt1[idx + 2] = b.state().angularAcceleration();
            }
        }

        // 7. Evaluate constraint rows at t + dt with current half-step velocities
        List<ConstraintRow> rows1 = new ArrayList<>();
        for (MechanicalConstraint c : constraints) {
            c.evaluate(mapping, bodyMap, rows1, dt);
        }

        // 8. Solve constraint accelerations a_c(t + dt)
        DirectLinearConstraintSolver.Solution sol1 =
                constraintSolver.solve(mapping, rows1, aExt1, DEFAULT_BAUMGARTE_ALPHA, DEFAULT_BAUMGARTE_BETA);
        double[] aConstr1 = sol1.constraintAccelerations();

        // 9. Step C: Complete velocity update with second half-step
        for (RigidBody b : mapping.dynamicBodies()) {
            int idx = mapping.startIndex(b.id());
            double netAx = aExt1[idx] + aConstr1[idx];
            double netAy = aExt1[idx + 1] + aConstr1[idx + 1];

            RigidBodyState state = b.state();
            Vector2D finalV = state.velocity().add(new Vector2D(netAx, netAy).multiply(0.5 * dt));
            state.setVelocity(finalV);
            state.setAcceleration(new Vector2D(netAx, netAy));

            if (mapping.hasRotation(b.id())) {
                double netAlpha = aExt1[idx + 2] + aConstr1[idx + 2];
                double finalOmega = state.angularVelocity() + 0.5 * netAlpha * dt;
                state.setAngularVelocity(finalOmega);
                state.setAngularAcceleration(netAlpha);
            }
        }

        // 10. Linear velocity projection (GGL projection)
        projectVelocities(mapping, bodyMap, constraints, dt);
    }

    private void projectPositions(
            CoordinateMapping mapping,
            Map<UUID, RigidBody> bodyMap,
            List<MechanicalConstraint> constraints,
            double dt) {

        int totalDim = 0;
        for (MechanicalConstraint c : constraints) {
            totalDim += c.dimension();
        }
        if (totalDim == 0) {
            return;
        }

        double[] posErrors = new double[totalDim];
        for (int iter = 0; iter < DEFAULT_MAX_PROJECTION_ITERATIONS; iter++) {
            int offset = 0;
            double maxErr = 0.0;
            for (MechanicalConstraint c : constraints) {
                c.evaluatePositionErrors(bodyMap, posErrors, offset);
                for (int i = 0; i < c.dimension(); i++) {
                    maxErr = Math.max(maxErr, Math.abs(posErrors[offset + i]));
                }
                offset += c.dimension();
            }

            if (maxErr <= DEFAULT_POSITION_TOLERANCE) {
                return;
            }

            List<ConstraintRow> projRows = new ArrayList<>();
            for (MechanicalConstraint c : constraints) {
                c.evaluate(mapping, bodyMap, projRows, dt);
            }

            double[] deltaQ = constraintSolver.solvePositionCorrection(mapping, projRows, posErrors);
            for (RigidBody b : mapping.dynamicBodies()) {
                int idx = mapping.startIndex(b.id());
                Vector2D p = b.position().add(new Vector2D(deltaQ[idx], deltaQ[idx + 1]));
                b.state().setPosition(p);
                if (mapping.hasRotation(b.id())) {
                    b.state().setAngle(b.angle() + deltaQ[idx + 2]);
                }
            }
        }

        // Post-loop verification: evaluate final position errors
        int offset = 0;
        double finalMaxErr = 0.0;
        for (MechanicalConstraint c : constraints) {
            c.evaluatePositionErrors(bodyMap, posErrors, offset);
            for (int i = 0; i < c.dimension(); i++) {
                finalMaxErr = Math.max(finalMaxErr, Math.abs(posErrors[offset + i]));
            }
            offset += c.dimension();
        }

        if (finalMaxErr > DEFAULT_POSITION_TOLERANCE) {
            throw new IllegalStateException("Constraint position projection failed to converge after "
                    + DEFAULT_MAX_PROJECTION_ITERATIONS + " iterations; final residual: " + finalMaxErr
                    + " m exceeds tolerance " + DEFAULT_POSITION_TOLERANCE + " m");
        }
    }

    private void projectVelocities(
            CoordinateMapping mapping,
            Map<UUID, RigidBody> bodyMap,
            List<MechanicalConstraint> constraints,
            double dt) {

        int totalDim = 0;
        for (MechanicalConstraint c : constraints) {
            totalDim += c.dimension();
        }
        if (totalDim == 0) {
            return;
        }

        double[] velErrors = new double[totalDim];
        int offset = 0;
        double maxErr = 0.0;
        for (MechanicalConstraint c : constraints) {
            c.evaluateVelocityErrors(mapping, bodyMap, velErrors, offset);
            for (int i = 0; i < c.dimension(); i++) {
                maxErr = Math.max(maxErr, Math.abs(velErrors[offset + i]));
            }
            offset += c.dimension();
        }

        if (maxErr <= DEFAULT_VELOCITY_TOLERANCE) {
            return;
        }

        List<ConstraintRow> projRows = new ArrayList<>();
        for (MechanicalConstraint c : constraints) {
            c.evaluate(mapping, bodyMap, projRows, dt);
        }

        double[] deltaV = constraintSolver.solveVelocityCorrection(mapping, projRows, velErrors);
        for (RigidBody b : mapping.dynamicBodies()) {
            int idx = mapping.startIndex(b.id());
            Vector2D v = b.velocity().add(new Vector2D(deltaV[idx], deltaV[idx + 1]));
            b.state().setVelocity(v);
            if (mapping.hasRotation(b.id())) {
                b.state().setAngularVelocity(b.angularVelocity() + deltaV[idx + 2]);
            }
        }

        // Post-projection verification: evaluate final velocity errors
        offset = 0;
        double finalMaxVelErr = 0.0;
        for (MechanicalConstraint c : constraints) {
            c.evaluateVelocityErrors(mapping, bodyMap, velErrors, offset);
            for (int i = 0; i < c.dimension(); i++) {
                finalMaxVelErr = Math.max(finalMaxVelErr, Math.abs(velErrors[offset + i]));
            }
            offset += c.dimension();
        }

        if (finalMaxVelErr > DEFAULT_VELOCITY_TOLERANCE) {
            throw new IllegalStateException("Constraint velocity projection failed to satisfy tolerance; final residual: "
                    + finalMaxVelErr + " m/s exceeds tolerance " + DEFAULT_VELOCITY_TOLERANCE + " m/s");
        }
    }

    /**
     * Evaluates all external and environmental forces and sets instantaneous accelerations.
     */
    public void evaluateForces(List<RigidBody> bodies, List<ForceGenerator> forceGenerators, double time) {
        for (RigidBody body : bodies) {
            body.clearForces();
            if (body.isStatic()) {
                body.state().setAcceleration(Vector2D.ZERO);
                body.state().setAngularAcceleration(0.0);
                continue;
            }
            for (ForceGenerator generator : forceGenerators) {
                generator.apply(body, time);
            }
            RigidBodyState state = body.state();
            Vector2D a = state.netForce().multiply(body.inverseMass());
            double alpha = body.inverseInertia() > 0.0 ? state.netTorque() * body.inverseInertia() : 0.0;
            state.setAcceleration(a);
            state.setAngularAcceleration(alpha);
        }
    }
}

