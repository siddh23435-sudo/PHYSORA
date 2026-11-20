package edu.physics.relative;

/**
 * Classifies physical contact and spatial proximity between two bodies.
 *
 * <p>Evaluated strictly at the interval-clamped closest approach point
 * t* = clamp(t_min, 0, T) during simultaneous active flight [0, T].</p>
 */
public enum ContactClassification {

    /**
     * Mathematical center-to-center intersection (d_min <= analytical position tolerance).
     */
    EXACT_CONTACT,

    /**
     * Physical surface contact occurs during active flight
     * (position tolerance < d_min <= contact radius).
     */
    TOLERANCE_CONTACT,

    /**
     * Bodies pass closely during active flight without touching
     * (contact radius < d_min <= proximity threshold).
     */
    NEAR_MISS,

    /**
     * Bodies remain separated beyond the proximity threshold throughout flight
     * (d_min > proximity threshold).
     */
    NO_CONTACT
}

