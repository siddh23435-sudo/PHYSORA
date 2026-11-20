package edu.physics.relative;

/**
 * Three-tier numerical verification classification for inverse rain reconstruction.
 *
 * <p>Evaluates whether the reconstructed ground-frame rain vector reproduces the empirical
 * observation angles through forward simulation using {@link RainManSolver#solve(edu.physics.simulation.Vector2D, edu.physics.simulation.Vector2D)}.</p>
 */
public enum RainVerificationStatus {

    /**
     * Forward simulation cleanly reproduces both observation angles within the UI threshold (residual <= 1e-4 degrees).
     */
    VERIFIED("Reconstruction Verified", "#15803d", "#dcfce7"),

    /**
     * Solvable result whose forward simulation residual exceeds 1e-4 degrees (numerical discrepancy or extreme ill-conditioning).
     */
    NUMERICAL_WARNING("Verification Discrepancy", "#b45309", "#fef3c7"),

    /**
     * Verification unavailable because observation inputs do not resolve to a physical rain vector.
     */
    NOT_VERIFIABLE("Verification Unavailable", "#64748b", "#f1f5f9");

    private final String label;
    private final String textColor;
    private final String backgroundColor;

    RainVerificationStatus(String label, String textColor, String backgroundColor) {
        this.label = label;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    public String label() {
        return label;
    }

    public String textColor() {
        return textColor;
    }

    public String backgroundColor() {
        return backgroundColor;
    }
}

