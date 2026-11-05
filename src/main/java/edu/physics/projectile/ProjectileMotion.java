package edu.physics.projectile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Immutable analytical model of a point projectile in a uniform gravitational
 * field with no air resistance. SI units are used. The launch angle is in
 * degrees counterclockwise from +x; any finite angle is valid and periodic.
 * Time queries describe the continuing ideal trajectory, including after it
 * crosses the configured landing height.
 */
public final class ProjectileMotion {
    private static final double ROOT_ROUNDOFF_ULPS = 8.0;

    /** Two-dimensional vector in SI units. */
    public record Vector2(double x, double y) {
        public double magnitude() {
            return finite(Math.hypot(x, y), "vector magnitude");
        }
    }

    /** Position and velocity at one instant, expressed in SI units. */
    public record State(double x, double y, double vx, double vy) {
        public Vector2 position() { return new Vector2(x, y); }
        public Vector2 velocity() { return new Vector2(vx, vy); }
        public double speed() { return finite(Math.hypot(vx, vy), "speed"); }
    }

    /** Roots of a time query. An infinite set is used for stationary coordinates. */
    public record TimeSolutions(List<Double> times, boolean infinitelyMany) {
        public TimeSolutions {
            times = times.stream().map(time -> time == 0.0 ? 0.0 : time).toList();
            if (infinitelyMany && !times.isEmpty()) {
                throw new IllegalArgumentException("An infinite solution set cannot list finite roots");
            }
        }
        public boolean hasSolutions() { return infinitelyMany || !times.isEmpty(); }
    }

    private final double gravity;
    private final double initialX;
    private final double initialY;
    private final double landingY;
    private final double initialVx;
    private final double initialVy;
    private final double initialSpeed;
    private final double launchAngleDegrees;

    public ProjectileMotion(double initialSpeed, double launchAngleDegrees,
                            double gravity, double initialX, double initialY,
                            double landingY) {
        requireFinite("initial speed", initialSpeed);
        requireFinite("launch angle", launchAngleDegrees);
        requireFinite("gravity", gravity);
        requireFinite("initial x", initialX);
        requireFinite("initial y", initialY);
        requireFinite("landing y", landingY);
        if (initialSpeed < 0.0) {
            throw new IllegalArgumentException("Initial speed must be non-negative");
        }
        if (gravity < 0.0) {
            throw new IllegalArgumentException("Gravity magnitude must be non-negative");
        }

        this.gravity = gravity;
        this.initialX = initialX;
        this.initialY = initialY;
        this.landingY = landingY;
        this.initialSpeed = initialSpeed;
        this.launchAngleDegrees = launchAngleDegrees;
        double radians = Math.toRadians(launchAngleDegrees % 360.0);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        // Remove trigonometric residue at the cardinal launch directions.
        if (launchAngleDegrees % 90.0 == 0.0) {
            int quadrant = Math.floorMod((int) Math.round((launchAngleDegrees % 360.0) / 90.0), 4);
            cosine = switch (quadrant) { case 0 -> 1.0; case 2 -> -1.0; default -> 0.0; };
            sine = switch (quadrant) { case 1 -> 1.0; case 3 -> -1.0; default -> 0.0; };
        }
        this.initialVx = finite(initialSpeed * cosine, "initial horizontal velocity");
        this.initialVy = finite(initialSpeed * sine, "initial vertical velocity");
    }

    public double gravity() { return gravity; }
    public double initialX() { return initialX; }
    public double initialY() { return initialY; }
    public double landingY() { return landingY; }
    public double initialSpeed() { return initialSpeed; }
    public double launchAngleDegrees() { return launchAngleDegrees; }
    public double initialVx() { return initialVx; }
    public double initialVy() { return initialVy; }
    public Vector2 initialVelocity() { return new Vector2(initialVx, initialVy); }
    public Vector2 acceleration() { return new Vector2(0.0, -gravity); }

    /** Evaluates the exact model at any finite, non-negative time. */
    public State stateAt(double time) {
        requireNonNegativeFinite("time", time);
        double x = finite(Math.fma(initialVx, time, initialX), "horizontal position");
        double y = finite(Math.fma(-0.5 * gravity * time, time,
                Math.fma(initialVy, time, initialY)), "vertical position");
        double vy = finite(Math.fma(-gravity, time, initialVy), "vertical velocity");
        return new State(x, y, initialVx, vy);
    }

