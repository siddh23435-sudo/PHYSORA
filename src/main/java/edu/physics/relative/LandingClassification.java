package edu.physics.relative;

/**
 * Geometric and physical classification of trajectory intersections with the landing surface plane.
 */
public enum LandingClassification {

    /**
     * Trajectory intersects surface with downward vertical velocity (v_y < 0).
     * Standard projectile ground impact.
     */
    DOWNWARD_CROSSING,

    /**
     * Trajectory apex touches surface plane tangentially with zero vertical velocity (v_y = 0).
     * The projectile grazes the surface without penetrating or crossing it.
     */
    TANGENTIAL_CONTACT,

    /**
     * Trajectory intersects surface from below with upward vertical velocity (v_y > 0).
     * Occurs during sub-surface launches that emerge into the atmosphere.
     */
    UPWARD_CROSSING,

    /**
     * Sub-surface launch whose apex lies strictly below the surface plane (y_max < y_surface).
     * Turns around without reaching the surface plane.
     */
    NO_LANDING_APEX_BELOW,

    /**
     * Sub-surface or ground launch directed downward away from the surface plane.
     */
    NO_LANDING_RECEDING,

    /**
     * Trajectory is parallel to surface plane (e.g. zero gravity with v_y = 0).
     */
    NO_LANDING_PARALLEL,

    /**
     * Rejected due to invalid gravitational acceleration (g < 0).
     */
    INVALID_GRAVITY
}

