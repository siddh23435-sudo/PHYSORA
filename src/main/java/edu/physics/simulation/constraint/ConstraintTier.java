package edu.physics.simulation.constraint;

/**
 * Four-tier classification for kinematic and unilateral constraint rows.
 *
 * <p>Tier Partitioning:
 * <ul>
 *   <li>{@link #TIER_1A_INDEPENDENT}: Standard linearly independent constraints with residual
 *       {@code r_diag >= tau_indep = sqrt(10^-5) ≈ 3.162277e-3}. Solved unregularized.</li>
 *   <li>{@link #TIER_1B_REGULARIZED}: Near-dependent or ill-conditioned constraints with residual
 *       {@code tau_redun = 1.0e-4 <= r_diag < tau_indep}. Solved with augmented QR Tikhonov damping.</li>
 *   <li>{@link #TIER_2_REDUNDANT}: Linearly dependent redundant constraints with residual
 *       {@code r_diag < tau_redun = 1.0e-4}. Omitted from active set, assigned zero multiplier,
 *       and subjected to unregularized compatibility verification.</li>
 *   <li>{@link #TIER_4_INCONSISTENT}: Inconsistent, conflicting, or singular constraints
 *       (e.g., zero Delassus mobility or acceleration compatibility residual violation). Rejected with exception.</li>
 * </ul>
 */
public enum ConstraintTier {
    TIER_1A_INDEPENDENT,
    TIER_1B_REGULARIZED,
    TIER_2_REDUNDANT,
    TIER_4_INCONSISTENT
}

