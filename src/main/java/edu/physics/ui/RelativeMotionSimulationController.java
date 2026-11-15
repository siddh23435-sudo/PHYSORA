package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

/**
 * Authoritative simulation controller for two-projectile relative motion.
 *
 * <p>Maintains exactly one physical simulation clock and one authoritative scenario model.
 * Frame switching transforms the visual coordinate system without resetting, restarting,
 * or altering the simulation clock or physical states.</p>
 */
public final class RelativeMotionSimulationController {

    private final ObjectProperty<ReferenceFrame> activeFrame =
            new SimpleObjectProperty<>(ReferenceFrame.GROUND);
    private final DoubleProperty simulationTime =
            new SimpleDoubleProperty(0.0);
    private final BooleanProperty isPlaying =
            new SimpleBooleanProperty(false);
    private final DoubleProperty playbackSpeed =
            new SimpleDoubleProperty(1.0);
    private final ObjectProperty<TwoProjectilePursuitModel> model =
            new SimpleObjectProperty<>(TwoProjectilePursuitModel.flagshipCollisionPreset());

    private final AnimationTimer timer;
    private long lastNanoTime = 0;

    public RelativeMotionSimulationController() {
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanoTime == 0) {
                    lastNanoTime = now;
                    return;
                }
                double elapsedSec = (now - lastNanoTime) / 1_000_000_000.0;
                lastNanoTime = now;

                if (isPlaying.get()) {
                    advanceTime(elapsedSec * playbackSpeed.get());
                }
            }
        };

        isPlaying.addListener((obs, wasPlaying, nowPlaying) -> {
            if (nowPlaying) {
                lastNanoTime = 0;
                timer.start();
            } else {
                timer.stop();
                lastNanoTime = 0;
            }
        });
    }

    public ObjectProperty<ReferenceFrame> activeFrameProperty() {
        return activeFrame;
    }

    public ReferenceFrame activeFrame() {
        return activeFrame.get();
    }

    /**
     * Changes the visual reference frame without altering simulation time, play/pause state,
     * or underlying physical trajectories.
     */
    public void setReferenceFrame(ReferenceFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        this.activeFrame.set(frame);
    }

    public DoubleProperty simulationTimeProperty() {
        return simulationTime;
    }

    public double simulationTime() {
        return simulationTime.get();
    }

    public BooleanProperty isPlayingProperty() {
        return isPlaying;
    }

    public boolean isPlaying() {
        return isPlaying.get();
    }

    public DoubleProperty playbackSpeedProperty() {
        return playbackSpeed;
    }

    public double playbackSpeed() {
        return playbackSpeed.get();
    }

    public void setPlaybackSpeed(double speed) {
        if (speed <= 0.0 || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("playbackSpeed must be positive and finite: " + speed);
        }
        this.playbackSpeed.set(speed);
    }

    public ObjectProperty<TwoProjectilePursuitModel> modelProperty() {
        return model;
    }

    public TwoProjectilePursuitModel model() {
        return model.get();
    }

    public void setModel(TwoProjectilePursuitModel newModel) {
        Objects.requireNonNull(newModel, "model must not be null");
        pause();
        this.model.set(newModel);
        this.simulationTime.set(0.0);
    }

    public void play() {
        if (simulationTime.get() >= maxTime()) {
            simulationTime.set(0.0);
        }
        isPlaying.set(true);
    }

    public void pause() {
        isPlaying.set(false);
    }

    public void togglePlayPause() {
        if (isPlaying.get()) {
            pause();
        } else {
            play();
        }
    }

    public void reset() {
        pause();
        simulationTime.set(0.0);
    }

    public void seek(double targetTime) {
        double clamped = Math.max(0.0, Math.min(targetTime, maxTime()));
        simulationTime.set(clamped);
    }

    public void stepForward(double dt) {
        pause();
        seek(simulationTime.get() + Math.abs(dt));
    }

    public void stepBackward(double dt) {
        pause();
        seek(simulationTime.get() - Math.abs(dt));
    }

    public double maxTime() {
        TwoProjectilePursuitModel m = model.get();
        if (m == null) return 5.0;
        return m.effectiveDuration();
    }

    private void advanceTime(double deltaSec) {
        double current = simulationTime.get();
        double next = current + deltaSec;
        double max = maxTime();

        if (next >= max) {
            simulationTime.set(max);
            pause();
        } else {
            simulationTime.set(next);
        }
    }

    public void dispose() {
        pause();
        timer.stop();
    }
}

