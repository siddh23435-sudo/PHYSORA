package edu.physics.problem;

import edu.physics.projectile.ProjectileMotion;
import edu.physics.ui.ExperimentSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExperimentComparison Domain Logic Tests")
class ExperimentComparisonTest {

    private ExperimentSnapshot createSnapshot(String label, double v0, double theta, double g, double y0, double yLand) {
        ProjectileMotion m = new ProjectileMotion(v0, theta, g, 0.0, y0, yLand);
        return new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", label,
                v0, theta, g, 0.0, y0, yLand, 0.0, "READY",
                m.maximumHeight(), m.horizontalRange(), m.timeOfFlight(), m.timeToApex(),
                0.0, 0.0, m.initialVx(), m.initialVy());
    }

    @Test
    @DisplayName("Null arguments are strictly rejected")
    void nullArgumentsRejected() {
        ExperimentSnapshot a = createSnapshot("A", 20.0, 45.0, 9.81, 0.0, 0.0);
        assertThrows(NullPointerException.class, () -> ExperimentComparison.compare(null, a));
        assertThrows(NullPointerException.class, () -> ExperimentComparison.compare(a, null));
    }

    @Test
    @DisplayName("Standard comparison produces complete metric rows and non-empty insights")
    void standardComparisonProducesCompleteRows() {
        ExperimentSnapshot a = createSnapshot("Exp A", 20.0, 30.0, 9.81, 0.0, 0.0);
        ExperimentSnapshot b = createSnapshot("Exp B", 25.0, 45.0, 9.81, 0.0, 0.0);

        ExperimentComparison.ComparisonReport report = ExperimentComparison.compare(a, b);

        assertSame(a, report.experimentA());
        assertSame(b, report.experimentB());
        assertEquals(8, report.rows().size(), "Must generate 8 metric comparison rows (5 input, 3 calculated)");

        for (ExperimentComparison.MetricRow row : report.rows()) {
            assertNotNull(row.metricName());
            assertNotNull(row.unit());
            assertNotNull(row.valueA());
            assertNotNull(row.valueB());
            assertNotNull(row.delta());
            assertNotNull(row.physicalInsight());
            assertFalse(row.physicalInsight().isBlank());
        }

        assertNotNull(report.summaryInsight());
        assertFalse(report.summaryInsight().isBlank());
    }

    @Test
    @DisplayName("Complementary angles on level ground yield identical range and specific symmetry insight")
    void complementaryAnglesOnLevelGroundYieldIdenticalRange() {
        // 30° and 60° on level ground (y0 = y_land = 0)
        ExperimentSnapshot a = createSnapshot("Complementary A (30°)", 25.0, 30.0, 9.81, 0.0, 0.0);
        ExperimentSnapshot b = createSnapshot("Complementary B (60°)", 25.0, 60.0, 9.81, 0.0, 0.0);

        ExperimentComparison.ComparisonReport report = ExperimentComparison.compare(a, b);

        // Find the Horizontal Range row
        ExperimentComparison.MetricRow rangeRow = report.rows().stream()
                .filter(r -> r.metricName().contains("Horizontal Range"))
                .findFirst()
                .orElseThrow();

        assertEquals("Identical", rangeRow.delta(), "Horizontal ranges must be identical for complementary angles on flat ground");
        assertEquals(rangeRow.valueA(), rangeRow.valueB());

        // Find the Max Height row
        ExperimentComparison.MetricRow heightRow = report.rows().stream()
                .filter(r -> r.metricName().contains("Maximum Height"))
                .findFirst()
                .orElseThrow();
        assertTrue(heightRow.delta().startsWith("+"), "60° trajectory reaches higher apex than 30°");

        // Verify summary insight contains academic explanation
        String summary = report.summaryInsight();
        assertTrue(summary.contains("COMPLEMENTARY ANGLES (θA + θB = 90°) ON LEVEL GROUND"));
        assertTrue(summary.contains("sin(2·θ) = sin(2·(90° - θ))"));
        assertTrue(summary.contains("IDENTICAL"));
    }

    @Test
    @DisplayName("Complementary angles on elevated platform break range symmetry with explicit JEE caveat")
    void complementaryAnglesWithElevationOffsetBreaksSymmetry() {
        // 30° and 60° launched from a 10m cliff (y0 = 10, y_land = 0)
        ExperimentSnapshot a = createSnapshot("Elevated A (30°)", 25.0, 30.0, 9.81, 10.0, 0.0);
        ExperimentSnapshot b = createSnapshot("Elevated B (60°)", 25.0, 60.0, 9.81, 10.0, 0.0);

        ExperimentComparison.ComparisonReport report = ExperimentComparison.compare(a, b);

        ExperimentComparison.MetricRow rangeRow = report.rows().stream()
                .filter(r -> r.metricName().contains("Horizontal Range"))
                .findFirst()
                .orElseThrow();

        assertNotEquals("Identical", rangeRow.delta(), "Ranges must diverge when launch elevation != landing elevation");

        String summary = report.summaryInsight();
        assertTrue(summary.contains("COMPLEMENTARY ANGLES (θA + θB = 90°) WITH ELEVATION OFFSET"));
        assertTrue(summary.contains("JEE Caveat"));
        assertTrue(summary.contains("strictly requires level ground"));
        assertTrue(summary.contains("symmetry breaks"));
    }

    @Test
    @DisplayName("Handles zero-gravity or unbounded calculations gracefully with N/A rows")
    void handlesZeroGravityOrUnboundedCalculationsGracefully() {
        // Zero gravity: H_max is empty (unbounded), flight time is empty
        ExperimentSnapshot a = createSnapshot("Zero g", 20.0, 45.0, 0.0, 0.0, 0.0);
        ExperimentSnapshot b = createSnapshot("Normal g", 20.0, 45.0, 9.81, 0.0, 0.0);

        ExperimentComparison.ComparisonReport report = ExperimentComparison.compare(a, b);

        ExperimentComparison.MetricRow heightRow = report.rows().stream()
                .filter(r -> r.metricName().contains("Maximum Height"))
                .findFirst()
                .orElseThrow();

        assertEquals("N/A", heightRow.valueA());
        assertNotEquals("N/A", heightRow.valueB());
        assertEquals("N/A", heightRow.delta());
    }

    @Test
    @DisplayName("Snapshot immutability is maintained after comparison")
    void snapshotsRemainImmutable() {
        ExperimentSnapshot a = createSnapshot("Exp A", 20.0, 30.0, 9.81, 0.0, 0.0);
        ExperimentSnapshot b = createSnapshot("Exp B", 25.0, 45.0, 9.81, 0.0, 0.0);

        double speedA = a.initialSpeed();
        double angleB = b.launchAngleDegrees();

        ExperimentComparison.compare(a, b);

        assertEquals(speedA, a.initialSpeed());
        assertEquals(angleB, b.launchAngleDegrees());
    }
}

