package edu.physics.projectile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileMotionEdgeCaseTest {
    private static final double TOL = 1e-9;

    @ParameterizedTest
    @CsvSource({"30, 8.660254037844386, 5.0", "60, 5.0, 8.660254037844386", "90, 0, 10"})
    void resolvesKnownLaunchDirections(double degrees, double expectedVx, double expectedVy) {
        ProjectileMotion model = new ProjectileMotion(10, degrees, 10, 0, 0, 0);
        assertEquals(expectedVx, model.initialVx(), TOL);
        assertEquals(expectedVy, model.initialVy(), TOL);
    }

    @Test
    void providesPositionVelocityAccelerationAndDisplacementVectors() {
        ProjectileMotion model = new ProjectileMotion(10, 0, 2, 3, 4, 0);
        ProjectileMotion.State state = model.stateAt(2);
        assertEquals(23, state.x(), TOL);
        assertEquals(0, state.y(), TOL);
        assertEquals(Math.hypot(10, -4), state.speed(), TOL);
        assertEquals(20, model.displacementAt(2).x(), TOL);
        assertEquals(-4, model.displacementAt(2).y(), TOL);
        assertEquals(Math.hypot(20, 4), model.straightLineDisplacementAt(2), TOL);
        assertEquals(0, model.acceleration().x(), TOL);
        assertEquals(-2, model.acceleration().y(), TOL);
        assertEquals(2, model.acceleration().magnitude(), TOL);
        assertEquals(Math.toDegrees(Math.atan2(-4, 10)), model.directionAt(2).orElseThrow(), TOL);
    }

    @Test
    void givesApexPositionVelocityAndSpeed() {
        ProjectileMotion model = new ProjectileMotion(20, 30, 10, 2, 3, 0);
        double apexTime = 1;
        ProjectileMotion.State apex = model.apexState().orElseThrow();
        assertEquals(apexTime, model.timeToApex().orElseThrow(), TOL);
        assertEquals(2 + 10 * Math.sqrt(3), apex.x(), TOL);
        assertEquals(8, apex.y(), TOL);
        assertEquals(10 * Math.sqrt(3), apex.vx(), TOL);
        assertEquals(0, apex.vy(), TOL);
        assertEquals(10 * Math.sqrt(3), apex.speed(), TOL);
    }

    @Test
    void reportsTwoHeightCrossingsAndKeepsTheirMeaning() {
        ProjectileMotion model = new ProjectileMotion(20, 90, 10, 0, 0, 0);
        ProjectileMotion.TimeSolutions times = model.timesAtHeight(15);
        assertFalse(times.infinitelyMany());
        assertEquals(List.of(1.0, 3.0), times.times());
        List<ProjectileMotion.State> states = model.statesAtHeight(15);
        assertEquals(2, states.size());
        assertEquals(10, states.get(0).vy(), TOL);
        assertEquals(-10, states.get(1).vy(), TOL);
        assertEquals(states.get(0).speed(), states.get(1).speed(), TOL);
        assertEquals(20, model.maximumHeight().orElseThrow(), TOL);
        assertEquals(2, model.timeToApex().orElseThrow(), TOL);
    }

    @Test
    void identifiesRepeatedAndUnreachableHeightRoots() {
        ProjectileMotion model = new ProjectileMotion(10, 90, 10, 0, 0, 0);
        assertEquals(List.of(1.0), model.timesAtHeight(5).times());
        assertTrue(model.timesAtHeight(6).times().isEmpty());
        assertEquals(0, model.statesAtHeight(5).get(0).vy(), TOL);
    }

    @Test
    void horizontalPositionQueryHasOneTimeOrAnExplicitInfiniteSet() {
        ProjectileMotion horizontal = new ProjectileMotion(10, 0, 10, 4, 2, 0);
        assertEquals(List.of(2.0), horizontal.timesAtHorizontalPosition(24).times());
        assertEquals(-18, horizontal.statesAtHorizontalPosition(24).get(0).y(), TOL);
        assertTrue(horizontal.timesAtHorizontalPosition(3).times().isEmpty());

        ProjectileMotion vertical = new ProjectileMotion(10, 90, 10, 4, 2, 0);
        assertTrue(vertical.timesAtHorizontalPosition(4).infinitelyMany());
        assertFalse(vertical.timesAtHorizontalPosition(5).hasSolutions());
        assertTrue(vertical.statesAtHorizontalPosition(4).isEmpty());
    }

    @Test
    void zeroGravityHasLinearStateAndExplicitInfiniteHeightSolutions() {
        ProjectileMotion model = new ProjectileMotion(5, 53.13010235415598, 0, 0, 1, 0);
        assertEquals(3, model.initialVx(), 1e-8);
        assertEquals(4, model.initialVy(), 1e-8);
        assertEquals(2.0, model.timesAtHeight(9).times().get(0), 1e-8);
        assertEquals(List.of(0.0), model.timesAtHeight(1).times());
        ProjectileMotion stationaryHeight = new ProjectileMotion(3, 0, 0, 0, 1, 0);
        assertTrue(stationaryHeight.timesAtHeight(1).infinitelyMany());
        assertEquals(10, model.distanceTravelledAt(2), 1e-8);
        assertTrue(model.timeToApex().isEmpty()); // upward motion never reaches an apex without gravity
    }

    @Test
    void arcLengthHandlesVerticalReversalNoGravityAndNumericalIntegration() {
        ProjectileMotion vertical = new ProjectileMotion(10, 90, 10, 0, 0, 0);
        assertEquals(10, vertical.distanceTravelledAt(2), TOL);
        ProjectileMotion diagonal = new ProjectileMotion(5, 53.13010235415598, 0, 0, 0, 0);
        assertEquals(10, diagonal.distanceTravelledAt(2), 1e-8);

        ProjectileMotion curved = new ProjectileMotion(20, 35, 9.81, 0, 0, 0);
        assertTrue(curved.distanceTravelledAt(1) >= curved.straightLineDisplacementAt(1));
    }

    @Test
    void supportsAnglesOutsideUiSliderRangeAndRejectsNonFiniteInputs() {
        ProjectileMotion downward = new ProjectileMotion(10, -30, 10, 0, 0, -100);
        assertTrue(downward.initialVy() < 0);
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(1, Double.NaN, 9.8, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(1, 45, 9.8, Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(1, 45, Double.NEGATIVE_INFINITY, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectileMotion(1, 45, 9.8, 0, 0, 0).timesAtHeight(Double.NaN));
    }

    @Test
    void handlesImmediateLandingAndNoLandingCases() {
        ProjectileMotion downward = new ProjectileMotion(10, -30, 10, 0, 0, 0);
        assertEquals(0, downward.timeOfFlight().orElseThrow(), TOL);
        ProjectileMotion horizontalZeroGravity = new ProjectileMotion(10, 0, 0, 0, 1, 0);
        assertTrue(horizontalZeroGravity.timeOfFlight().isEmpty());
        ProjectileMotion zeroSpeed = new ProjectileMotion(0, 45, 10, 0, 0, 0);
        assertEquals(0, zeroSpeed.timeOfFlight().orElseThrow(), TOL);
        assertEquals(0, zeroSpeed.horizontalRange().orElseThrow(), TOL);
    }

    @Test
    void handlesLandingAboveAndBelowTheLaunchLevel() {
        ProjectileMotion above = new ProjectileMotion(20, 90, 10, 0, 0, 15);
        assertEquals(3, above.timeOfFlight().orElseThrow(), TOL);
        assertEquals(15, above.stateAt(above.timeOfFlight().orElseThrow()).y(), TOL);
        assertTrue(above.stateAt(above.timeOfFlight().orElseThrow()).vy() < 0,
                "Landing on an elevated surface is the descending intersection");

        ProjectileMotion unreachableHigherSurface = new ProjectileMotion(10, 90, 10, 0, 0, 6);
        assertTrue(unreachableHigherSurface.timeOfFlight().isEmpty());

        ProjectileMotion upwardUnboundedToHigherHeight = new ProjectileMotion(10, 90, 0, 0, 0, 6);
        assertTrue(upwardUnboundedToHigherHeight.timesAtHeight(6).hasSolutions());
        assertTrue(upwardUnboundedToHigherHeight.timeOfFlight().isEmpty(),
                "An upward-only crossing is not a landing event");

        ProjectileMotion below = new ProjectileMotion(10, -30, 10, 0, 5, -10);
        double flight = below.timeOfFlight().orElseThrow();
        assertTrue(flight > 0);
        assertEquals(-10, below.stateAt(flight).y(), TOL);
    }

    @Test
    void keepsAnalyticalQueriesStableForVerySmallAndLargeSimilarScales() {
        ProjectileMotion tiny = new ProjectileMotion(1e-100, 45, 1e-100, 0, 0, 0);
        assertEquals(2 * Math.sin(Math.PI / 4), tiny.timeOfFlight().orElseThrow(), TOL);
        assertEquals(0.25e-100, tiny.maximumHeight().orElseThrow(), 1e-115);

        ProjectileMotion large = new ProjectileMotion(1e150, 45, 1e150, 0, 0, 0);
        assertEquals(tiny.timeOfFlight().orElseThrow(), large.timeOfFlight().orElseThrow(), TOL);
        assertTrue(Double.isFinite(large.horizontalRange().orElseThrow()));
        assertTrue(Double.isFinite(large.maximumHeight().orElseThrow()));
    }

    @Test
    void stableHeightRootRetainsSmallLandingTimeAfterLargeDownwardLaunchSpeed() {
        ProjectileMotion model = new ProjectileMotion(1e8, -90, 1, 0, 1, 0);
        double time = model.timeOfFlight().orElseThrow();
        assertEquals(1e-8, time, 1e-15);
        assertEquals(0, model.stateAt(time).y(), 1e-8);
    }

    @Test
    void tinyArcLengthDoesNotLosePrecisionBySubtractingNearlyEqualPrimitives() {
        ProjectileMotion model = new ProjectileMotion(10, 30, 9.81, 0, 0, 0);
        double time = 1e-12;
        double distance = model.distanceTravelledAt(time);
        assertEquals(10 * time, distance, 1e-22);
        assertTrue(distance > 0);
    }

    @ParameterizedTest
    @CsvSource({"7, 0, 9.8, 0.2", "15, 30, 4, 1.1", "25, 60, 12, 0.7", "2, 89.999, 9.81, 0.01"})
    void analyticalStateSatisfiesVelocityAndPositionInvariants(double speed, double angleDegrees,
                                                               double gravity, double time) {
        ProjectileMotion model = new ProjectileMotion(speed, angleDegrees, gravity, 2, 5, 0);
        ProjectileMotion.State initial = model.stateAt(0);
        ProjectileMotion.State later = model.stateAt(time);
        double radians = Math.toRadians(angleDegrees);
        assertEquals(speed * Math.cos(radians), later.vx(), 1e-9 * Math.max(1, speed));
        assertEquals(initial.vy() - gravity * time, later.vy(), 1e-9 * Math.max(1, speed));
        assertEquals(initial.x() + initial.vx() * time, later.x(), 1e-9 * Math.max(1, Math.abs(later.x())));
        assertEquals(initial.y() + initial.vy() * time - 0.5 * gravity * time * time,
                later.y(), 1e-9 * Math.max(1, Math.abs(later.y())));
        assertEquals(Math.hypot(later.vx(), later.vy()), later.speed(), TOL);
    }

    @Test
    void rejectsQueriesOutsideFiniteNumericalRange() {
        ProjectileMotion model = new ProjectileMotion(2, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> model.stateAt(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> model.timesAtHorizontalPosition(Double.NaN));
        assertThrows(ArithmeticException.class, () -> model.stateAt(Double.MAX_VALUE));
        ProjectileMotion extreme = new ProjectileMotion(1e200, 90, 1e200, 0, 0, 0);
        assertThrows(ArithmeticException.class, () -> extreme.timesAtHeight(-1));
    }
}
