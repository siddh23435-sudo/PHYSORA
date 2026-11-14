package edu.physics.simulation.constraint;

import edu.physics.simulation.RigidBody;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Common contract for mechanical constraints acting on rigid bodies and point masses.
 *
 * <p>Enforces kinematic relationships by evaluating constraint Jacobians J, position errors Φ(q),
 * centripetal convective accelerations J̇ v, and velocity violations J v.
 */
public interface MechanicalConstraint {

    /**
     * Unique identifier for this constraint.
     */
    String constraintId();

    /**
     * Number of scalar rows (degrees of freedom removed) by this constraint.
     */
    int dimension();

    /**
     * Evaluates constraint Jacobian rows, position error, centripetal acceleration, and velocity error.
     *
     * @param mapping coordinate mapping of dynamic degrees of freedom
     * @param bodies map of all bodies in the world
     * @param rows output list where new ConstraintRow instances should be appended
     * @param dt current simulation timestep in seconds
     */
    void evaluate(CoordinateMapping mapping, Map<UUID, RigidBody> bodies, List<ConstraintRow> rows, double dt);

    /**
     * Evaluates instantaneous position errors Φ(q) for Newton-Raphson position projection.
     *
     * @param bodies map of all bodies in the world
     * @param errors output array of errors
     * @param offset starting index in the errors array
     */
    void evaluatePositionErrors(Map<UUID, RigidBody> bodies, double[] errors, int offset);

    /**
     * Evaluates instantaneous constraint velocity errors J v for velocity projection.
     *
     * @param mapping coordinate mapping
     * @param bodies map of all bodies in the world
     * @param errors output array of velocity errors
     * @param offset starting index in the errors array
     */
    void evaluateVelocityErrors(CoordinateMapping mapping, Map<UUID, RigidBody> bodies, double[] errors, int offset);
}

