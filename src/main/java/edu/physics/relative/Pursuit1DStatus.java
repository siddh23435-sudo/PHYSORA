package edu.physics.relative;

/**
 * Physical outcome and collision classification for 1D relative pursuit and overtaking.
 */
public enum Pursuit1DStatus {

    /**
     * Bodies are initially co-located at t = 0 (x0 = 0), regardless of whether subsequent future roots exist.
     */
    INITIAL_COLLISION_CO_LOCATED,

    /**
     * Bodies are initially co-located and remain permanently co-located (x0 = 0, v_rel0 = 0, a_rel = 0).
     */
    PERMANENT_CO_LOCATION,

    /**
     * Bodies maintain a constant, non-zero separation (x0 != 0, v_rel0 = 0, a_rel = 0).
     */
    CONSTANT_NONZERO_SEPARATION,

    /**
     * Relative acceleration is zero; linear collision occurs at t = -x0 / v_rel0 inside the active interval.
     */
    LINEAR_COLLISION,

    /**
     * Accelerated pursuit with two real roots; first physical collision occurs at the earliest positive root.
     */
    COLLISION_FIRST_PASS,

    /**
     * Accelerated pursuit where the earlier root was non-positive; collision occurs at the second positive root.
     */
    COLLISION_OVERTAKE,

    /**
     * Repeated root (Delta = 0); bodies make glancing contact with matched velocities (v_rel = 0).
     */
    GLANCING_CONTACT,

    /**
     * No real collision roots (Delta < 0); separation closes to a positive turning-point minimum inside active interval.
     */
    NO_COLLISION_MINIMUM_SEPARATION,

    /**
     * Separation strictly increases forward in time; minimum separation occurs at t = 0.
     */
    NO_COLLISION_DIVERGING,

    /**
     * Physical collision would occur in future, but active interval expires before the collision point is reached.
     */
    NO_COLLISION_INTERVAL_EXPIRED
}

