package edu.physics.relative;

/**
 * Scale-aware numerical tolerance policy for relative-motion calculations and root finding.
 *
 * <p>To prevent arbitrary scale-dependent thresholds, this policy supports both absolute
 * and relative tolerances, distinguishing machine numerical precision from physical
 * scenario classifications (such as physical contact radius vs analytical co-location).</p>
 */
public final class RelativeTolerancePolicy {

    public static final double DEFAULT_POSITION_TOLERANCE = 1e-9;       // meters
    public static final double DEFAULT_VELOCITY_TOLERANCE = 1e-12;      // m/s
    public static final double DEFAULT_ACCELERATION_TOLERANCE = 1e-12;  // m/s²
    public static final double DEFAULT_TIME_TOLERANCE = 1e-12;          // seconds
    public static final double DEFAULT_RELATIVE_TOLERANCE = 1e-12;      // dimensionless
    public static final double DEFAULT_CONTACT_RADIUS = 0.2;            // meters (R_A + R_B)
    public static final double DEFAULT_PROXIMITY_RADIUS = 2.0;          // meters
    public static final double MACHINE_EPSILON = 1e-15;                 // IEEE 754 double precision limit

    public static final RelativeTolerancePolicy DEFAULT = new RelativeTolerancePolicy(
            DEFAULT_POSITION_TOLERANCE,
            DEFAULT_VELOCITY_TOLERANCE,
            DEFAULT_ACCELERATION_TOLERANCE,
            DEFAULT_RELATIVE_TOLERANCE,
            DEFAULT_CONTACT_RADIUS,
            DEFAULT_PROXIMITY_RADIUS
    );

    private final double positionTolerance;
    private final double velocityTolerance;
    private final double accelerationTolerance;
    private final double relativeTolerance;
    private final double contactRadius;
    private final double proximityRadius;

    public RelativeTolerancePolicy(double positionTolerance,
                                   double velocityTolerance,
                                   double accelerationTolerance,
                                   double relativeTolerance,
                                   double contactRadius,
                                   double proximityRadius) {
        if (!Double.isFinite(positionTolerance) || positionTolerance <= 0.0) {
            throw new IllegalArgumentException("positionTolerance must be positive and finite: " + positionTolerance);
        }
        if (!Double.isFinite(velocityTolerance) || velocityTolerance <= 0.0) {
            throw new IllegalArgumentException("velocityTolerance must be positive and finite: " + velocityTolerance);
        }
        if (!Double.isFinite(accelerationTolerance) || accelerationTolerance <= 0.0) {
            throw new IllegalArgumentException("accelerationTolerance must be positive and finite: " + accelerationTolerance);
        }
        if (!Double.isFinite(relativeTolerance) || relativeTolerance <= 0.0) {
            throw new IllegalArgumentException("relativeTolerance must be positive and finite: " + relativeTolerance);
        }
        if (!Double.isFinite(contactRadius) || contactRadius < 0.0) {
            throw new IllegalArgumentException("contactRadius must be non-negative and finite: " + contactRadius);
        }
        if (!Double.isFinite(proximityRadius) || proximityRadius < contactRadius) {
            throw new IllegalArgumentException("proximityRadius must be >= contactRadius: " + proximityRadius);
        }

        this.positionTolerance = positionTolerance;
        this.velocityTolerance = velocityTolerance;
        this.accelerationTolerance = accelerationTolerance;
        this.relativeTolerance = relativeTolerance;
        this.contactRadius = contactRadius;
        this.proximityRadius = proximityRadius;
    }

    public double positionTolerance() {
        return positionTolerance;
    }

    public double velocityTolerance() {
        return velocityTolerance;
    }

    public double accelerationTolerance() {
        return accelerationTolerance;
    }

    public double timeTolerance() {
        return DEFAULT_TIME_TOLERANCE;
    }

    public double relativeTolerance() {
        return relativeTolerance;
    }

