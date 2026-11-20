package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable snapshot of an active physics experiment and its deterministic solver results.
 * Serves as the ground-truth context passed to AI teaching companions to ensure numerical integrity.
 */
public record ExperimentSnapshot(
        String chapterId,
        String chapterTitle,
        String modelTitle,
        double initialSpeed,
        double launchAngleDegrees,
        double gravity,
        double initialX,
        double initialY,
        double landingY,
        double simulationTime,
        String simulationStatus,
        OptionalDouble maximumHeight,
        OptionalDouble horizontalRange,
        OptionalDouble timeOfFlight,
        OptionalDouble timeToApex,
        double currentX,
        double currentY,
        double currentVx,
        double currentVy) {

    public ExperimentSnapshot {
        Objects.requireNonNull(chapterId, "chapterId is required");
        Objects.requireNonNull(chapterTitle, "chapterTitle is required");
        Objects.requireNonNull(modelTitle, "modelTitle is required");
        Objects.requireNonNull(simulationStatus, "simulationStatus is required");
    }

    /**
     * Constructs an immutable snapshot from the live controller state and its analytical model.
     */
    public static ExperimentSnapshot fromController(
            ProjectileSimulationController controller, String chapterTitle, String modelTitle) {
        ProjectileMotion model = controller.model();
        double t = controller.simulationTimeProperty().get();
        ProjectileMotion.State state = model.stateAt(t);

        return new ExperimentSnapshot(
                "kinematics",
                chapterTitle,
                modelTitle,
                controller.launchSpeedProperty().get(),
                controller.launchAngleProperty().get(),
                controller.gravityProperty().get(),
                controller.initialXProperty().get(),
                controller.initialYProperty().get(),
                controller.landingYProperty().get(),
                t,
                controller.status().label(),
                model.maximumHeight(),
                model.horizontalRange(),
                model.timeOfFlight(),
                model.timeToApex(),
                state.x(),
                state.y(),
                state.vx(),
                state.vy());
    }

    /**
     * Formats a concise one-line summary for student UI display.
     */
    public String formatSummary() {
        return String.format(Locale.ROOT,
                "v0 = %.2f m/s, θ = %.1f°, g = %.2f m/s², launch (%.1f, %.1f) m, landing y = %.1f m",
                initialSpeed, launchAngleDegrees, gravity, initialX, initialY, landingY);
    }

    /**
     * Formats solver results in SI units or notes unavailability.
     */
    public String formatSolverResults() {
        String h = maximumHeight.isPresent() ? String.format(Locale.ROOT, "%.2f m", maximumHeight.getAsDouble()) : "Unbounded";
        String r = horizontalRange.isPresent() ? String.format(Locale.ROOT, "%.2f m", horizontalRange.getAsDouble()) : "No landing";
        String tof = timeOfFlight.isPresent() ? String.format(Locale.ROOT, "%.2f s", timeOfFlight.getAsDouble()) : "No landing";
        String apex = timeToApex.isPresent() ? String.format(Locale.ROOT, "%.2f s", timeToApex.getAsDouble()) : "N/A";
        return String.format("H_max = %s · Range = %s · Flight Time = %s · Time to Apex = %s", h, r, tof, apex);
    }

    /**
     * Formats the structured context injected into the AI teaching companion prompt.
     * Guaranteed grounded in deterministic physics model values.
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ACTIVE EXPERIMENT CONTEXT]\n");
        sb.append(String.format("Chapter: %s · Model: %s\n", chapterTitle, modelTitle));
        sb.append(String.format(Locale.ROOT,
                "Initial Parameters: projected at %.2f m/s at angle %.2f degrees, gravity g = %.2f m/s^2, initial position (%.2f, %.2f) m, landing elevation y = %.2f m.\n",
                initialSpeed, launchAngleDegrees, gravity, initialX, initialY, landingY));
        sb.append(String.format(Locale.ROOT,
                "Active Clock & State: simulation time t = %.2f s, status = %s, current position = (%.2f, %.2f) m, current velocity = (%.2f, %.2f) m/s.\n",
                simulationTime, simulationStatus, currentX, currentY, currentVx, currentVy));
        sb.append("Deterministic Physics Solver Ground Truth:\n");
        sb.append(String.format(Locale.ROOT, "- Maximum Height: %s\n",
                maximumHeight.isPresent() ? String.format(Locale.ROOT, "%.2f m", maximumHeight.getAsDouble()) : "Unbounded (infinite / zero downward gravity)"));
        sb.append(String.format(Locale.ROOT, "- Horizontal Range: %s\n",
                horizontalRange.isPresent() ? String.format(Locale.ROOT, "%.2f m", horizontalRange.getAsDouble()) : "No landing surface intersection"));
        sb.append(String.format(Locale.ROOT, "- Total Flight Time: %s\n",
                timeOfFlight.isPresent() ? String.format(Locale.ROOT, "%.2f s", timeOfFlight.getAsDouble()) : "No landing surface intersection"));
        sb.append(String.format(Locale.ROOT, "- Time to Apex: %s\n",
                timeToApex.isPresent() ? String.format(Locale.ROOT, "%.2f s", timeToApex.getAsDouble()) : "N/A"));
        sb.append("Constraint Notice: Air resistance is neglected, gravity is uniform downward, and coordinates are standard Cartesian (+x right, +y up). Do not fabricate different numerical values.");
        return sb.toString();
    }
}

