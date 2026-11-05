package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.OptionalDouble;

/** Shares one analytical model and one simulation clock between all bench views. */
final class ProjectileSimulationController {
    static final double OPEN_ENDED_DURATION = 8.0;
    private static final double MAX_FRAME_DELTA_SECONDS = 0.1;

    private final DoubleProperty launchSpeed = new SimpleDoubleProperty(20.0);
    private final DoubleProperty launchAngle = new SimpleDoubleProperty(45.0);
    private final DoubleProperty gravity = new SimpleDoubleProperty(9.81);
    private final DoubleProperty simulationTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty playbackSpeed = new SimpleDoubleProperty(1.0);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final ObjectProperty<ProjectileMotion> model = new SimpleObjectProperty<>();

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
        launchSpeed.addListener((obs, oldValue, newValue) -> rebuildModel());
        launchAngle.addListener((obs, oldValue, newValue) -> rebuildModel());
        gravity.addListener((obs, oldValue, newValue) -> rebuildModel());
        rebuildModel();
    }

    private void rebuildModel() {
        pause();
        model.set(new ProjectileMotion(launchSpeed.get(), launchAngle.get(), gravity.get(),
                0.0, 0.0, 0.0));
        simulationTime.set(0.0);
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

    void reset() {
        pause();
        simulationTime.set(0.0);
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
    DoubleProperty simulationTimeProperty() { return simulationTime; }
    DoubleProperty playbackSpeedProperty() { return playbackSpeed; }
    BooleanProperty playingProperty() { return playing; }
    ObjectProperty<ProjectileMotion> modelProperty() { return model; }
    ProjectileMotion model() { return model.get(); }
}
