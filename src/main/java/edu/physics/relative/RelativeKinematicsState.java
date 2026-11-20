package edu.physics.relative;

/**
 * Classifies the relative kinematics behavior of a multi-body system.
 *
 * <p>Indicates whether the bodies are stationary relative to each other (parallel motion
 * with zero relative velocity) or have changing relative separation.</p>
 */
public enum RelativeKinematicsState {

    /**
     * Relative velocity vanishes (|v_rel| <= tolerance).
     * The separation between the bodies remains constant over time.
     */
    STATIONARY_SEPARATION,

    /**
     * Non-zero relative velocity (|v_rel| > tolerance).
     * The separation between the bodies changes linearly along the relative line of motion.
     */
    UNIFORM_RELATIVE_VELOCITY
}