    public Vector2 displacementAt(double time) {
        State state = stateAt(time);
        return new Vector2(state.x - initialX, state.y - initialY);
    }

    public double straightLineDisplacementAt(double time) {
        return displacementAt(time).magnitude();
    }

    /**
     * Arc length from launch to the requested time. Uses the analytical primitive
     * normally and adaptive 8-point Gauss-Legendre integration when primitive
     * subtraction would lose precision. The fallback compares one interval with
     * two half-intervals using a scale-aware 1e-12 tolerance (16 refinements max).
     */
    public double distanceTravelledAt(double time) {
        requireNonNegativeFinite("time", time);
        if (time == 0.0) return 0.0;
        double horizontalSpeed = Math.abs(initialVx);
        if (gravity == 0.0) {
            return finite(Math.hypot(initialVx, initialVy) * time, "distance travelled");
        }
        if (horizontalSpeed == 0.0) {
            return verticalDistance(time);
        }
        double verticalChange = gravity * time;
        double velocityScale = Math.max(horizontalSpeed, Math.abs(initialVy));
        double verticalScale = Math.max(Math.abs(initialVy), Math.abs(verticalChange));
        if (horizontalSpeed > 0.0 && verticalScale > 0.0
                && horizontalSpeed / verticalScale < 1.0e-150) {
            return verticalDistance(time);
        }
        if (Double.isFinite(verticalChange) && velocityScale > 0.0
                && Math.abs(verticalChange) / velocityScale < 1.0e-5) {
            return gaussArcLength(time);
        }
        double initialVerticalSpeed = initialVy;
        double finalVerticalSpeed = finite(Math.fma(-gravity, time, initialVy), "vertical velocity");
        double difference = arcPrimitive(initialVerticalSpeed, horizontalSpeed)
                - arcPrimitive(finalVerticalSpeed, horizontalSpeed);
        return finite(Math.abs(difference / gravity), "distance travelled");
    }

    /** Direction of a non-zero velocity, in degrees from +x. Empty at zero speed. */
    public OptionalDouble directionAt(double time) {
        State state = stateAt(time);
        if (state.vx == 0.0 && state.vy == 0.0) return OptionalDouble.empty();
        return OptionalDouble.of(Math.toDegrees(Math.atan2(state.vy, state.vx)));
    }

    /** First landing crossing under the model's convention; empty means no future landing. */
    public OptionalDouble timeOfFlight() {
        TimeSolutions crossings = timesAtHeight(landingY);
        if (crossings.infinitelyMany()) {
            return initialVy <= 0.0 ? OptionalDouble.of(0.0) : OptionalDouble.empty();
        }
        if (crossings.times().isEmpty()) return OptionalDouble.empty();
        if (initialY == landingY && initialVy > 0.0 && gravity == 0.0) {
            return OptionalDouble.empty();
        }
        if (initialY == landingY && initialVy <= 0.0) return OptionalDouble.of(0.0);
        return crossings.times().stream().filter(t -> t > 0.0).mapToDouble(Double::doubleValue).min();
    }

    /** Time at the apex; empty when positive vertical motion is unbounded (g = 0). */
    public OptionalDouble timeToApex() {
        if (gravity == 0.0) {
            return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(0.0);
        }
        return OptionalDouble.of(Math.max(0.0, initialVy / gravity));
    }

    /** Greatest vertical coordinate reached, or empty when unbounded. */
    public OptionalDouble maximumHeight() {
        if (gravity == 0.0) {
            return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(initialY);
        }
        double rise = initialVy > 0.0
                ? finite(0.5 * initialVy * (initialVy / gravity), "maximum height rise") : 0.0;
        return OptionalDouble.of(finite(initialY + rise, "maximum height"));
    }

    public Optional<State> apexState() {
        OptionalDouble time = timeToApex();
        return time.isPresent() ? Optional.of(stateAt(time.getAsDouble())) : Optional.empty();
    }

