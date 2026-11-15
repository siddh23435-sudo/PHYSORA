package edu.physics.relative;

import java.util.Objects;

/**
 * Reference frames for two-projectile relative motion analysis.
 *
 * <p>Distinguishes clearly between:
 * <ul>
 *   <li><b>Ground Frame S:</b> The inertial laboratory reference frame, where gravity
 *       $\vec{g}$ acts downward causing classical curved parabolic trajectories.</li>
 *   <li><b>Observer-Attached Frames S_A and S_B:</b> Non-inertial translating and accelerating
 *       frames attached to each projectile ($\vec{a}_{\text{origin}} = \vec{g} \downarrow$).
 *       Because both bodies experience the identical gravitational acceleration, the relative
 *       acceleration vanishes ($\vec{a}_{\text{rel}} = \vec{g} - \vec{g} = \vec{0}$),
 *       reducing the relative motion to uniform rectilinear (straight-line) motion.</li>
 * </ul>
 * </p>
 */
public enum ReferenceFrame {

    /**
     * Laboratory Ground Frame S:
     * Standard inertial Cartesian frame fixed to the ground.
     * Both projectiles accelerate downwards under gravity g = 9.8 m/s² along parabolic paths.
     */
    GROUND(
            "Ground Frame S (Inertial Laboratory)",
            "Inertial laboratory frame. Both projectiles describe independent parabolic paths under uniform downward gravity g.",
            true,
            "INERTIAL LAB FRAME",
            "Parabolic trajectories under uniform downward gravity g = 9.8 m/s²"
    ),

    /**
     * Observer A Frame S_A:
     * Non-inertial frame translating and accelerating downwards with Projectile A (a_origin = g).
     * Projectile A is permanently stationary at the origin (0, 0).
     * Because both projectiles experience identical downward gravity, their relative acceleration
     * vanishes (a_rel = g - g = 0), so Projectile B moves along a rectilinear (straight-line) path.
     */
    OBSERVER_A(
            "Observer A Frame S_A (Accelerating with A)",
            "Non-inertial frame accelerating with Projectile A (a_origin = g ↓). Projectile A remains at origin (0, 0). Gravitational acceleration cancels (a_rel = 0); Projectile B moves with constant relative velocity along a straight line.",
            false,
            "NON-INERTIAL (ACCELERATING) FRAME",
            "Origin attached to Projectile A | Relative acceleration a_{B/A} = g - g = 0 (Pure straight line)"
    ),

    /**
     * Observer B Frame S_B:
     * Non-inertial frame translating and accelerating downwards with Projectile B (a_origin = g).
     * Projectile B is permanently stationary at the origin (0, 0).
     * The relative acceleration vanishes (a_rel = 0), and Projectile A moves along an equal
     * and opposite straight line with constant relative velocity v_{A/B} = -v_{B/A}.
     */
    OBSERVER_B(
            "Observer B Frame S_B (Accelerating with B)",
            "Non-inertial frame accelerating with Projectile B (a_origin = g ↓). Projectile B remains at origin (0, 0). Gravitational acceleration cancels (a_rel = 0); Projectile A moves with constant relative velocity v_{A/B} = -v_{B/A} along a straight line.",
            false,
            "NON-INERTIAL (ACCELERATING) FRAME",
            "Origin attached to Projectile B | Relative acceleration a_{A/B} = g - g = 0 (Pure straight line)"
    );

    private final String displayName;
    private final String description;
    private final boolean isInertial;
    private final String frameClassification;
    private final String pedagogicalPrinciple;

    ReferenceFrame(String displayName,
                   String description,
                   boolean isInertial,
                   String frameClassification,
                   String pedagogicalPrinciple) {
        this.displayName = Objects.requireNonNull(displayName, "displayName is required");
        this.description = Objects.requireNonNull(description, "description is required");
        this.isInertial = isInertial;
        this.frameClassification = Objects.requireNonNull(frameClassification, "frameClassification is required");
        this.pedagogicalPrinciple = Objects.requireNonNull(pedagogicalPrinciple, "pedagogicalPrinciple is required");
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean isInertial() {
        return isInertial;
    }

    public String frameClassification() {
        return frameClassification;
    }

    public String pedagogicalPrinciple() {
        return pedagogicalPrinciple;
    }
}
