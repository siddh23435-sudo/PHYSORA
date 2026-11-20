package edu.physics.relative;

import edu.physics.simulation.Vector2D;


/**
 * Authoritative physical simulation model for Rain-Man relative motion and umbrella orientation.
 *
 * <p>Coordinate Conventions:
 * <ul>
 *   <li>Horizontal motion is along +x (m/s). Positive x is East / Forward.</li>
 *   <li>Upward vertical is +y (m/s). Rain falls downward with positive downward speed v_d &gt; 0,
 *       so the ground rain velocity is v_r = (v_{rx}, -v_d).</li>
 *   <li>Person moves horizontally with signed velocity v_m = (v_m, 0).</li>
 *   <li>Apparent rain velocity: v_{r/m} = v_r - v_m = (v_{rx} - v_m, -v_d).</li>
 *   <li>Incoming rain direction: u_{incoming} = -v_{r/m} = (v_m - v_{rx}, v_d).</li>
 *   <li>Optimal umbrella shaft angle from upward vertical (+y):
 *       &phi;_{optimal} = atan2(v_m - v_{rx}, v_d).</li>
 *   <li>Positive angle indicates forward tilt (+x direction). Negative indicates backward tilt (-x).</li>
 * </ul>
 * </p>
 *
 * <p>Numerical calculations delegate strictly to {@link RainManSolver#solve(Vector2D, Vector2D)}.</p>
 */
public final class RainManSimulationModel {

    public static final double DEFAULT_DOWNWARD_SPEED = 8.0;   // m/s
    public static final double DEFAULT_HORIZONTAL_RAIN = 0.0;  // m/s (no wind)
    public static final double DEFAULT_PERSON_SPEED = 3.0;     // m/s (walking forward)
    public static final double DEFAULT_UMBRELLA_ANGLE = 0.0;   // rad (vertical)

    public static final double ALIGNMENT_TOLERANCE_WELL_DEG = 3.0;
    public static final double ALIGNMENT_TOLERANCE_SLIGHT_DEG = 12.0;

    /**
     * Alignment quality classification for student feedback.
     */
    public enum AlignmentStatus {
        WELL_ALIGNED("Well aligned (≤ 3°)", "#15803d", "#dcfce7"),
        SLIGHTLY_MISALIGNED("Slightly misaligned (3°–12°)", "#b45309", "#fef3c7"),
        MISALIGNED("Misaligned (> 12°)", "#b91c1c", "#fee2e2");

        private final String label;
        private final String textColor;
        private final String backgroundColor;

