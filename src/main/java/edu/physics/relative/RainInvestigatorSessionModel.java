package edu.physics.relative;

import edu.physics.simulation.Vector2D;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Headless, JavaFX-independent session model for two-observation inverse rain velocity reconstruction.
 *
 * <p>Coordinates observation state, active pedagogical stage, preset loading, candidate velocity
 * exploration along Observation Line 1, evaluation delegation to {@link RainInvestigatorEvaluator},
 * and forward round-trip verification through {@link RainManSolver}.</p>
 *
 * <p>Key architectural properties:
 * <ul>
 *   <li><b>Zero JavaFX dependencies:</b> Can be fully tested and run in headless environments.</li>
 *   <li><b>Coherent source of truth:</b> Investigation stage and Observation 2 visibility are unified.
 *       Stage 2 (Ambiguity) hides Observation 2; other stages display both observations.</li>
 *   <li><b>Immutable snapshots:</b> Exposes immutable results and verification diagnostics so UI components
 *       do not recompute physics or numerical residuals.</li>
 * </ul>
 * </p>
 */
public final class RainInvestigatorSessionModel {

    private final List<RainInvestigatorChangeListener> listeners = new CopyOnWriteArrayList<>();

    private double vPerson1;
    private double angleRad1;
    private double vPerson2;
    private double angleRad2;

    private RainInvestigatorStage stage;
    private Optional<RainInvestigatorPreset> activePreset;

    private RainInvestigatorResult evaluatorResult;
    private RainVerificationSnapshot verificationSnapshot;

    /**
     * Constructs a session model initialized with the default Standard JEE preset.
     */
    public RainInvestigatorSessionModel() {
        this(RainInvestigatorPreset.STANDARD_JEE);
    }

    /**
     * Constructs a session model initialized with a specific preset.
     *
     * @param preset Curated preset (must not be null)
     */
    public RainInvestigatorSessionModel(RainInvestigatorPreset preset) {
        Objects.requireNonNull(preset, "preset must not be null");
        this.stage = RainInvestigatorStage.STAGE_1_EVIDENCE;
        applyPresetInternal(preset);
    }

    /**
     * Constructs a session model with explicit custom observations.
     *
     * @param vPerson1  Observer 1 walking velocity (m/s)
     * @param angleRad1 Observer 1 apparent umbrella angle (rad)
     * @param vPerson2  Observer 2 walking velocity (m/s)
     * @param angleRad2 Observer 2 apparent umbrella angle (rad)
     */
    public RainInvestigatorSessionModel(double vPerson1, double angleRad1, double vPerson2, double angleRad2) {
        this.stage = RainInvestigatorStage.STAGE_1_EVIDENCE;
        this.activePreset = Optional.empty();
        updateObservationsInternal(vPerson1, angleRad1, vPerson2, angleRad2);
    }

    // --- State Accessors ---

    public double vPerson1() {
        return vPerson1;
    }

    public double angleRad1() {
        return angleRad1;
    }

    public double angleDeg1() {
        return Math.toDegrees(angleRad1);
    }

    public double vPerson2() {
        return vPerson2;
    }

    public double angleRad2() {
        return angleRad2;
    }

    public double angleDeg2() {
        return Math.toDegrees(angleRad2);
    }

    public RainInvestigatorStage stage() {
        return stage;
    }

    /**
     * Single source of truth for Observation 2 visibility, derived from active stage.
     * Returns false exclusively during STAGE_2_AMBIGUITY.
     */
    public boolean isObservation2Visible() {
        return stage.isObservation2Visible();
    }

    /**
     * Returns true if the presentation layer is in single-observation ambiguity mode.
     */
    public boolean isUnderdetermined() {
        return stage == RainInvestigatorStage.STAGE_2_AMBIGUITY;
    }

    public Optional<RainInvestigatorPreset> activePreset() {
        return activePreset;
    }

    public RainInvestigatorResult evaluatorResult() {
        return evaluatorResult;
    }

    public RainVerificationSnapshot verificationSnapshot() {
        return verificationSnapshot;
    }

    // --- State Modifiers ---

    /**
     * Sets the active pedagogical stage. Stage 2 automatically hides Observation 2;
     * other stages restore Observation 2 visibility.
     */
    public void setStage(RainInvestigatorStage newStage) {
        Objects.requireNonNull(newStage, "newStage must not be null");
        if (this.stage != newStage) {
            this.stage = newStage;
            notifyListeners();
        }
    }

