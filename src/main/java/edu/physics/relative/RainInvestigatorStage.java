package edu.physics.relative;

/**
 * Pedagogical stages of the two-observation inverse rain investigation experience.
 *
 * <p>Constitutes the single source of truth for the investigation progression
 * and observation visibility.</p>
 */
public enum RainInvestigatorStage {

    /**
     * Stage 1: Review empirical evidence (two observers with known walking speeds and umbrella angles).
     */
    STAGE_1_EVIDENCE("1. Observation Evidence", "Review observed walking speeds and umbrella tilt angles."),

    /**
     * Stage 2: Single-observation ambiguity demonstration (Observation 2 is hidden; Line 1 permits infinite solutions).
     */
    STAGE_2_AMBIGUITY("2. Single-Observation Ambiguity", "Observe that one measurement permits infinitely many rain velocities."),

    /**
     * Stage 3: Velocity-space geometric intersection (Both lines visible; unique intersection in physical half-plane).
     */
    STAGE_3_GEOMETRY("3. Velocity-Space Geometry", "Analyze the two observation lines and their intersection."),

    /**
     * Stage 4: Analytical mathematical reconstruction and condition diagnostics.
     */
    STAGE_4_RECONSTRUCTION("4. Analytical Reconstruction", "Inspect governing equations and condition diagnostics."),

    /**
     * Stage 5: Round-trip forward simulation verification and residual checks.
     */
    STAGE_5_VERIFICATION("5. Round-Trip Verification", "Verify reconstructed ground rain through forward apparent simulation.");

    private final String title;
    private final String description;

    RainInvestigatorStage(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /**
     * Single source of truth for Observation 2 visibility.
     * Observation 2 is hidden exclusively during Stage 2 to demonstrate underdetermination.
     */
    public boolean isObservation2Visible() {
        return this != STAGE_2_AMBIGUITY;
    }
}

