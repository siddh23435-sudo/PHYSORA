package edu.physics.simulation.constraint;

/**
 * Physical regime of a unilateral inextensible cable constraint.
 */
public enum CableState {
    /**
     * Positive geometric slack (L < L_0 - eps_pos). Endpoints are strictly in the interior.
     * The cable is completely inactive (lambda = 0).
     */
    SLACK_INTERIOR,

    /**
     * Near boundary (L >= L_0 - eps_pos), but endpoints are actively separating (g_dot > eps_vel).
     * The cable is moving into slack and cannot exert tension (lambda = 0).
     */
    SLACK_SEPARATING,

    /**
     * At boundary (L >= L_0 - eps_pos, g_dot <= eps_vel), and system acceleration naturally
     * maintains or increases the gap (w >= -eps_w). Cable is inactive (lambda = 0).
     */
    SLACK_FREE,

    /**
     * Active, boundary-constrained cable. The solver enforces kinematic inextensibility (w = 0
     * within tolerance), carrying non-negative physical tension (lambda_public >= 0, lambda_alg >= -eps_lambda).
     */
    TAUT
}

