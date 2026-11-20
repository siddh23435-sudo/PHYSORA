package edu.physics.problem;

/**
 * Centralised physical constants for the PHYSORA platform.
 *
 * <p>Maintains explicit, documented separation between standard Earth gravitational
 * acceleration in SI metric conventions ({@value #STANDARD_GRAVITY_SI} m/s²) and standard
 * textbook / JEE problem preset gravity ({@value #STANDARD_GRAVITY_JEE} m/s²), which is
 * conventionally chosen in textbook curricula for integer/clean analytical coordinate results.</p>
 */
public final class PhysicsConstants {

    /**
     * Standard Earth surface gravitational acceleration magnitude in SI metric (m/s²).
     * Used by default in the Kinematics simulation engine and general numerical modeling.
     */
    public static final double STANDARD_GRAVITY_SI = 9.81;

    /**
     * Standard textbook and JEE examination preset gravitational acceleration magnitude (m/s²).
     * Used in curated analytical collision scenarios (e.g., Two-Projectile Pursuit flagship preset)
     * where integer coordinate apex and intersection times are required for student verification.
     */
    public static final double STANDARD_GRAVITY_JEE = 9.80;

    private PhysicsConstants() {
        // Private constructor prevents instantiation of constants class
    }
}

