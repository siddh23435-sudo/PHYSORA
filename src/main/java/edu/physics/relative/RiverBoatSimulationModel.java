package edu.physics.relative;

import edu.physics.simulation.Vector2D;

import java.util.Objects;

/**
 * Authoritative physical simulation model for river-boat vector crossing problems.
 *
 * <p>Encapsulates the scenario parameters, dynamic time-dependent positions,
 * velocity vectors in both river and ground frames, and the exact analytical solution
 * provided by {@link RiverBoatSolver}.</p>
 *
 * <p>Physics Conventions:
 * <ul>
 *   <li>River current flows along the +x axis with speed u = riverSpeed (u &ge; 0).</li>
 *   <li>Across-river direction to the opposite bank is along the +y axis with width w = riverWidth (w &gt; 0).</li>
 *   <li>Steering angle &theta; is measured upstream from the +y bank normal (-&pi;/2 &le; &theta; &le; &pi;/2).</li>
 *   <li>Positive &theta; directs the boat upstream (-x direction relative to river water).</li>
 * </ul>
 * Ground-frame velocity components:
 * <pre>
 *   v_x = u - v_b * sin(&theta;)
 *   v_y = v_b * cos(&theta;)
 * </pre>
 * </p>
 */
public final class RiverBoatSimulationModel {

    public static final double DEFAULT_RIVER_WIDTH = 100.0; // meters
    public static final double DEFAULT_RIVER_SPEED = 3.0;   // m/s
    public static final double DEFAULT_BOAT_SPEED = 5.0;    // m/s
    public static final double DEFAULT_HEADING_DEG = 36.86989765; // degrees (~ arcsin(3/5))

    private final double riverWidth;
    private final double riverSpeed;
    private final double boatSpeed;
    private final double headingAngleRad;
    private final RiverBoatResult analyticalResult;
    private final double effectiveDuration;

    public RiverBoatSimulationModel(double riverWidth, double riverSpeed, double boatSpeed, double headingAngleRad) {
        if (!Double.isFinite(riverWidth) || riverWidth <= 0.0) {
            throw new IllegalArgumentException("riverWidth must be positive and finite: " + riverWidth);
        }
        if (!Double.isFinite(riverSpeed) || riverSpeed < 0.0) {
            throw new IllegalArgumentException("riverSpeed must be non-negative and finite: " + riverSpeed);
        }
        if (!Double.isFinite(boatSpeed) || boatSpeed <= 0.0) {
            throw new IllegalArgumentException("boatSpeed must be positive and finite: " + boatSpeed);
        }
        if (!Double.isFinite(headingAngleRad)) {
            throw new IllegalArgumentException("headingAngleRad must be finite: " + headingAngleRad);
        }

        this.riverWidth = riverWidth;
        this.riverSpeed = riverSpeed;
        this.boatSpeed = boatSpeed;
        this.headingAngleRad = headingAngleRad;
        this.analyticalResult = RiverBoatSolver.solveForHeading(riverWidth, riverSpeed, boatSpeed, headingAngleRad);

        if (analyticalResult.crossingTime().isPresent()) {
            this.effectiveDuration = analyticalResult.crossingTime().getAsDouble();
        } else {
            // If the boat never reaches the opposite bank (e.g. vy <= 0 or holding stationary)
            this.effectiveDuration = 30.0;
        }
    }

    public static RiverBoatSimulationModel ofDegrees(double riverWidth, double riverSpeed, double boatSpeed, double headingAngleDeg) {
        return new RiverBoatSimulationModel(riverWidth, riverSpeed, boatSpeed, Math.toRadians(headingAngleDeg));
    }

    public static RiverBoatSimulationModel presetShortestTime(double riverWidth, double riverSpeed, double boatSpeed) {
        return new RiverBoatSimulationModel(riverWidth, riverSpeed, boatSpeed, 0.0);
    }

