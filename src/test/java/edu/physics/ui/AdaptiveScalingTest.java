package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveScalingTest {

    private static final double CANVAS_WIDTH = 800.0;
    private static final double CANVAS_HEIGHT = 520.0;

    @Test
    void lowSpeedTrajectoryYieldsLegibleScaleAndLargeScreenRepresentation() {
        // v0 = 1.79 m/s, theta = 45 deg, g = 9.81 m/s^2 -> Range ~ 0.326 m, Apex ~ 0.081 m
        ProjectileMotion model = new ProjectileMotion(1.79, 45.0, 9.81, 0.0, 0.0, 0.0);
        OptionalDouble flightTime = model.timeOfFlight();
        assertTrue(flightTime.isPresent());
        double tf = flightTime.getAsDouble();

        int samples = 120;
        double[] sampledX = new double[samples + 1];
        double[] sampledY = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State s = model.stateAt(tf * i / samples);
            sampledX[i] = s.x();
            sampledY[i] = s.y();
        }

        TrajectoryViewport vp = TrajectoryViewport.compute(
                model, sampledX, sampledY, model.stateAt(0.0), CANVAS_WIDTH, CANVAS_HEIGHT);

        // Verify physical bounds are faithfully captured
        assertEquals(0.0, vp.minX(), 1e-6);
        assertEquals(model.horizontalRange().orElseThrow(), vp.maxX(), 1e-3);
        assertEquals(0.0, vp.minY(), 1e-6);
        assertEquals(model.maximumHeight().orElseThrow(), vp.maxY(), 1e-3);

        // Scale should be large (adaptive) rather than clamped to 2.0m
        assertTrue(vp.scale() > 1500.0, "Scale should be > 1500 px/m for ~0.33m trajectory, actual: " + vp.scale());

        // Screen width of trajectory should occupy > 500 pixels across the 800px canvas
        double screenStartX = vp.toScreenX(sampledX[0]);
        double screenEndX = vp.toScreenX(sampledX[samples]);
        double screenWidth = Math.abs(screenEndX - screenStartX);
        assertTrue(screenWidth > 500.0, "Trajectory screen width should be > 500 px, actual: " + screenWidth);
        assertTrue(screenStartX >= vp.left());
        assertTrue(screenEndX <= vp.right());

        // Screen height of trajectory should be substantial (> 120 px)
        double screenGroundY = vp.toScreenY(0.0);
        double screenApexY = vp.toScreenY(model.maximumHeight().orElseThrow());
        double screenHeight = screenGroundY - screenApexY;
        assertTrue(screenHeight > 120.0, "Trajectory screen height should be > 120 px, actual: " + screenHeight);
        assertTrue(screenApexY >= vp.top());
        assertTrue(screenGroundY <= vp.bottom());
    }

    @Test
    void highSpeedTrajectoryFitsCleanlyWithinMargins() {
        // v0 = 80 m/s, theta = 45 deg, g = 9.81 m/s^2 -> Range ~ 652 m, Apex ~ 163 m
        ProjectileMotion model = new ProjectileMotion(80.0, 45.0, 9.81, 0.0, 0.0, 0.0);
        double tf = model.timeOfFlight().orElseThrow();

        int samples = 120;
        double[] sampledX = new double[samples + 1];
        double[] sampledY = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State s = model.stateAt(tf * i / samples);
            sampledX[i] = s.x();
            sampledY[i] = s.y();
        }

        TrajectoryViewport vp = TrajectoryViewport.compute(
                model, sampledX, sampledY, model.stateAt(0.0), CANVAS_WIDTH, CANVAS_HEIGHT);

        assertTrue(vp.scale() < 2.0, "Scale for 650m trajectory should be < 2.0 px/m, actual: " + vp.scale());

        // All sampled points must be within [left, right] and [top, bottom]
        for (int i = 0; i <= samples; i++) {
            double sx = vp.toScreenX(sampledX[i]);
            double sy = vp.toScreenY(sampledY[i]);
            assertTrue(sx >= vp.left() - 1e-6 && sx <= vp.right() + 1e-6,
                    "sx out of bounds at " + i + ": " + sx);
            assertTrue(sy >= vp.top() - 1e-6 && sy <= vp.bottom() + 1e-6,
                    "sy out of bounds at " + i + ": " + sy);
        }
    }

    @Test
    void isotropicAspectPreservedAcrossAllDimensions() {
        ProjectileMotion model = new ProjectileMotion(20.0, 30.0, 9.81, 0.0, 0.0, 0.0);
        TrajectoryViewport vp = TrajectoryViewport.compute(
                model, new double[]{0.0, 35.3}, new double[]{0.0, 5.1}, model.stateAt(0.0), 1024, 600);

        // 1 meter in X and 1 meter in Y must convert to identical pixel displacements
        double deltaPxX = vp.toScreenX(10.0) - vp.toScreenX(9.0);
        double deltaPxY = vp.toScreenY(9.0) - vp.toScreenY(10.0); // downward-positive screen
        assertEquals(deltaPxX, deltaPxY, 1e-9, "Scale must be strictly isotropic");
    }

    @Test
    void degenerateZeroSpanCasesDoNotDivideByZeroOrProduceNaN() {
        // Pure vertical launch: dx = 0
        ProjectileMotion vertical = new ProjectileMotion(10.0, 90.0, 9.81, 0.0, 0.0, 0.0);
        TrajectoryViewport vpVert = TrajectoryViewport.compute(
                vertical, new double[]{0.0, 0.0}, new double[]{0.0, 5.1}, vertical.stateAt(0.0), 800, 500);

        assertTrue(Double.isFinite(vpVert.scale()) && vpVert.scale() > 0.0);
        assertTrue(Double.isFinite(vpVert.originX()));
        assertTrue(Double.isFinite(vpVert.groundScreenY()));
        assertEquals(vpVert.toScreenX(0.0), (vpVert.left() + vpVert.right()) / 2.0, 1.0,
                "Vertical launch should be centered horizontally");

        // Zero-velocity launch: v0 = 0 -> dx = 0, dy = 0
        ProjectileMotion stationary = new ProjectileMotion(0.0, 45.0, 9.81, 0.0, 0.0, 0.0);
        TrajectoryViewport vpStat = TrajectoryViewport.compute(
                stationary, new double[]{0.0}, new double[]{0.0}, stationary.stateAt(0.0), 800, 500);

        assertTrue(Double.isFinite(vpStat.scale()) && vpStat.scale() > 0.0);
        assertTrue(Double.isFinite(vpStat.originX()));
        assertTrue(Double.isFinite(vpStat.groundScreenY()));
    }

    @Test
    void elevatedCliffLaunchAndNegativeLandingSurfaceAreProperlyBounded() {
        // Launch from y = 25m, landing at y = -15m
        ProjectileMotion cliff = new ProjectileMotion(20.0, 30.0, 9.81, 0.0, 25.0, -15.0);
        double tf = cliff.timeOfFlight().orElseThrow();

        int samples = 60;
        double[] sx = new double[samples + 1];
        double[] sy = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State st = cliff.stateAt(tf * i / samples);
            sx[i] = st.x();
            sy[i] = st.y();
        }

        TrajectoryViewport vp = TrajectoryViewport.compute(cliff, sx, sy, cliff.stateAt(0.0), 800, 520);

        double screenLandingY = vp.toScreenY(-15.0);
        double screenGroundY = vp.toScreenY(0.0);
        double screenLaunchY = vp.toScreenY(25.0);

        // In screen coords: top is smaller Y, bottom is larger Y
        assertTrue(screenLaunchY < screenGroundY, "Launch at y=25m should be higher (smaller screen Y) than ground");
        assertTrue(screenGroundY < screenLandingY, "Ground y=0m should be higher than landing at y=-15m");
        assertTrue(screenLandingY <= vp.bottom(), "Landing level must not be clipped below bottom");
        assertTrue(screenLaunchY >= vp.top(), "Launch level must not be clipped above top");
    }

    @Test
    void niceDecadeStepsFollowStandard125Sequence() {
        assertEquals(0.05, TrajectoryViewport.calculateNiceStep(0.33, 5), 1e-9);
        assertEquals(0.2, TrajectoryViewport.calculateNiceStep(0.8, 5), 1e-9);
        assertEquals(0.2, TrajectoryViewport.calculateNiceStep(1.2, 5), 1e-9);
        assertEquals(1.0, TrajectoryViewport.calculateNiceStep(5.0, 5), 1e-9);
        assertEquals(2.0, TrajectoryViewport.calculateNiceStep(12.0, 5), 1e-9);
        assertEquals(5.0, TrajectoryViewport.calculateNiceStep(24.0, 5), 1e-9);
        assertEquals(10.0, TrajectoryViewport.calculateNiceStep(45.0, 5), 1e-9);
        assertEquals(20.0, TrajectoryViewport.calculateNiceStep(110.0, 5), 1e-9);
        assertEquals(100.0, TrajectoryViewport.calculateNiceStep(650.0, 5), 1e-9);

        // Formatting verification
        assertEquals("0", TrajectoryViewport.formatTick(0.0, 0.05));
        assertEquals("0.05", TrajectoryViewport.formatTick(0.05, 0.05));
        assertEquals("0.10", TrajectoryViewport.formatTick(0.10, 0.05));
        assertEquals("2.5", TrajectoryViewport.formatTick(2.5, 0.5));
        assertEquals("10", TrajectoryViewport.formatTick(10.0, 5.0));
    }

    @Test
    void badgeCollisionDetectionStaggersOverlappingApexAndLandingLabels() {
        // Projectile launched towards an elevated landing surface very close to apex height
        // v0 = 10 m/s, theta = 45 deg -> apex height ~ 2.548 m. Landing surface at 2.540 m.
        // Apex and landing are separated by only ~0.29 m horizontally, which at ~115 px/m is ~33 px (< 48 px).
        ProjectileMotion nearApexLanding = new ProjectileMotion(10.0, 45.0, 9.81, 0.0, 0.0, 2.54);
        double tf = nearApexLanding.timeOfFlight().orElseThrow();
        int samples = 50;
        double[] sx = new double[samples + 1];
        double[] sy = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State s = nearApexLanding.stateAt(tf * i / samples);
            sx[i] = s.x();
            sy[i] = s.y();
        }

        TrajectoryViewport vp = TrajectoryViewport.compute(
                nearApexLanding, sx, sy, nearApexLanding.stateAt(0.0), CANVAS_WIDTH, CANVAS_HEIGHT);
        assertTrue(vp.apexBadge().isPresent());
        assertTrue(vp.landingBadge().isPresent());

        // Staggered placement must be chosen instead of overlapping STANDARD
        TrajectoryViewport.EventBadge aBadge = vp.apexBadge().get();
        TrajectoryViewport.EventBadge lBadge = vp.landingBadge().get();
        assertEquals(TrajectoryViewport.BadgePlacement.STAGGERED_UP, aBadge.placement(),
                "Apex badge should be staggered upward when crowded");
        assertEquals(TrajectoryViewport.BadgePlacement.STAGGERED_DOWN, lBadge.placement(),
                "Landing badge should be staggered downward when crowded");
    }
}
