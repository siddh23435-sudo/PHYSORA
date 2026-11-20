package edu.physics.problem;

import edu.physics.ui.ExperimentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure domain logic for comparing two discrete projectile experiments (Experiment A vs Experiment B).
 *
 * <p>Produces metric differences and constructive physics insights, specifically handling
 * JEE-relevant edge cases such as complementary angles on level vs elevated terrain.
 */
public final class ExperimentComparison {

    public record MetricRow(
            String metricName,
            String unit,
            String valueA,
            String valueB,
            String delta,
            String physicalInsight) {
    }

    public record ComparisonReport(
            ExperimentSnapshot experimentA,
            ExperimentSnapshot experimentB,
            List<MetricRow> rows,
            String summaryInsight) {
    }

    public static ComparisonReport compare(ExperimentSnapshot a, ExperimentSnapshot b) {
        Objects.requireNonNull(a, "Experiment A is required");
        Objects.requireNonNull(b, "Experiment B is required");

        List<MetricRow> rows = new ArrayList<>();

        // 1. Initial Parameters
        rows.add(compareDoubles("Launch Speed (v₀)", "m/s", a.initialSpeed(), b.initialSpeed(),
                "Higher launch speed provides greater kinetic energy, increasing range and height."));
        rows.add(compareDoubles("Launch Angle (θ)", "°", a.launchAngleDegrees(), b.launchAngleDegrees(),
                "Steeper angles favor vertical climb over horizontal velocity."));
        rows.add(compareDoubles("Gravity (g)", "m/s²", a.gravity(), b.gravity(),
                "Weaker gravity prolongs airborne duration and magnifies reach."));
        rows.add(compareDoubles("Launch Elevation (y₀)", "m", a.initialY(), b.initialY(),
                "Higher platforms extend the post-apex descent duration."));
        rows.add(compareDoubles("Landing Surface (y_land)", "m", a.landingY(), b.landingY(),
                "Lower landing planes allow longer vertical fall and increased range."));

        // 2. Calculated Outcomes
        rows.add(compareOptional("Maximum Height (H_max)", "m", a.maximumHeight(), b.maximumHeight(),
                "H_max = y₀ + vᵧ₀² / (2g); scales quadratically with initial vertical velocity."));
        rows.add(compareOptional("Flight Time (T)", "s", a.timeOfFlight(), b.timeOfFlight(),
                "Determined by vertical launch speed, gravity, and net vertical drop."));
        rows.add(compareOptional("Horizontal Range (R)", "m", a.horizontalRange(), b.horizontalRange(),
                "R = vₓ₀ · T; product of invariant horizontal speed and airborne duration."));

        // 3. High-level Physics Summary Insight
        String summaryInsight = buildSummaryInsight(a, b);

        return new ComparisonReport(a, b, rows, summaryInsight);
    }

    private static MetricRow compareDoubles(String name, String unit, double valA, double valB, String insight) {
        double diff = valB - valA;
        String deltaStr = Math.abs(diff) < 1e-4
                ? "Identical"
                : String.format(Locale.ROOT, "%+.2f %s", diff, unit);
        return new MetricRow(name, unit,
                String.format(Locale.ROOT, "%.2f %s", valA, unit),
                String.format(Locale.ROOT, "%.2f %s", valB, unit),
                deltaStr, insight);
    }

    private static MetricRow compareOptional(String name, String unit, OptionalDouble optA, OptionalDouble optB, String insight) {
        String strA = optA.isPresent() ? String.format(Locale.ROOT, "%.2f %s", optA.getAsDouble(), unit) : "N/A";
        String strB = optB.isPresent() ? String.format(Locale.ROOT, "%.2f %s", optB.getAsDouble(), unit) : "N/A";
        String deltaStr;
        if (optA.isPresent() && optB.isPresent()) {
            double diff = optB.getAsDouble() - optA.getAsDouble();
            deltaStr = Math.abs(diff) < 1e-4 ? "Identical" : String.format(Locale.ROOT, "%+.2f %s", diff, unit);
        } else {
            deltaStr = "N/A";
        }
        return new MetricRow(name, unit, strA, strB, deltaStr, insight);
    }

    private static String buildSummaryInsight(ExperimentSnapshot a, ExperimentSnapshot b) {
        StringBuilder sb = new StringBuilder();
        double sumAngle = a.launchAngleDegrees() + b.launchAngleDegrees();
        boolean complementary = Math.abs(sumAngle - 90.0) < 1e-2;
        boolean equalSpeed = Math.abs(a.initialSpeed() - b.initialSpeed()) < 1e-2;
        boolean equalGravity = Math.abs(a.gravity() - b.gravity()) < 1e-2;
        boolean levelA = Math.abs(a.initialY() - a.landingY()) < 1e-2;
        boolean levelB = Math.abs(b.initialY() - b.landingY()) < 1e-2;

        if (complementary && equalSpeed && equalGravity) {
            if (levelA && levelB) {
                sb.append("COMPLEMENTARY ANGLES (θA + θB = 90°) ON LEVEL GROUND:\n")
                  .append("Both experiments launch with equal speed and equal launch/landing elevations. ")
                  .append("Because sin(2·θ) = sin(2·(90° - θ)), horizontal ranges are IDENTICAL. ")
                  .append("However, the steeper angle (").append(String.format(Locale.ROOT, "%.1f°", Math.max(a.launchAngleDegrees(), b.launchAngleDegrees())))
                  .append(") achieves higher maximum elevation and longer flight time.");
            } else {
                sb.append("COMPLEMENTARY ANGLES (θA + θB = 90°) WITH ELEVATION OFFSET:\n")
                  .append("Although the launch angles sum to 90°, the launch and landing elevations are NOT equal! ")
                  .append("JEE Caveat: The textbook identity of equal ranges strictly requires level ground (y0 = y_land). ")
                  .append("When elevation offsets exist, the sin(2θ) symmetry breaks and the ranges diverge.");
            }
        } else {
            sb.append("EXPERIMENT A vs EXPERIMENT B COMPARISON:\n")
              .append("The two experiments highlight trade-offs in projectile mechanics. ")
              .append("Observe the shared trajectory overlay to see how modifying direction and speed alters peak elevation, hang time, and overall horizontal range.");
        }
        return sb.toString();
    }
}

