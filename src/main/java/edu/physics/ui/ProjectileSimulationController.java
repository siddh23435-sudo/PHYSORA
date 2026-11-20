package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.OptionalDouble;

/** Shares one analytical model and one simulation clock between all bench views. */
final class ProjectileSimulationController {
    static final double OPEN_ENDED_DURATION = 8.0;
    private static final double MAX_FRAME_DELTA_SECONDS = 0.1;
    static final double DEFAULT_LAUNCH_SPEED = 20.0;
    static final double DEFAULT_LAUNCH_ANGLE = 45.0;
    static final double DEFAULT_GRAVITY = 9.81;

    /**
     * Deterministic simulation status reflecting the physical and clock state of the model.
     */
    public enum SimulationStatus {
        READY("READY", "Simulation ready for launch"),
        PLAYING("PLAYING", "Trajectory in motion"),
        PAUSED("PAUSED", "Simulation paused"),
        AT_APEX("AT APEX", "Projectile at peak altitude"),
        LANDED("LANDED", "Projectile reached landing surface");

        private final String label;
        private final String description;

        SimulationStatus(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() { return label; }
        public String description() { return description; }
    }

    private final DoubleProperty launchSpeed = new SimpleDoubleProperty(DEFAULT_LAUNCH_SPEED);
    private final DoubleProperty launchAngle = new SimpleDoubleProperty(DEFAULT_LAUNCH_ANGLE);
    private final DoubleProperty gravity = new SimpleDoubleProperty(DEFAULT_GRAVITY);
    private final DoubleProperty initialX = new SimpleDoubleProperty(0.0);
    private final DoubleProperty initialY = new SimpleDoubleProperty(0.0);
    private final DoubleProperty landingY = new SimpleDoubleProperty(0.0);
    private final DoubleProperty simulationTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty playbackSpeed = new SimpleDoubleProperty(1.0);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final ObjectProperty<ProjectileMotion> model = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<SimulationStatus> status = new SimpleObjectProperty<>(SimulationStatus.READY);
    private boolean restoringDefaults;

