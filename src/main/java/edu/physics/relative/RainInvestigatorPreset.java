package edu.physics.relative;

/**
 * Curated pedagogical presets for the Inverse Rain Investigator workbench.
 *
 * <p>All angles are stored in degrees for human-readable display and converted
 * to radians internally for physics calculations.</p>
 */
public enum RainInvestigatorPreset {

    STANDARD_JEE(
            "Standard JEE Case",
            "Ideal vertical rain baseline with strong vertical resolution.",
            0.0, 0.0,
            10.0, 45.0
    ),
    WINDY_JEE(
            "Windy JEE Classic",
            "Forward wind requiring opposite tilts when stationary vs walking forward.",
            0.0, -30.0,
            10.0, 30.0
    ),
    NEAR_PARALLEL(
            "Near-Parallel (Ill-Conditioned)",
            "Close walking speeds and angles produce high sensitivity to measurement error.",
            2.0, 20.0,
            2.2, 21.0
    ),
    IDENTICAL_SPEEDS(
            "Identical Speeds (Degenerate)",
            "Identical walking velocities provide zero vertical resolution baseline.",
            5.0, 15.0,
            5.0, 30.0
    ),
    INCONSISTENT_DIRECTION(
            "Inconsistent Direction (Upward Rain)",
            "Apparent angles imply unphysical upward falling rain.",
            0.0, 45.0,
            5.0, 15.0
    );

    private final String title;
    private final String description;
    private final double vPerson1;
    private final double angleDeg1;
    private final double vPerson2;
    private final double angleDeg2;

    RainInvestigatorPreset(String title, String description,
                           double vPerson1, double angleDeg1,
                           double vPerson2, double angleDeg2) {
        this.title = title;
        this.description = description;
        this.vPerson1 = vPerson1;
        this.angleDeg1 = angleDeg1;
        this.vPerson2 = vPerson2;
        this.angleDeg2 = angleDeg2;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public double vPerson1() {
        return vPerson1;
    }

    public double angleDeg1() {
        return angleDeg1;
    }

    public double angleRad1() {
        return Math.toRadians(angleDeg1);
    }

    public double vPerson2() {
        return vPerson2;
    }

    public double angleDeg2() {
        return angleDeg2;
    }

    public double angleRad2() {
        return Math.toRadians(angleDeg2);
    }
}

