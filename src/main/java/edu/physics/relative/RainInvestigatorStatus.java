package edu.physics.relative;

/**
 * Outcome and validity classification for two-observation inverse rain velocity reconstruction.
 *
 * <p>Distinguishes physically solvable cases (well-conditioned vs ill-conditioned) from
 * mathematical degeneracies, domain violations, and numerical calculation failures.</p>
 */
public enum RainInvestigatorStatus {

    /**
     * Reconstructed ground-frame rain velocity is unique, physically consistent,
     * and numerically stable (downward condition metric kappa_down <= 25.0).
     */
    VALID(true, "Solution well-conditioned and numerically stable."),

    /**
     * Reconstructed ground-frame rain velocity is mathematically unique and physically consistent,
     * but highly sensitive to observation angle errors (downward condition metric kappa_down > 25.0).
     */
    ILL_CONDITIONED(true, "Solution mathematically valid but highly sensitive to angular measurement error."),

    /**
     * Observations were recorded at identical walking velocities within scale-aware tolerance,
     * providing zero vertical baseline resolution.
     */
    DEGENERATE_IDENTICAL_SPEEDS(false, "Observations at identical walking velocities provide zero vertical resolution."),

    /**
     * Observations produce parallel velocity-space rays that do not intersect.
     */
    DEGENERATE_PARALLEL_OBSERVATIONS(false, "Observations produce parallel velocity rays that do not intersect."),

    /**
     * Apparent observation angles are physically inconsistent with falling rain (calculated downward speed v_down <= 0).
     */
    INVALID_INCONSISTENT_DIRECTION(false, "Apparent angles are physically inconsistent with downward rain (calculated v_down <= 0)."),

    /**
     * Observation angle exceeds the supported open domain (|phi| >= pi/2 - 1e-6 rad).
     */
    INVALID_ANGLE_RANGE(false, "Observation angle out of domain (|phi| >= 90 deg)."),

    /**
     * Input walking speeds or angles contain non-finite numbers (NaN or Infinity).
     */
    INVALID_NON_FINITE(false, "Input parameters contain non-finite numbers (NaN or Infinity)."),

    /**
     * Intermediate calculations (e.g. subtraction overflow, quotient overflow) or solver output
     * produced non-finite values.
     */
    NUMERICAL_FAILURE(false, "Intermediate calculation or solver output produced non-finite values (overflow or undefined arithmetic).");

    private final boolean solvable;
    private final String description;

    RainInvestigatorStatus(boolean solvable, String description) {
        this.solvable = solvable;
        this.description = description;
    }

    /**
     * Returns true if this status represents a physically and mathematically solvable observation pair.
     * Only VALID and ILL_CONDITIONED return true.
     */
    public boolean isSolvable() {
        return solvable;
    }

    /**
     * Brief human-readable description of this outcome.
     */
    public String description() {
        return description;
    }
}