    /**
     * Synchronizes observation 2 visibility with the stage hierarchy.
     * Setting false transitions to STAGE_2_AMBIGUITY; setting true while in Stage 2 transitions to STAGE_3_GEOMETRY.
     */
    public void setObservation2Visible(boolean visible) {
        if (!visible && stage != RainInvestigatorStage.STAGE_2_AMBIGUITY) {
            setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY);
        } else if (visible && stage == RainInvestigatorStage.STAGE_2_AMBIGUITY) {
            setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
        }
    }

    /**
     * Updates all observation parameters simultaneously and re-evaluates.
     */
    public void setObservations(double vPerson1, double angleRad1, double vPerson2, double angleRad2) {
        this.activePreset = Optional.empty();
        updateObservationsInternal(vPerson1, angleRad1, vPerson2, angleRad2);
        notifyListeners();
    }

    /**
     * Updates observation 1 parameters and re-evaluates.
     */
    public void setObservation1(double vPerson1, double angleRad1) {
        setObservations(vPerson1, angleRad1, this.vPerson2, this.angleRad2);
    }

    /**
     * Updates observation 2 parameters and re-evaluates.
     */
    public void setObservation2(double vPerson2, double angleRad2) {
        setObservations(this.vPerson1, this.angleRad1, vPerson2, angleRad2);
    }

    /**
     * Loads a curated pedagogical preset.
     */
    public void loadPreset(RainInvestigatorPreset preset) {
        Objects.requireNonNull(preset, "preset must not be null");
        applyPresetInternal(preset);
        notifyListeners();
    }

    // --- Candidate Velocities for Ambiguity Mode ---

    /**
     * Computes a candidate ground rain velocity lying on Observation Line 1 for a given positive downward speed.
     *
     * <p>Observation Line 1 equation:
     * <pre>
     *   v_x - v_1 = v_y * tan(phi_1)  ==>  v_x = v_1 - v_d * tan(phi_1)
     *   v_y = -v_d
     * </pre>
     * </p>
     *
     * @param vDown Positive downward rain speed in m/s (must be finite and > 0)
     * @return Ground rain vector lying on Observation Line 1
     * @throws IllegalArgumentException if vDown <= 0 or non-finite
     * @throws IllegalStateException if Observation 1 parameters are non-finite or angle exceeds supported domain
     */
    public Vector2D getCandidateRainVelocityForDownwardSpeed(double vDown) {
        if (!Double.isFinite(vDown) || vDown <= 0.0) {
            throw new IllegalArgumentException("Downward rain speed vDown must be positive and finite: " + vDown);
        }
        if (!Double.isFinite(vPerson1) || !Double.isFinite(angleRad1)) {
            throw new IllegalStateException("Observation 1 parameters must be finite numbers.");
        }
        if (Math.abs(angleRad1) >= RainInvestigatorEvaluator.MAX_SUPPORTED_ANGLE_RAD) {
            throw new IllegalStateException("Observation 1 angle magnitude exceeds supported domain: " + angleRad1);
        }

        double tan1 = Math.tan(angleRad1);
        double vx = vPerson1 - vDown * tan1;
        double vy = -vDown;
        return new Vector2D(vx, vy);
    }

    /**
     * Convenience method returning candidate rain velocity for a negative vertical component vy < 0.
     *
     * @param vy Negative vertical rain velocity in m/s (must be finite and < 0)
     * @return Ground rain vector lying on Observation Line 1
     */
    public Vector2D getCandidateRainVelocityForVy(double vy) {
        if (!Double.isFinite(vy) || vy >= 0.0) {
            throw new IllegalArgumentException("Physical falling rain requires negative vertical velocity (vy < 0): " + vy);
        }
        return getCandidateRainVelocityForDownwardSpeed(-vy);
    }

    // --- Listeners ---

    public void addChangeListener(RainInvestigatorChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(RainInvestigatorChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (RainInvestigatorChangeListener listener : listeners) {
            listener.onSessionChanged(this);
        }
    }

    // --- Internal Helpers ---

    private void applyPresetInternal(RainInvestigatorPreset preset) {
        this.activePreset = Optional.of(preset);
        updateObservationsInternal(preset.vPerson1(), preset.angleRad1(), preset.vPerson2(), preset.angleRad2());
    }

    private void updateObservationsInternal(double v1, double phi1, double v2, double phi2) {
        this.vPerson1 = v1;
        this.angleRad1 = phi1;
        this.vPerson2 = v2;
        this.angleRad2 = phi2;

        this.evaluatorResult = RainInvestigatorEvaluator.evaluate(v1, phi1, v2, phi2);

        if (this.evaluatorResult.isSolvable() && this.evaluatorResult.reconstructedRain().isPresent()) {
            this.verificationSnapshot = RainVerificationSnapshot.compute(
                    this.evaluatorResult.reconstructedRainVelocity(),
                    v1, phi1, v2, phi2,
                    this.evaluatorResult.status(),
                    this.evaluatorResult.downwardConditionNumber()
            );
        } else {
            this.verificationSnapshot = RainVerificationSnapshot.notVerifiable(
                    "Evaluator status is " + this.evaluatorResult.status() + ": verification unavailable."
            );
        }
    }
}