    /** Horizontal displacement from launch to the selected landing surface. */
    public OptionalDouble horizontalRange() {
        OptionalDouble flightTime = timeOfFlight();
        if (flightTime.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(finite(initialVx * flightTime.getAsDouble(), "horizontal range"));
    }

    /** All non-negative times at which the projectile reaches a vertical coordinate. */
    public TimeSolutions timesAtHeight(double height) {
        requireFinite("height", height);
        double displacement = finite(initialY - height, "height difference");
        if (gravity == 0.0) {
            if (initialVy == 0.0) {
                return displacement == 0.0
                        ? new TimeSolutions(List.of(), true) : new TimeSolutions(List.of(), false);
            }
            double time = finite(-displacement / initialVy, "time at height");
            return time >= 0.0
                    ? new TimeSolutions(List.of(time), false) : new TimeSolutions(List.of(), false);
        }

        if (displacement == 0.0) {
            if (initialVy > 0.0) {
                double returnTime = finite(2.0 * (initialVy / gravity), "equal-height return time");
                return new TimeSolutions(List.of(0.0, returnTime), false);
            }
            return new TimeSolutions(List.of(0.0), false);
        }

        double verticalTerm = finite(2.0 * gravity * displacement, "height discriminant term");
        double squareTerm = finite(initialVy * initialVy, "height discriminant term");
        double discriminant = Math.fma(2.0 * gravity, displacement, squareTerm);
        if (!Double.isFinite(discriminant)) throw numericRange("height discriminant");
        double scale = Math.abs(squareTerm) + Math.abs(verticalTerm);
        if (discriminant < 0.0 && -discriminant <= ROOT_ROUNDOFF_ULPS * Math.ulp(scale)) {
            discriminant = 0.0;
        }
        if (discriminant < 0.0) return new TimeSolutions(List.of(), false);

        double root = Math.sqrt(discriminant);
        if (root == 0.0) {
            double time = finite(initialVy / gravity, "repeated-root time");
            return time >= 0.0
                    ? new TimeSolutions(List.of(time), false) : new TimeSolutions(List.of(), false);
        }
        double largeNumerator = initialVy + Math.copySign(root, initialVy == 0.0 ? 1.0 : initialVy);
        double first = finite(largeNumerator / gravity, "height crossing time");
        double second = finite((-2.0 * displacement) / largeNumerator, "height crossing time");
        List<Double> times = new ArrayList<>(2);
        if (first >= 0.0) times.add(first);
        if (second >= 0.0 && !sameRoot(first, second)) times.add(second);
        times.sort(Comparator.naturalOrder());
        return new TimeSolutions(times, false);
    }

    public List<State> statesAtHeight(double height) {
        TimeSolutions solutions = timesAtHeight(height);
        return solutions.times().stream().map(this::stateAt).toList();
    }

    /** All non-negative times at a horizontal coordinate; dx means displacement from launch. */
    public TimeSolutions timesAtHorizontalPosition(double x) {
        requireFinite("horizontal position", x);
        double dx = finite(x - initialX, "horizontal displacement");
        if (initialVx == 0.0) {
            return dx == 0.0
                    ? new TimeSolutions(List.of(), true) : new TimeSolutions(List.of(), false);
        }
        double time = finite(dx / initialVx, "time at horizontal position");
        return time >= 0.0
                ? new TimeSolutions(List.of(time), false) : new TimeSolutions(List.of(), false);
    }

    /** Times at a two-dimensional coordinate. No root is chosen when multiple times are valid. */
    public TimeSolutions timesAtPosition(double x, double y) {
        requireFinite("horizontal position", x);
        requireFinite("vertical position", y);
        TimeSolutions horizontal = timesAtHorizontalPosition(x);
        if (!horizontal.infinitelyMany()) {
            List<Double> matching = horizontal.times().stream()
                    .filter(time -> nearlyEqual(stateAt(time).y(), y)).toList();
            return new TimeSolutions(matching, false);
        }
        return timesAtHeight(y);
    }

    public List<State> statesAtHorizontalPosition(double x) {
        TimeSolutions solutions = timesAtHorizontalPosition(x);
        return solutions.times().stream().map(this::stateAt).toList();
    }

    public List<Double> speedsAtHeight(double height) {
        return statesAtHeight(height).stream().map(State::speed).toList();
    }

    public List<Vector2> velocitiesAtHeight(double height) {
        return statesAtHeight(height).stream().map(State::velocity).toList();
    }

    public List<Double> speedsAtHorizontalPosition(double x) {
        return statesAtHorizontalPosition(x).stream().map(State::speed).toList();
    }

    private static boolean sameRoot(double a, double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }

    private static boolean nearlyEqual(double a, double b) {
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= 32.0 * Math.ulp(scale);
    }

    private static double arcPrimitive(double verticalSpeed, double horizontalSpeed) {
        double speed = Math.hypot(horizontalSpeed, verticalSpeed);
        double first = verticalSpeed * speed;
        double asinh = asinh(verticalSpeed / horizontalSpeed);
        double second = horizontalSpeed * horizontalSpeed * asinh;
        return finite(0.5 * (first + second), "arc-length primitive");
    }

    private double verticalDistance(double time) {
        double apex = Math.max(0.0, initialVy / gravity);
        if (initialVy > 0.0 && time > apex) {
            double rise = 0.5 * initialVy * (initialVy / gravity);
            double fallTime = time - apex;
            return finite(rise + 0.5 * gravity * fallTime * fallTime, "distance travelled");
        }
        return finite(Math.abs(initialVy * time - 0.5 * gravity * time * time), "distance travelled");
    }

    /** Adaptive Gaussian evaluation avoids subtracting nearly equal primitives for tiny time steps. */
    private double gaussArcLength(double time) {
        double whole = gaussArcLengthOn(0.0, time);
        return adaptiveArcLength(0.0, time, whole, 1.0e-12 * Math.max(1.0, whole), 16);
    }

    private double adaptiveArcLength(double start, double end, double whole,
                                     double tolerance, int remainingRefinements) {
        double midpoint = start + 0.5 * (end - start);
        double left = gaussArcLengthOn(start, midpoint);
        double right = gaussArcLengthOn(midpoint, end);
        double split = left + right;
        if (Math.abs(split - whole) <= tolerance) return finite(split, "distance travelled");
        if (remainingRefinements == 0) {
            throw new ArithmeticException("Arc-length integration did not converge within 16 refinements");
        }
        return finite(adaptiveArcLength(start, midpoint, left, tolerance * 0.5, remainingRefinements - 1)
                + adaptiveArcLength(midpoint, end, right, tolerance * 0.5, remainingRefinements - 1),
                "distance travelled");
    }

    private double gaussArcLengthOn(double start, double end) {
        double[] nodes = {0.1834346424956498, 0.5255324099163290,
                0.7966664774136267, 0.9602898564975363};
        double[] weights = {0.3626837833783620, 0.3137066458778873,
                0.2223810344533745, 0.1012285362903763};
        double midpoint = start + 0.5 * (end - start);
        double halfWidth = 0.5 * (end - start);
        double sum = 0.0;
        for (int i = 0; i < nodes.length; i++) {
            double offset = halfWidth * nodes[i];
            double leftVy = Math.fma(-gravity, midpoint - offset, initialVy);
            double rightVy = Math.fma(-gravity, midpoint + offset, initialVy);
            sum += weights[i] * (Math.hypot(initialVx, leftVy) + Math.hypot(initialVx, rightVy));
        }
        return finite(halfWidth * sum, "arc-length quadrature");
    }

    private static double asinh(double value) {
        double absolute = Math.abs(value);
        if (absolute < 1.0e-8) return value;
        double result = absolute > 1.0e154
                ? Math.log(absolute) + Math.log(2.0)
                : Math.log(absolute + Math.hypot(absolute, 1.0));
        return Math.copySign(result, value);
    }

    private static void requireNonNegativeFinite(String name, double value) {
        requireFinite(name, value);
        if (value < 0.0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static double finite(double value, String calculation) {
        if (!Double.isFinite(value)) throw numericRange(calculation);
        return value;
    }

    private static ArithmeticException numericRange(String calculation) {
        return new ArithmeticException(calculation + " is outside the supported finite numeric range");
    }
}
