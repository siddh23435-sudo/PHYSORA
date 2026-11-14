package edu.physics.simulation;

/**
 * Functional interface for force fields or environmental interactions applying forces/torques to bodies.
 */
@FunctionalInterface
public interface ForceGenerator {
    /**
     * Applies forces or torques to the specified body at the current simulation time.
     *
     * @param body target body
     * @param simulationTime elapsed simulation time in seconds
     */
    void apply(RigidBody body, double simulationTime);
}