    public static RiverBoatSimulationModel presetShortestPathOrMinDrift(double riverWidth, double riverSpeed, double boatSpeed) {
        RiverBoatResult solved = RiverBoatSolver.solveShortestPath(riverWidth, riverSpeed, boatSpeed);
        return new RiverBoatSimulationModel(riverWidth, riverSpeed, boatSpeed, solved.headingAngleRad());
    }

    public static RiverBoatSimulationModel presetEqualSpeedBoundary(double riverWidth, double speed) {
        return new RiverBoatSimulationModel(riverWidth, speed, speed, Math.PI / 2.0);
    }

    public static RiverBoatSimulationModel defaultScenario() {
        return ofDegrees(DEFAULT_RIVER_WIDTH, DEFAULT_RIVER_SPEED, DEFAULT_BOAT_SPEED, DEFAULT_HEADING_DEG);
    }

    public double riverWidth() {
        return riverWidth;
    }

    public double riverSpeed() {
        return riverSpeed;
    }

    public double boatSpeed() {
        return boatSpeed;
    }

    public double headingAngleRad() {
        return headingAngleRad;
    }

    public double headingAngleDeg() {
        return Math.toDegrees(headingAngleRad);
    }

    public RiverBoatResult analyticalResult() {
        return analyticalResult;
    }

    public double effectiveDuration() {
        return effectiveDuration;
    }

    /**
     * Relative velocity of the boat with respect to river water:
     * <pre>
     *   v_{b/r} = -v_b * sin(&theta;) i + v_b * cos(&theta;) j
     * </pre>
     */
    public Vector2D velocityBoatRelative() {
        double vx = -boatSpeed * Math.sin(headingAngleRad);
        double vy = boatSpeed * Math.cos(headingAngleRad);
        return new Vector2D(vx, vy);
    }

    /**
     * River current velocity with respect to ground bank:
     * <pre>
     *   u = riverSpeed i + 0 j
     * </pre>
     */
    public Vector2D velocityRiverGround() {
        return new Vector2D(riverSpeed, 0.0);
    }

    /**
     * Resultant velocity of the boat with respect to ground bank:
     * <pre>
     *   v_{b/g} = v_{b/r} + u = (riverSpeed - v_b * sin(&theta;)) i + (v_b * cos(&theta;)) j
     * </pre>
     */
    public Vector2D velocityBoatGround() {
        return velocityBoatRelative().add(velocityRiverGround());
    }

    /**
     * Computes the boat position (in meters) relative to the launch dock (0, 0) at physical time t.
     * When t exceeds crossing time, the boat position is clamped to the landing point on the north bank.
     */
    public Vector2D boatPosition(double time) {
        if (time <= 0.0) {
            return Vector2D.ZERO;
        }

        Vector2D vGround = velocityBoatGround();

        if (analyticalResult.crossingTime().isPresent()) {
            double tCross = analyticalResult.crossingTime().getAsDouble();
            if (time >= tCross) {
                double drift = analyticalResult.downstreamDrift().orElse(vGround.x() * tCross);
                return new Vector2D(drift, riverWidth);
            }
        }

        double x = vGround.x() * time;
        double y = Math.max(0.0, vGround.y() * time);
        return new Vector2D(x, y);
    }

    /**
     * Checks if the boat has completed crossing to the opposite bank at physical time t.
     */
    public boolean isLanded(double time) {
        return analyticalResult.crossingTime().isPresent() && time >= analyticalResult.crossingTime().getAsDouble();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiverBoatSimulationModel that = (RiverBoatSimulationModel) o;
        return Double.compare(that.riverWidth, riverWidth) == 0 &&
                Double.compare(that.riverSpeed, riverSpeed) == 0 &&
                Double.compare(that.boatSpeed, boatSpeed) == 0 &&
                Double.compare(that.headingAngleRad, headingAngleRad) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(riverWidth, riverSpeed, boatSpeed, headingAngleRad);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "RiverBoatSimulationModel[w=%.1f m, u=%.1f m/s, vb=%.1f m/s, theta=%.2f deg, type=%s]",
                riverWidth, riverSpeed, boatSpeed, headingAngleDeg(), analyticalResult.crossingType());
    }
}
