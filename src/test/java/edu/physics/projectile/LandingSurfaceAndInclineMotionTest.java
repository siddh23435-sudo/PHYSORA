package edu.physics.projectile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LandingSurfaceAndInclineMotionTest {
    private static final double TOL = 1e-9;

    @Test
    void solvesAnalyticalRisingInclinedPlaneReferenceProblem() {
        // u = 25 m/s, 60 deg from horizontal, rising 30 deg incline from origin, g = 10 m/s^2
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);
        ProjectileMotion model = new ProjectileMotion(25.0, 60.0, 10.0, 0.0, 0.0, incline);

        double expectedFlightTime = 5.0 / Math.sqrt(3.0);
        double expectedRangeAlongIncline = 125.0 / 3.0;
        double expectedLandingX = 62.5 / Math.sqrt(3.0);
        double expectedLandingY = 62.5 / 3.0;
        double expectedMaxHeight = 23.4375;
        double expectedMaxPerp = 156.25 / (10.0 * Math.sqrt(3.0));

        assertEquals(expectedFlightTime, model.timeOfFlight().orElseThrow(), TOL);
        assertEquals(expectedRangeAlongIncline, model.rangeAlongSurface().orElseThrow(), TOL);
        assertEquals(expectedRangeAlongIncline, model.signedDisplacementAlongSurface().orElseThrow(), TOL);
        assertEquals(expectedMaxHeight, model.maximumHeight().orElseThrow(), TOL);
        assertEquals(expectedMaxHeight, model.maximumHeightInFlight().orElseThrow(), TOL);
        assertEquals(expectedMaxPerp, model.maxPerpendicularDistance().orElseThrow(), TOL);
        assertEquals(expectedMaxPerp, model.maxSignedPerpendicularDistance().orElseThrow(), TOL);

        ProjectileMotion.State landing = model.landingState().orElseThrow();
        assertEquals(expectedLandingX, landing.x(), TOL);
        assertEquals(expectedLandingY, landing.y(), TOL);
        assertEquals(0.0, incline.signedPerpendicularDistance(landing.x(), landing.y()), TOL);

        ProjectileMotion fromComponents = ProjectileMotion.fromVelocityComponents(
                25.0 * Math.cos(Math.toRadians(60.0)),
                25.0 * Math.sin(Math.toRadians(60.0)),
                10.0, 0.0, 0.0, incline);
        assertEquals(expectedFlightTime, fromComponents.timeOfFlight().orElseThrow(), TOL);
        assertEquals(expectedRangeAlongIncline, fromComponents.rangeAlongSurface().orElseThrow(), TOL);
        assertEquals(expectedMaxPerp, fromComponents.maxPerpendicularDistance().orElseThrow(), TOL);
    }

    @Test
    void horizontalLaunchStartingBelowLandingLevelSelectsDescendingIntersection() {
        // Launch at (0, 0) upward toward elevated flat surface y = 10 with vy0 = 10*sqrt(3), g = 10
        ProjectileMotion model = new ProjectileMotion(20.0, 60.0, 10.0, 0.0, 0.0, 10.0);
        double sqrt3 = Math.sqrt(3.0);
        double expectedAscendingTime = sqrt3 - 1.0;
        double expectedDescendingTime = sqrt3 + 1.0;

        assertEquals(10.0, model.stateAt(expectedAscendingTime).y(), TOL);
        assertTrue(model.stateAt(expectedAscendingTime).vy() > 0.0);

        double flightTime = model.timeOfFlight().orElseThrow();
        assertEquals(expectedDescendingTime, flightTime, TOL);
        assertEquals(10.0 * expectedDescendingTime, model.horizontalRange().orElseThrow(), TOL);
        assertEquals(10.0, model.landingState().orElseThrow().y(), TOL);
        assertEquals(-10.0, model.landingState().orElseThrow().vy(), TOL);
        assertEquals(5.0, model.maxPerpendicularDistance().orElseThrow(), TOL);
    }

    @Test
    void solvesFallingInclineAndMatchesZeroDegreeInclineWithHorizontalPlane() {
        LandingSurface falling = LandingSurface.incline(0.0, 0.0, -30.0);
        ProjectileMotion model = new ProjectileMotion(20.0, 15.0, 10.0, 0.0, 0.0, falling);

        double alphaRadians = Math.toRadians(45.0);
        double betaRadians = Math.toRadians(-30.0);
        double expectedTime = 2.0 * 20.0 * Math.sin(alphaRadians) / (10.0 * Math.cos(betaRadians));
        double expectedRange = Math.abs(model.initialVx() * expectedTime / Math.cos(betaRadians));

        assertEquals(expectedTime, model.timeOfFlight().orElseThrow(), TOL);
        assertEquals(expectedRange, model.rangeAlongSurface().orElseThrow(), TOL);
        ProjectileMotion.State landing = model.landingState().orElseThrow();
        assertEquals(falling.heightAt(landing.x()), landing.y(), TOL);

        ProjectileMotion flat = new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 0.0, 0.0);
        ProjectileMotion zeroSlopeIncline = new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 0.0,
                LandingSurface.incline(12.0, 0.0, 0.0));
        assertEquals(flat.timeOfFlight().orElseThrow(), zeroSlopeIncline.timeOfFlight().orElseThrow(), TOL);
        assertEquals(flat.horizontalRange().orElseThrow(), zeroSlopeIncline.rangeAlongSurface().orElseThrow(), TOL);
        assertEquals(flat.maximumHeight().orElseThrow(), zeroSlopeIncline.maxPerpendicularDistance().orElseThrow(), TOL);
    }

    @Test
    void handlesArbitrarySurfaceAnchorAndElevatedLaunchMovingTowardIncline() {
        LandingSurface incline = LandingSurface.incline(5.0, 2.0, 25.0);

        // On-surface shifted anchor launch
        double onSurfaceX = 9.0;
        double onSurfaceY = incline.heightAt(onSurfaceX);
        ProjectileMotion onAnchorPlane = new ProjectileMotion(18.0, 55.0, 9.81, onSurfaceX, onSurfaceY, incline);
        double tOn = onAnchorPlane.timeOfFlight().orElseThrow();
        ProjectileMotion.State landOn = onAnchorPlane.landingState().orElseThrow();
        assertTrue(tOn > 0.0);
        assertEquals(incline.heightAt(landOn.x()), landOn.y(), 1e-8);
        assertEquals(incline.signedCoordinateFromAnchor(landOn.x()),
                onAnchorPlane.signedCoordinateAlongSurfaceFromAnchor().orElseThrow(), 1e-8);

        // Elevated launch strictly above incline and initially moving toward it (v_perp0 < 0)
        ProjectileMotion aboveToward = ProjectileMotion.fromVelocityComponents(
                6.0, -4.0, 10.0, 5.0, 10.0, incline);
        double flight = aboveToward.timeOfFlight().orElseThrow();
        assertTrue(flight > 0.0);
        ProjectileMotion.State impact = aboveToward.landingState().orElseThrow();
        assertEquals(incline.heightAt(impact.x()), impact.y(), 1e-8);
        assertEquals(incline.signedPerpendicularDistance(5.0, 10.0),
                aboveToward.maxPerpendicularDistance().orElseThrow(), TOL);

        // Extreme downward speed retains Citardauq numerical precision above an incline
        LandingSurface gentle = LandingSurface.incline(0.0, 0.0, 10.0);
        ProjectileMotion fastDown = ProjectileMotion.fromVelocityComponents(0.0, -1e8, 1.0, 0.0, 1.0, gentle);
        double fastTime = fastDown.timeOfFlight().orElseThrow();
        assertEquals(1e-8, fastTime, 1e-15);
        assertEquals(0.0, gentle.heightAt(fastDown.stateAt(fastTime).x()) - fastDown.stateAt(fastTime).y(), 1e-8);
    }

    @Test
    void distinguishesNegativeHorizontalVelocityBackwardLanding() {
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 20.0);
        ProjectileMotion backward = new ProjectileMotion(20.0, 135.0, 10.0, 0.0, 0.0, incline);

        double flight = backward.timeOfFlight().orElseThrow();
        assertTrue(flight > 0.0);
        ProjectileMotion.State landing = backward.landingState().orElseThrow();
        assertTrue(landing.x() < 0.0);
        assertTrue(landing.y() < 0.0);
        assertEquals(incline.heightAt(landing.x()), landing.y(), 1e-8);

        double signedAlong = backward.signedDisplacementAlongSurface().orElseThrow();
        double absoluteRange = backward.rangeAlongSurface().orElseThrow();
        assertTrue(signedAlong < 0.0);
        assertEquals(Math.abs(signedAlong), absoluteRange, TOL);
    }

    @Test
    void handlesImpactBeforeVerticalApex() {
        // u = 20 m/s, theta = 50 deg, steep rising incline beta = 45 deg, g = 10 m/s^2
        // Unconstrained apex is at t = 1.532 s, but impact occurs earlier at t ~ 0.493 s while vy > 0
        LandingSurface steep = LandingSurface.incline(0.0, 0.0, 45.0);
        ProjectileMotion model = new ProjectileMotion(20.0, 50.0, 10.0, 0.0, 0.0, steep);

        double flight = model.timeOfFlight().orElseThrow();
        double apexTime = model.timeToApex().orElseThrow();
        assertTrue(flight < apexTime);
        ProjectileMotion.State landing = model.landingState().orElseThrow();
        assertTrue(landing.vy() > 0.0, "Projectile strikes steep incline while still rising");
        assertTrue(model.maximumHeightInFlight().orElseThrow() < model.maximumHeight().orElseThrow());
        assertEquals(landing.y(), model.maximumHeightInFlight().orElseThrow(), TOL);
    }

    @Test
    void handlesImmediateContactAndTangentialContact() {
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);

        // Tangential along incline from surface point: f(0) = 0, v_perp0 = 0 => immediate contact T = 0
        ProjectileMotion tangentOnIncline = new ProjectileMotion(15.0, 30.0, 9.81, 0.0, 0.0, incline);
        assertEquals(0.0, tangentOnIncline.timeOfFlight().orElseThrow(), TOL);
        assertEquals(0.0, tangentOnIncline.rangeAlongSurface().orElseThrow(), TOL);
        assertEquals(0.0, tangentOnIncline.maxPerpendicularDistance().orElseThrow(), TOL);

        // Launched downward into incline from surface => immediate contact T = 0
        ProjectileMotion intoIncline = new ProjectileMotion(15.0, 10.0, 9.81, 0.0, 0.0, incline);
        assertEquals(0.0, intoIncline.timeOfFlight().orElseThrow(), TOL);
        assertEquals(0.0, intoIncline.signedDisplacementAlongSurface().orElseThrow(), TOL);

        // Peak-tangent grazing on elevated horizontal plateau (D = 0, T = 1.0, vy(T) = 0)
        ProjectileMotion peakTangent = new ProjectileMotion(10.0, 90.0, 10.0, 0.0, 0.0, 5.0);
        assertEquals(1.0, peakTangent.timeOfFlight().orElseThrow(), TOL);
        assertEquals(0.0, peakTangent.landingState().orElseThrow().vy(), TOL);
        assertEquals(0.0, peakTangent.maxPerpendicularDistance().orElseThrow(), TOL);
    }

    @Test
    void distinguishesNoLandingBoundedAndUnboundedPerpendicularClearance() {
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 25.0);

        // Zero gravity moving strictly parallel at 4 m vertical (d_perp = 4*cos(25)) above incline:
        // no landing, yet constant bounded perpendicular clearance
        ProjectileMotion parallelZeroG = new ProjectileMotion(12.0, 25.0, 0.0, 0.0, 4.0, incline);
        double expectedClearance = 4.0 * Math.cos(Math.toRadians(25.0));
        assertTrue(parallelZeroG.timeOfFlight().isEmpty());
        assertTrue(parallelZeroG.landingState().isEmpty());
        assertTrue(parallelZeroG.rangeAlongSurface().isEmpty());
        assertEquals(expectedClearance, parallelZeroG.maxSignedPerpendicularDistance().orElseThrow(), TOL);
        assertEquals(expectedClearance, parallelZeroG.maxPerpendicularDistance().orElseThrow(), TOL);

        // Zero gravity moving upward away from incline: no landing, unbounded perpendicular clearance
        ProjectileMotion divergingZeroG = new ProjectileMotion(12.0, 45.0, 0.0, 0.0, 4.0, incline);
        assertTrue(divergingZeroG.timeOfFlight().isEmpty());
        assertTrue(divergingZeroG.maxSignedPerpendicularDistance().isEmpty());
        assertTrue(divergingZeroG.maxPerpendicularDistance().isEmpty());

        // Zero gravity converging onto incline from above: lands linearly at T > 0
        ProjectileMotion convergingZeroG = new ProjectileMotion(10.0, 0.0, 0.0, 0.0, 2.0,
                LandingSurface.incline(0.0, 0.0, 45.0));
        assertEquals(0.2, convergingZeroG.timeOfFlight().orElseThrow(), TOL);

        // Unreachable elevated horizontal ledge (y0 = 0, yMax = 5 < landingY = 10):
        // no landing, bounded negative signed clearance, no positive above-surface clearance
        ProjectileMotion belowLedge = new ProjectileMotion(10.0, 90.0, 10.0, 0.0, 0.0, 10.0);
        assertTrue(belowLedge.timeOfFlight().isEmpty());
        assertEquals(-5.0, belowLedge.maxSignedPerpendicularDistance().orElseThrow(), TOL);
        assertTrue(belowLedge.maxPerpendicularDistance().isEmpty());
    }

    @Test
    void rejectsInvalidSurfaceAndBelowInclineLaunchConditions() {
        assertThrows(IllegalArgumentException.class, () -> LandingSurface.incline(0.0, 0.0, 90.0));
        assertThrows(IllegalArgumentException.class, () -> LandingSurface.incline(0.0, 0.0, -90.0));
        assertThrows(IllegalArgumentException.class, () -> LandingSurface.incline(0.0, 0.0, 110.0));
        assertThrows(IllegalArgumentException.class, () -> LandingSurface.incline(0.0, 0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> LandingSurface.horizontal(Double.POSITIVE_INFINITY));

        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 0.0, (LandingSurface) null));
        // Starting strictly below an inclined surface is invalid
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(20.0, 60.0, 10.0, 0.0, -0.01, incline));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectileMotion.fromVelocityComponents(Double.NaN, 10.0, 9.81, 0.0, 0.0, incline));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(20.0, 60.0, -1.0, 0.0, 0.0, incline));

        // Inclined surface throws explicitly if constant landingY() is requested
        ProjectileMotion inclined = new ProjectileMotion(25.0, 60.0, 10.0, 0.0, 0.0, incline);
        assertThrows(IllegalStateException.class, inclined::landingY);
    }

    @Test
    void solvesOrdinaryEqualHeightZeroAngleElevatedAndBallisticLaunches() {
        // Equal-height 30 deg / 60 deg range equivalence on flat ground
        double g = 9.81;
        ProjectileMotion thirty = new ProjectileMotion(20.0, 30.0, g, 0.0, 0.0, 0.0);
        ProjectileMotion sixty = new ProjectileMotion(20.0, 60.0, g, 0.0, 0.0, 0.0);
        double expectedRange = (400.0 * Math.sin(Math.toRadians(60.0))) / g;
        assertEquals(expectedRange, thirty.horizontalRange().orElseThrow(), TOL);
        assertEquals(expectedRange, sixty.horizontalRange().orElseThrow(), TOL);
        assertEquals(40.0 * 0.5 / g, thirty.timeOfFlight().orElseThrow(), TOL);
        assertEquals(100.0 / (2.0 * g), thirty.maximumHeight().orElseThrow(), TOL);

        // 0 deg elevated horizontal launch
        ProjectileMotion cliffThrow = new ProjectileMotion(15.0, 0.0, 9.8, 0.0, 20.0, 0.0);
        double expectedCliffTime = Math.sqrt(40.0 / 9.8);
        assertEquals(expectedCliffTime, cliffThrow.timeOfFlight().orElseThrow(), TOL);
        assertEquals(15.0 * expectedCliffTime, cliffThrow.horizontalRange().orElseThrow(), TOL);
        assertEquals(0.0, cliffThrow.landingState().orElseThrow().y(), TOL);

        // Realistic ballistic speed (100 m/s)
        ProjectileMotion ballistic = new ProjectileMotion(100.0, 40.0, g, 0.0, 0.0, 0.0);
        double expectedBallisticTime = 200.0 * Math.sin(Math.toRadians(40.0)) / g;
        assertEquals(expectedBallisticTime, ballistic.timeOfFlight().orElseThrow(), TOL);
        assertEquals(10000.0 * Math.sin(Math.toRadians(80.0)) / g,
                ballistic.horizontalRange().orElseThrow(), 1e-8);

        // Speed-angle vs Cartesian (vx, vy) parity on HorizontalPlane
        ProjectileMotion polar = new ProjectileMotion(20.0, Math.toDegrees(Math.atan2(16.0, 12.0)),
                10.0, 0.0, 0.0, 0.0);
        ProjectileMotion cartesian = ProjectileMotion.fromVelocityComponents(
                12.0, 16.0, 10.0, 0.0, 0.0, LandingSurface.horizontal(0.0));
        assertEquals(20.0, cartesian.initialSpeed(), TOL);
        assertEquals(polar.timeOfFlight().orElseThrow(), cartesian.timeOfFlight().orElseThrow(), TOL);
        assertEquals(polar.horizontalRange().orElseThrow(), cartesian.horizontalRange().orElseThrow(), TOL);
    }

    @Test
    void handlesZeroSpeedVerticalDropOntoInclineAndZeroGravityStationaryState() {
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);
        double x0 = 4.0;
        double surfaceY = incline.heightAt(x0);
        ProjectileMotion verticalDrop = new ProjectileMotion(0.0, 0.0, 10.0, x0, surfaceY + 5.0, incline);

        assertEquals(1.0, verticalDrop.timeOfFlight().orElseThrow(), TOL);
        assertEquals(x0, verticalDrop.landingState().orElseThrow().x(), TOL);
        assertEquals(surfaceY, verticalDrop.landingState().orElseThrow().y(), TOL);
        assertEquals(0.0, verticalDrop.rangeAlongSurface().orElseThrow(), TOL);
        assertEquals(5.0 * Math.cos(Math.toRadians(30.0)),
                verticalDrop.maxPerpendicularDistance().orElseThrow(), TOL);

        ProjectileMotion restOnInclineNoGravity = ProjectileMotion.fromVelocityComponents(
                0.0, 0.0, 0.0, x0, surfaceY, incline);
        assertEquals(0.0, restOnInclineNoGravity.timeOfFlight().orElseThrow(), TOL);

        ProjectileMotion stationaryAboveInclineNoGravity = ProjectileMotion.fromVelocityComponents(
                0.0, 0.0, 0.0, x0, surfaceY + 3.0, incline);
        assertTrue(stationaryAboveInclineNoGravity.timeOfFlight().isEmpty());
        assertEquals(3.0 * Math.cos(Math.toRadians(30.0)),
                stationaryAboveInclineNoGravity.maxPerpendicularDistance().orElseThrow(), TOL);
    }

    @Test
    void handlesNegativeHorizontalVelocityOnHorizontalPlaneAndNegativeAnchor() {
        ProjectileMotion flatBackward = new ProjectileMotion(20.0, 135.0, 10.0, 0.0, 0.0, 0.0);
        assertTrue(flatBackward.horizontalRange().orElseThrow() < 0.0);
        assertEquals(flatBackward.horizontalRange().orElseThrow(),
                flatBackward.signedDisplacementAlongSurface().orElseThrow(), TOL);
        assertEquals(Math.abs(flatBackward.horizontalRange().orElseThrow()),
                flatBackward.rangeAlongSurface().orElseThrow(), TOL);

        LandingSurface negativeAnchorSlope = LandingSurface.incline(-4.0, -3.0, -20.0);
        ProjectileMotion model = new ProjectileMotion(14.0, 50.0, 9.81, -1.0, 5.0, negativeAnchorSlope);
        ProjectileMotion.State landing = model.landingState().orElseThrow();
        assertEquals(negativeAnchorSlope.heightAt(landing.x()), landing.y(), 1e-8);
        assertEquals(negativeAnchorSlope.signedCoordinateFromAnchor(landing.x()),
                model.signedCoordinateAlongSurfaceFromAnchor().orElseThrow(), 1e-8);
    }

    @Test
    void handlesSteepInclineLimitsAndExtremeScaleInvarianceOnInclinedPlane() {
        // Steep slopes approaching (-90, +90) boundaries
        LandingSurface steepRising = LandingSurface.incline(0.0, 0.0, 89.0);
        ProjectileMotion upSteep = new ProjectileMotion(20.0, 89.5, 10.0, 0.0, 0.0, steepRising);
        double tSteepUp = upSteep.timeOfFlight().orElseThrow();
        ProjectileMotion.State landSteepUp = upSteep.landingState().orElseThrow();
        assertTrue(tSteepUp > 0.0 && Double.isFinite(tSteepUp));
        assertEquals(steepRising.heightAt(landSteepUp.x()), landSteepUp.y(), 1e-7 * Math.max(1.0, Math.abs(landSteepUp.y())));

        LandingSurface steepFalling = LandingSurface.incline(0.0, 0.0, -89.0);
        ProjectileMotion downSteep = new ProjectileMotion(10.0, 0.0, 10.0, 0.0, 0.0, steepFalling);
        double tSteepDown = downSteep.timeOfFlight().orElseThrow();
        ProjectileMotion.State landSteepDown = downSteep.landingState().orElseThrow();
        assertTrue(tSteepDown > 0.0 && Double.isFinite(tSteepDown));
        assertEquals(steepFalling.heightAt(landSteepDown.x()), landSteepDown.y(), 1e-7 * Math.max(1.0, Math.abs(landSteepDown.y())));

        // Scale invariance on inclined planes from 1e-100 to 1e150
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);
        double expectedFlight = 2.0 * Math.sin(Math.toRadians(30.0)) / Math.cos(Math.toRadians(30.0));
        ProjectileMotion tinyIncline = new ProjectileMotion(1e-100, 60.0, 1e-100, 0.0, 0.0, incline);
        ProjectileMotion hugeIncline = new ProjectileMotion(1e150, 60.0, 1e150, 0.0, 0.0, incline);
        assertEquals(expectedFlight, tinyIncline.timeOfFlight().orElseThrow(), TOL);
        assertEquals(expectedFlight, hugeIncline.timeOfFlight().orElseThrow(), TOL);
        assertTrue(Double.isFinite(hugeIncline.rangeAlongSurface().orElseThrow()));
        assertTrue(Double.isFinite(hugeIncline.maxPerpendicularDistance().orElseThrow()));
    }

    @Test
    void preservesSmallPositiveFlightForNearTangentInclineAndIrrationalFlatApexTangent() {
        LandingSurface incline = LandingSurface.incline(0.0, 0.0, 30.0);
        ProjectileMotion nearTangent = new ProjectileMotion(25.0, 30.0001, 10.0, 0.0, 0.0, incline);

        double tinyFlight = nearTangent.timeOfFlight().orElseThrow();
        double expectedFlight = 5.0 * (Math.sin(Math.toRadians(30.0001))
                - Math.cos(Math.toRadians(30.0001)) * Math.tan(Math.toRadians(30.0)));
        assertTrue(tinyFlight > 0.0, "Small genuine launch above incline must not be clamped to T = 0");
        assertEquals(expectedFlight, tinyFlight, 1e-14);
        assertTrue(nearTangent.maxPerpendicularDistance().orElseThrow() > 0.0);

        // Irrational peak height on elevated flat surface preserves grazing tangent root under trig ULP noise
        double g = 9.81;
        double speed = 17.0;
        double angle = 53.0;
        double vy0 = speed * Math.sin(Math.toRadians(angle));
        double exactPeakHeight = (vy0 * vy0) / (2.0 * g);
        ProjectileMotion grazingPlateau = new ProjectileMotion(speed, angle, g, 0.0, 0.0, exactPeakHeight);
        assertEquals(vy0 / g, grazingPlateau.timeOfFlight().orElseThrow(), 1e-11);

        ProjectileMotion justAbovePeak = new ProjectileMotion(speed, angle, g, 0.0, 0.0, exactPeakHeight + 1e-8);
        assertTrue(justAbovePeak.timeOfFlight().isEmpty());
    }

    @Test
    void distinguishesMaximumHeightInFlightAndClearanceAtZeroGravityAndBelowSurface() {
        // Zero gravity with positive vy: unbounded without landing, finite if steep incline catches it
        ProjectileMotion zeroGFlatUp = new ProjectileMotion(10.0, 40.0, 0.0, 0.0, 2.0, 0.0);
        assertTrue(zeroGFlatUp.maximumHeightInFlight().isEmpty());

        LandingSurface steepIncline = LandingSurface.incline(0.0, 0.0, 60.0);
        ProjectileMotion zeroGCaughtBySteepIncline = new ProjectileMotion(10.0, 30.0, 0.0, 0.0, 5.0, steepIncline);
        assertEquals(0.5, zeroGCaughtBySteepIncline.timeOfFlight().orElseThrow(), TOL);
        assertEquals(7.5, zeroGCaughtBySteepIncline.maximumHeightInFlight().orElseThrow(), TOL);

        // Zero gravity with zero and negative vy
        ProjectileMotion zeroGHorizontal = new ProjectileMotion(10.0, 0.0, 0.0, 0.0, 4.0, 0.0);
        assertEquals(4.0, zeroGHorizontal.maximumHeightInFlight().orElseThrow(), TOL);
        ProjectileMotion zeroGDownward = new ProjectileMotion(10.0, -30.0, 0.0, 0.0, 6.0, 0.0);
        assertEquals(1.2, zeroGDownward.timeOfFlight().orElseThrow(), TOL);
        assertEquals(6.0, zeroGDownward.maximumHeightInFlight().orElseThrow(), TOL);
        ProjectileMotion zeroGDownwardNoLanding = new ProjectileMotion(10.0, -30.0, 0.0, 0.0, -2.0, 0.0);
        assertTrue(zeroGDownwardNoLanding.timeOfFlight().isEmpty());
        assertEquals(-2.0, zeroGDownwardNoLanding.maximumHeightInFlight().orElseThrow(), TOL);
        assertEquals(-2.0, zeroGDownwardNoLanding.maxSignedPerpendicularDistance().orElseThrow(), TOL);
        assertTrue(zeroGDownwardNoLanding.maxPerpendicularDistance().isEmpty());

        // Decreasing zero-gravity clearance above incline until impact
        ProjectileMotion zeroGConverging = new ProjectileMotion(
                10.0, 0.0, 0.0, 0.0, 2.0, LandingSurface.incline(0.0, 0.0, 45.0));
        assertEquals(0.2, zeroGConverging.timeOfFlight().orElseThrow(), TOL);
        assertEquals(2.0 * Math.cos(Math.toRadians(45.0)),
                zeroGConverging.maxPerpendicularDistance().orElseThrow(), TOL);

        // No landing under gravity below unreachable plateau still has finite maximumHeightInFlight
        ProjectileMotion underCeiling = new ProjectileMotion(10.0, 90.0, 10.0, 0.0, 0.0, 10.0);
        assertTrue(underCeiling.timeOfFlight().isEmpty());
        assertEquals(5.0, underCeiling.maximumHeightInFlight().orElseThrow(), TOL);

        // Gravity downward launch from elevated ledge: max height in flight equals initialY
        ProjectileMotion downwardDrop = new ProjectileMotion(12.0, -45.0, 10.0, 0.0, 8.0, 0.0);
        assertTrue(downwardDrop.timeOfFlight().orElseThrow() > 0.0);
        assertEquals(8.0, downwardDrop.maximumHeightInFlight().orElseThrow(), TOL);
    }
}