        AlignmentStatus(String label, String textColor, String backgroundColor) {
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

    private final double vDown;
    private final double vRainX;
    private final double vPerson;
    private final double umbrellaAngleRad;
    private final RainManResult analyticalResult;

    /**
     * Constructs a RainManSimulationModel with given physical parameters.
     *
     * @param vDown             Positive downward rain speed (m/s)
     * @param vRainX            Horizontal rain velocity (m/s)
     * @param vPerson           Signed person horizontal walking velocity (m/s)
     * @param umbrellaAngleRad  Student's actual umbrella shaft tilt angle from upward vertical (rad)
     */
    public RainManSimulationModel(double vDown, double vRainX, double vPerson, double umbrellaAngleRad) {
        if (!Double.isFinite(vDown) || vDown <= 0.0) {
            throw new IllegalArgumentException("Downward rain speed vDown must be positive and finite: " + vDown);
        }
        if (!Double.isFinite(vRainX)) {
            throw new IllegalArgumentException("vRainX must be finite: " + vRainX);
        }
        if (!Double.isFinite(vPerson)) {
            throw new IllegalArgumentException("vPerson must be finite: " + vPerson);
        }
        if (!Double.isFinite(umbrellaAngleRad)) {
            throw new IllegalArgumentException("umbrellaAngleRad must be finite: " + umbrellaAngleRad);
        }
        if (Math.abs(umbrellaAngleRad) > (Math.PI / 2.0 + 1e-6)) {
            throw new IllegalArgumentException("umbrellaAngleRad must be within [-pi/2, pi/2]: " + umbrellaAngleRad);
        }

        this.vDown = vDown;
        this.vRainX = vRainX;
        this.vPerson = vPerson;
        this.umbrellaAngleRad = Math.max(-Math.PI / 2.0, Math.min(Math.PI / 2.0, umbrellaAngleRad));

        // Authoritative delegate to RainManSolver
        Vector2D groundRain = new Vector2D(vRainX, -vDown);
        Vector2D groundPerson = new Vector2D(vPerson, 0.0);
        this.analyticalResult = RainManSolver.solve(groundRain, groundPerson);
    }

    public static RainManSimulationModel ofDegrees(double vDown, double vRainX, double vPerson, double umbrellaAngleDeg) {
        return new RainManSimulationModel(vDown, vRainX, vPerson, Math.toRadians(umbrellaAngleDeg));
    }

    public static RainManSimulationModel defaultScenario() {
        return new RainManSimulationModel(
                DEFAULT_DOWNWARD_SPEED,
                DEFAULT_HORIZONTAL_RAIN,
                DEFAULT_PERSON_SPEED,
                DEFAULT_UMBRELLA_ANGLE
        );
    }

    public static RainManSimulationModel presetStationaryVerticalRain() {
        return new RainManSimulationModel(DEFAULT_DOWNWARD_SPEED, 0.0, 0.0, 0.0);
    }

    public static RainManSimulationModel presetForwardWalkingVerticalRain() {
        return new RainManSimulationModel(8.0, 0.0, 3.0, 0.0);
    }

    public static RainManSimulationModel presetTailwindZeroRelativeHorizontal() {
        // Horizontal rain speed equals walking speed -> rain appears purely vertical to walker!
        return new RainManSimulationModel(6.0, 4.0, 4.0, 0.0);
    }

    public static RainManSimulationModel presetHeadwindRunning() {
        // Wind blows opposite to runner (-3 m/s), runner runs forward at 5 m/s
        return new RainManSimulationModel(8.0, -3.0, 5.0, 0.0);
    }

    public static RainManSimulationModel presetBackwardWalking() {
        // Person walks backward (-4 m/s) in vertical rain
        return new RainManSimulationModel(7.0, 0.0, -4.0, 0.0);
    }

    // --- Getters and Physical Quantities ---

    public double vDown() {
        return vDown;
    }

    public double vRainX() {
        return vRainX;
    }

    public double vPerson() {
        return vPerson;
    }

    public double umbrellaAngleRad() {
        return umbrellaAngleRad;
    }

    public double umbrellaAngleDeg() {
        return Math.toDegrees(umbrellaAngleRad);
    }

    public RainManResult analyticalResult() {
        return analyticalResult;
    }

    public Vector2D rainVelocityGround() {
        return analyticalResult.rainVelocityGround();
    }

    public Vector2D personVelocityGround() {
        return analyticalResult.personVelocityGround();
    }

    public Vector2D apparentRainVelocity() {
        return analyticalResult.apparentRainVelocity();
    }

    public double apparentRainSpeed() {
        return analyticalResult.apparentRainSpeed();
    }

    /**
     * Optimal umbrella shaft angle in radians, measured from upward vertical (+y).
     * Positive indicates forward tilt (+x), negative backward tilt (-x).
     */
    public double optimalAngleRad() {
        double rad = analyticalResult.umbrellaAngleRad();
        return Math.abs(rad) < 1e-9 ? 0.0 : rad;
    }

    /**
     * Optimal umbrella shaft angle in degrees.
     */
    public double optimalAngleDeg() {
        double deg = Math.toDegrees(optimalAngleRad());
        return Math.abs(deg) < 1e-9 ? 0.0 : deg;
    }

    /**
     * Signed angular difference: actual umbrella angle minus optimal umbrella angle (radians).
     */
    public double angularDifferenceRad() {
        return umbrellaAngleRad - optimalAngleRad();
    }

    /**
     * Absolute angular alignment error in radians: |actual - optimal|.
     */
    public double angularErrorRad() {
        return Math.abs(angularDifferenceRad());
    }

    /**
     * Absolute angular alignment error in degrees.
     */
    public double angularErrorDeg() {
        return Math.toDegrees(angularErrorRad());
    }

    /**
     * Classifies the current alignment into pedagogical feedback levels.
     */
    public AlignmentStatus alignmentStatus() {
        double errDeg = angularErrorDeg();
        if (errDeg <= ALIGNMENT_TOLERANCE_WELL_DEG) {
            return AlignmentStatus.WELL_ALIGNED;
        } else if (errDeg <= ALIGNMENT_TOLERANCE_SLIGHT_DEG) {
            return AlignmentStatus.SLIGHTLY_MISALIGNED;
        } else {
            return AlignmentStatus.MISALIGNED;
        }
    }

    /**
     * Returns a new model with the umbrella angle set to the optimal angle.
     */
    public RainManSimulationModel snapToOptimal() {
        return new RainManSimulationModel(vDown, vRainX, vPerson, optimalAngleRad());
    }

    /**
     * Returns a new model with the specified umbrella angle in radians.
     */
    public RainManSimulationModel withUmbrellaAngleRad(double newAngleRad) {
        return new RainManSimulationModel(vDown, vRainX, vPerson, newAngleRad);
    }

    /**
     * Returns a new model with the specified umbrella angle in degrees.
     */
    public RainManSimulationModel withUmbrellaAngleDeg(double newAngleDeg) {
        return ofDegrees(vDown, vRainX, vPerson, newAngleDeg);
    }
}
