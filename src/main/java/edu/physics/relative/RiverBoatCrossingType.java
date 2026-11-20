package edu.physics.relative;

/**
 * Physical outcome classification for river-boat crossing problems.
 */
public enum RiverBoatCrossingType {

    /**
     * Heading directly perpendicular to river current (theta = 0).
     * Minimizes crossing time: t_min = width / v_boat, with downstream drift x = v_river * t_min.
     */
    SHORTEST_TIME,

    /**
     * Boat speed exceeds river current (v_boat > v_river).
     * Zero downstream drift is achievable with upstream heading sin(theta) = v_river / v_boat.
     */
    ZERO_DRIFT_ACHIEVABLE,

    /**
     * Boat speed equals river current (v_boat == v_river).
     * Heading directly upstream (theta = 90 deg) achieves stationary hold (v_net = 0);
     * zero drift is approached only asymptotically as crossing time diverges to infinity (t -> inf).
     * Finite crossing with zero drift is physically impossible.
     */
    ZERO_DRIFT_ONLY_ASYMPTOTIC,

    /**
     * Boat speed is less than river current (v_boat < v_river).
     * Zero drift is physically impossible; optimal heading sin(theta) = v_boat / v_river minimizes drift.
     */
    ZERO_DRIFT_UNACHIEVABLE,

    /**
     * General crossing under an arbitrary user-specified steering angle theta.
     */
    GENERAL_CROSSING
}