    private long previousFrameNanos;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (previousFrameNanos == 0L) {
                previousFrameNanos = now;
                return;
            }
            double elapsedSeconds = Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0);
            previousFrameNanos = now;
            advanceByElapsedSeconds(elapsedSeconds);
        }
    };

    ProjectileSimulationController() {
        launchSpeed.addListener((obs, oldValue, newValue) -> rebuildWhenActive());
        launchAngle.addListener((obs, oldValue, newValue) -> rebuildWhenActive());
        gravity.addListener((obs, oldValue, newValue) -> rebuildWhenActive());
        initialX.addListener((obs, oldValue, newValue) -> rebuildWhenActive());
        initialY.addListener((obs, oldValue, newValue) -> rebuildWhenActive());
        landingY.addListener((obs, oldValue, newValue) -> rebuildWhenActive());

        simulationTime.addListener((obs, oldValue, newValue) -> updateStatus());
        playing.addListener((obs, oldValue, newValue) -> updateStatus());
        model.addListener((obs, oldValue, newValue) -> updateStatus());

        rebuildModel();
    }

    private void rebuildWhenActive() {
        if (!restoringDefaults) rebuildModel();
    }

    private void rebuildModel() {
        pause();
        model.set(new ProjectileMotion(launchSpeed.get(), launchAngle.get(), gravity.get(),
                initialX.get(), initialY.get(), landingY.get()));
        simulationTime.set(0.0);
        updateStatus();
    }

    private void updateStatus() {
        ProjectileMotion m = model.get();
        if (m == null) {
            status.set(SimulationStatus.READY);
            return;
        }
        double t = simulationTime.get();
        OptionalDouble flightTime = m.timeOfFlight();
        boolean hasLanded = flightTime.isPresent() && flightTime.getAsDouble() > 0.0
                && t >= (flightTime.getAsDouble() - 1e-4);

        if (hasLanded) {
            status.set(SimulationStatus.LANDED);
            return;
        }

        // Apex occurs when vy = 0 -> timeToApex()
        OptionalDouble apexTime = m.timeToApex();
        boolean isAtApex = apexTime.isPresent() && Math.abs(t - apexTime.getAsDouble()) <= 0.02;

        if (isAtApex) {
            status.set(SimulationStatus.AT_APEX);
            return;
        }

        if (playing.get()) {
            status.set(SimulationStatus.PLAYING);
        } else if (t <= 1e-6) {
            status.set(SimulationStatus.READY);
        } else {
            status.set(SimulationStatus.PAUSED);
        }
    }

    void play() {
        OptionalDouble flightTime = model.get().timeOfFlight();
        if (flightTime.isPresent() && flightTime.getAsDouble() <= 0.0) {
            return;
        }
        if (flightTime.isPresent() && simulationTime.get() >= flightTime.getAsDouble()) {
            simulationTime.set(0.0);
        }
        previousFrameNanos = 0L;
        playing.set(true);
        timer.start();
    }

    void pause() {
        timer.stop();
        previousFrameNanos = 0L;
        playing.set(false);
    }

    /**
     * Non-destructive playback restart: rewinds display time to zero while strictly
     * preserving all student-selected parameters (speed, angle, gravity, coordinates, rate).
     */
    void restartAnimation() {
        pause();
        simulationTime.set(0.0);
    }

    /**
     * Restores factory defaults for all parameters and resets the simulation.
     */
    void restoreDefaults() {
        pause();
        restoringDefaults = true;
        launchSpeed.set(DEFAULT_LAUNCH_SPEED);
        launchAngle.set(DEFAULT_LAUNCH_ANGLE);
        gravity.set(DEFAULT_GRAVITY);
        initialX.set(0.0);
        initialY.set(0.0);
        landingY.set(0.0);
        playbackSpeed.set(1.0);
        restoringDefaults = false;
        rebuildModel();
        simulationTime.set(0.0);
    }

    /**
     * Backward-compatible delegate to {@link #restoreDefaults()}.
     */
    void reset() {
        restoreDefaults();
    }

    /** Advances display time only; the analytical model remains the source of physical state. */
    void advanceByElapsedSeconds(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException("Elapsed time must be finite and non-negative");
        }
        double boundedElapsed = Math.min(MAX_FRAME_DELTA_SECONDS, elapsedSeconds);
        double nextTime = simulationTime.get() + boundedElapsed * playbackSpeed.get();
        OptionalDouble flightTime = model.get().timeOfFlight();
        if (flightTime.isPresent() && nextTime >= flightTime.getAsDouble()) {
            simulationTime.set(flightTime.getAsDouble());
            pause();
        } else {
            simulationTime.set(nextTime);
        }
    }

    void seek(double time) {
        double bounded = Math.max(0.0, Math.min(time, timelineDuration()));
        simulationTime.set(bounded);
    }

    void seekToApex() {
        pause();
        ProjectileMotion m = model.get();
        if (m != null) {
            m.timeToApex().ifPresent(this::seek);
        }
    }

    void seekToLanding() {
        pause();
        ProjectileMotion m = model.get();
        if (m != null && m.timeOfFlight().isPresent()) {
            seek(m.timeOfFlight().getAsDouble());
        }
    }

    double timelineDuration() {
        OptionalDouble flightTime = model.get().timeOfFlight();
        return flightTime.isPresent() && flightTime.getAsDouble() > 0.0
                ? flightTime.getAsDouble() : OPEN_ENDED_DURATION;
    }

    void dispose() {
        pause();
    }

    DoubleProperty launchSpeedProperty() { return launchSpeed; }
    DoubleProperty launchAngleProperty() { return launchAngle; }
    DoubleProperty gravityProperty() { return gravity; }
    DoubleProperty initialXProperty() { return initialX; }
    DoubleProperty initialYProperty() { return initialY; }
    DoubleProperty landingYProperty() { return landingY; }
    DoubleProperty simulationTimeProperty() { return simulationTime; }
    DoubleProperty playbackSpeedProperty() { return playbackSpeed; }
    BooleanProperty playingProperty() { return playing; }
    ObjectProperty<ProjectileMotion> modelProperty() { return model; }
    ProjectileMotion model() { return model.get(); }
    ReadOnlyObjectProperty<SimulationStatus> statusProperty() { return status; }
    SimulationStatus status() { return status.get(); }
}