    public double contactRadius() {
        return contactRadius;
    }

    public double proximityRadius() {
        return proximityRadius;
    }

    /**
     * Checks if a position value is numerically zero relative to an optional characteristic scale.
     */
    public boolean isZeroPosition(double value, double characteristicScale) {
        double threshold = Math.max(positionTolerance, relativeTolerance * Math.abs(characteristicScale));
        return Math.abs(value) <= threshold;
    }

    /**
     * Checks if a velocity value is numerically zero relative to an optional characteristic scale.
     */
    public boolean isZeroVelocity(double value, double characteristicScale) {
        double threshold = Math.max(velocityTolerance, relativeTolerance * Math.abs(characteristicScale));
        return Math.abs(value) <= threshold;
    }

    /**
     * Checks if an acceleration value is numerically zero relative to an optional characteristic scale.
     */
    public boolean isZeroAcceleration(double value, double characteristicScale) {
        double threshold = Math.max(accelerationTolerance, relativeTolerance * Math.abs(characteristicScale));
        return Math.abs(value) <= threshold;
    }

    /**
     * Checks if a quantity is indistinguishable from zero within double-precision machine epsilon.
     * Prevents tiny floating-point rounding artifacts from being misclassified as macroscopic physical parameters.
     */
    public boolean isMachineZero(double value, double characteristicScale) {
        double threshold = Math.max(1e-24, MACHINE_EPSILON * Math.max(Math.abs(characteristicScale), 1.0));
        return Math.abs(value) <= threshold;
    }

    /**
     * Checks if a quadratic discriminant Δ = B² - 4AC represents a repeated root / glancing contact.
     *
     * <p>Distinguishes:
     * <ul>
     *   <li>Mathematically repeated root or machine-precision zero: |Δ| <= machineThreshold</li>
     *   <li>Genuinely negative discriminant (Δ < -machineThreshold): returns false (never manufactures a collision root)</li>
     *   <li>Non-negative discriminant where roots coalesce within scale-aware tolerance: 0 <= Δ <= scaleThreshold</li>
     * </ul>
     * </p>
     */
    public boolean isZeroDiscriminant(double delta, double b, double fourAc) {
        double scale = Math.max(b * b, Math.abs(fourAc));
        double machineThreshold = Math.max(1e-24, MACHINE_EPSILON * Math.max(scale, 1.0));

        // Machine-level roundoff around zero
        if (Math.abs(delta) <= machineThreshold) {
            return true;
        }

        // Genuinely negative discriminant means no real collision roots exist
        if (delta < 0.0) {
            return false;
        }

        // Non-negative discriminant where distinct real roots coalesce within scale-aware tolerance
        double scaleThreshold = Math.max(velocityTolerance * velocityTolerance, relativeTolerance * scale);
        return delta <= scaleThreshold;
    }

    /**
     * Checks if two time values are numerically equal within scale-aware time tolerance.
     *
     * <p>Policy note: The 1.0 second scale floor establishes an absolute baseline time resolution
     * of 10^-12 s (1 picosecond) for sub-second and macroscopic classical mechanics problems.
     * This is a deliberate numerical policy for simulation stability and floating-point comparison,
     * preventing vanishing thresholds near t = 0, rather than an assertion of universal physical resolution.</p>
     */
    public boolean areTimesEqual(double t1, double t2, double characteristicTime) {
        double diff = Math.abs(t1 - t2);
        double threshold = Math.max(DEFAULT_TIME_TOLERANCE, relativeTolerance * Math.max(Math.abs(characteristicTime), 1.0));
        return diff <= threshold;
    }

    /**
     * General equality check between two scalars using scale-aware relative tolerance.
     */
    public boolean areEqual(double a, double b, double absoluteTolerance) {
        double diff = Math.abs(a - b);
        double threshold = Math.max(absoluteTolerance, relativeTolerance * Math.max(Math.abs(a), Math.abs(b)));
        return diff <= threshold;
    }
}

