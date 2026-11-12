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
    private static final double SURFACE_ROUNDOFF_ULPS = 16.0;

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
    private final LandingSurface landingSurface;
    private final double initialVx;
    private final double initialVy;
    private final double initialSpeed;
    private final double launchAngleDegrees;

    /**
     * Constructs a projectile motion model with a horizontal landing surface at {@code y = landingY}.
     */
    public ProjectileMotion(double initialSpeed, double launchAngleDegrees,
                            double gravity, double initialX, double initialY,
                            double landingY) {
        this(initialSpeed, launchAngleDegrees, gravity, initialX, initialY,
                new LandingSurface.HorizontalPlane(landingY));
    }

    /**
     * Constructs a projectile motion model over an arbitrary {@link LandingSurface}.
     */
    public ProjectileMotion(double initialSpeed, double launchAngleDegrees,
                            double gravity, double initialX, double initialY,
                            LandingSurface landingSurface) {
        this(
                resolveVx(requireNonNegativeSpeed(initialSpeed), requireFiniteAngle(launchAngleDegrees)),
                resolveVy(initialSpeed, launchAngleDegrees),
                initialSpeed,
                launchAngleDegrees,
                gravity,
                initialX,
                initialY,
                landingSurface);
    }

    /**
     * Constructs a projectile motion model directly from Cartesian initial velocity components
     * {@code (initialVx, initialVy)} over the given {@link LandingSurface}.
     */
    public static ProjectileMotion fromVelocityComponents(double initialVx, double initialVy,
                                                        double gravity, double initialX,
                                                        double initialY, LandingSurface landingSurface) {
        requireFinite("initial horizontal velocity", initialVx);
        requireFinite("initial vertical velocity", initialVy);
        double vx = initialVx == 0.0 ? 0.0 : initialVx;
        double vy = initialVy == 0.0 ? 0.0 : initialVy;
        double speed = finite(Math.hypot(vx, vy), "initial speed");
        double angleDegrees = (vx == 0.0 && vy == 0.0) ? 0.0 : Math.toDegrees(Math.atan2(vy, vx));
        return new ProjectileMotion(vx, vy, speed, angleDegrees, gravity, initialX, initialY, landingSurface);
    }

    private ProjectileMotion(double initialVx, double initialVy,
                             double initialSpeed, double launchAngleDegrees,
                             double gravity, double initialX, double initialY,
                             LandingSurface landingSurface) {
        requireFinite("initial horizontal velocity", initialVx);
        requireFinite("initial vertical velocity", initialVy);
        requireNonNegativeFinite("initial speed", initialSpeed);
        requireFinite("launch angle", launchAngleDegrees);
        requireNonNegativeFinite("gravity", gravity);
        requireFinite("initial x", initialX);
        requireFinite("initial y", initialY);
        if (landingSurface == null) {
            throw new IllegalArgumentException("landing surface is required");
        }

        if (!landingSurface.isHorizontal()) {
            double rawClearance = finite(initialY - landingSurface.heightAt(initialX),
                    "initial surface height offset");
            double scale = inclineHeightScale(initialX, initialY, landingSurface);
            double tolerance = scale == 0.0 ? 0.0 : SURFACE_ROUNDOFF_ULPS * Math.ulp(scale);
            if (rawClearance < -tolerance) {
                throw new IllegalArgumentException(
                        "Launch position cannot start strictly below an inclined landing surface");
            }
        }

        this.gravity = gravity;
        this.initialX = initialX == 0.0 ? 0.0 : initialX;
        this.initialY = initialY == 0.0 ? 0.0 : initialY;
        this.landingSurface = landingSurface;
        this.initialVx = initialVx == 0.0 ? 0.0 : initialVx;
        this.initialVy = initialVy == 0.0 ? 0.0 : initialVy;
        this.initialSpeed = initialSpeed == 0.0 ? 0.0 : initialSpeed;
        this.launchAngleDegrees = launchAngleDegrees == 0.0 ? 0.0 : launchAngleDegrees;
    }

    private static double requireNonNegativeSpeed(double initialSpeed) {
        requireNonNegativeFinite("initial speed", initialSpeed);
        return initialSpeed;
    }

    private static double requireFiniteAngle(double launchAngleDegrees) {
        requireFinite("launch angle", launchAngleDegrees);
        return launchAngleDegrees;
    }

    private static double resolveVx(double initialSpeed, double launchAngleDegrees) {
        double cosine = cosDegrees(launchAngleDegrees);
        return finite(initialSpeed * cosine, "initial horizontal velocity");
    }

    private static double resolveVy(double initialSpeed, double launchAngleDegrees) {
        double sine = sinDegrees(launchAngleDegrees);
        return finite(initialSpeed * sine, "initial vertical velocity");
    }

    private static double cosDegrees(double degrees) {
        if (degrees % 90.0 == 0.0) {
            int quadrant = Math.floorMod((int) Math.round((degrees % 360.0) / 90.0), 4);
            return switch (quadrant) { case 0 -> 1.0; case 2 -> -1.0; default -> 0.0; };
        }
        return Math.cos(Math.toRadians(degrees % 360.0));
    }

    private static double sinDegrees(double degrees) {
        if (degrees % 90.0 == 0.0) {
            int quadrant = Math.floorMod((int) Math.round((degrees % 360.0) / 90.0), 4);
            return switch (quadrant) { case 1 -> 1.0; case 3 -> -1.0; default -> 0.0; };
        }
        return Math.sin(Math.toRadians(degrees % 360.0));
    }

    public double gravity() { return gravity; }
    public double initialX() { return initialX; }
    public double initialY() { return initialY; }
    public LandingSurface landingSurface() { return landingSurface; }

    /**
     * Vertical landing coordinate for horizontal landing surfaces.
     *
     * @throws IllegalStateException if the configured landing surface is non-horizontal
     */
    public double landingY() {
        if (!landingSurface.isHorizontal()) {
            throw new IllegalStateException(
                    "Landing surface is inclined; landingY is not constant — use landingSurface()");
        }
        return landingSurface.anchorY();
    }
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

    /** First future downward crossing of the configured landing surface; empty means no future landing. */
    public OptionalDouble timeOfFlight() {
        if (landingSurface.isHorizontal()) {
            return timeOfFlightHorizontal(landingSurface.anchorY());
        }
        return timeOfFlightInclined();
    }

    /** Trajectory state at landing, or empty if no landing occurs. */
    public Optional<State> landingState() {
        OptionalDouble flight = timeOfFlight();
        return flight.isPresent() ? Optional.of(stateAt(flight.getAsDouble())) : Optional.empty();
    }

    /** Time at the unconstrained apex; empty when positive vertical motion is unbounded (g = 0). */
    public OptionalDouble timeToApex() {
        if (gravity == 0.0) {
            return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(0.0);
        }
        return OptionalDouble.of(Math.max(0.0, initialVy / gravity));
    }

    /** Greatest unconstrained vertical coordinate reached, or empty when unbounded. */
    public OptionalDouble maximumHeight() {
        if (gravity == 0.0) {
            return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(initialY);
        }
        double rise = initialVy > 0.0
                ? finite(0.5 * initialVy * (initialVy / gravity), "maximum height rise") : 0.0;
        return OptionalDouble.of(finite(initialY + rise, "maximum height"));
    }

    /**
     * Greatest vertical coordinate reached during actual flight ({@code [0, T]} when a landing
     * occurs, or {@link #maximumHeight()} otherwise). Distinct from {@link #maximumHeight()}
     * when the projectile impacts a rising surface before reaching its unconstrained apex.
     */
    public OptionalDouble maximumHeightInFlight() {
        OptionalDouble flight = timeOfFlight();
        if (flight.isEmpty()) {
            return maximumHeight();
        }
        double duration = flight.getAsDouble();
        if (duration == 0.0) {
            return OptionalDouble.of(initialY);
        }
        double endY = stateAt(duration).y();
        double best = Math.max(initialY, endY);
        if (gravity > 0.0 && initialVy > 0.0) {
            double apexTime = initialVy / gravity;
            if (apexTime < duration) {
                double apexHeight = finite(
                        Math.fma(0.5 * initialVy, apexTime, initialY),
                        "maximum height in flight");
                best = Math.max(best, apexHeight);
            }
        }
        return OptionalDouble.of(best);
    }

    public Optional<State> apexState() {
        OptionalDouble time = timeToApex();
        return time.isPresent() ? Optional.of(stateAt(time.getAsDouble())) : Optional.empty();
    }

    /** Horizontal displacement from launch to the selected landing surface. */
    public OptionalDouble horizontalRange() {
        OptionalDouble flightTime = timeOfFlight();
        if (flightTime.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(normalizeZero(finite(initialVx * flightTime.getAsDouble(), "horizontal range")));
    }

    /**
     * Non-negative distance along the landing surface between the vertical projection of the
     * launch point and the landing point, or empty when no landing occurs.
     */
    public OptionalDouble rangeAlongSurface() {
        OptionalDouble signed = signedDisplacementAlongSurface();
        if (signed.isEmpty()) return OptionalDouble.empty();
        double value = Math.abs(signed.getAsDouble());
        return OptionalDouble.of(normalizeZero(value));
    }

    /**
     * Signed displacement along the landing surface directed toward {@code +x} from the launch
     * vertical projection to landing, or empty when no landing occurs. Negative values represent
     * backward landings relative to {@code +x}.
     */
    public OptionalDouble signedDisplacementAlongSurface() {
        OptionalDouble flightTime = timeOfFlight();
        if (flightTime.isEmpty()) return OptionalDouble.empty();
        double duration = flightTime.getAsDouble();
        if (duration == 0.0) return OptionalDouble.of(0.0);
        double horizontalDisplacement = finite(initialVx * duration, "horizontal displacement to landing");
        double along = finite(horizontalDisplacement / landingSurface.cosine(), "surface displacement");
        return OptionalDouble.of(normalizeZero(along));
    }

    /**
     * Signed coordinate along the landing surface from {@code landingSurface().anchorX()} to the
     * landing point, or empty when no landing occurs.
     */
    public OptionalDouble signedCoordinateAlongSurfaceFromAnchor() {
        Optional<State> landing = landingState();
        if (landing.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(landingSurface.signedCoordinateFromAnchor(landing.get().x()));
    }

    /**
     * Signed perpendicular distance from the landing surface at {@code time}
     * (positive above the surface, zero on it, negative below it).
     */
    public double signedPerpendicularDistanceAt(double time) {
        State state = stateAt(time);
        return landingSurface.signedPerpendicularDistance(state.x(), state.y());
    }

    /**
     * Exact supremum of signed perpendicular clearance from the landing surface over the physical
     * flight domain ({@code [0, T]} if landing occurs, {@code [0, infinity)} otherwise).
     * Returns empty only when clearance grows without bound ({@code g = 0} with positive normal
     * velocity). Parallel zero-gravity motion and sub-surface flights below an unreachable ceiling
     * remain bounded and return finite values.
     */
    public OptionalDouble maxSignedPerpendicularDistance() {
        double cosBeta = landingSurface.cosine();
        double d0 = normalizedInitialPerpendicularDistance();
        double vPerp0 = normalizedInitialPerpendicularVelocity();
        double gPerp = finite(gravity * cosBeta, "normal gravity");
        OptionalDouble flight = timeOfFlight();
        if (flight.isPresent()) {
            double duration = flight.getAsDouble();
            if (duration == 0.0) {
                return OptionalDouble.of(normalizeZero(d0));
            }
            double dEnd = signedPerpendicularDistanceAt(duration);
            double endScale = perpendicularDistanceScale(stateAt(duration).x(), stateAt(duration).y());
            double endTolerance = endScale == 0.0 ? 0.0 : SURFACE_ROUNDOFF_ULPS * Math.ulp(endScale);
            if (Math.abs(dEnd) <= endTolerance) {
                dEnd = 0.0;
            }
            double best = Math.max(d0, dEnd);
            if (gPerp > 0.0 && vPerp0 > 0.0) {
                double peakTime = vPerp0 / gPerp;
                if (peakTime < duration) {
                    double rise = finite(0.5 * vPerp0 * peakTime, "perpendicular rise");
                    best = Math.max(best, finite(d0 + rise, "maximum perpendicular distance"));
                }
            }
            return OptionalDouble.of(normalizeZero(best));
        }

        if (gPerp == 0.0) {
            if (vPerp0 > 0.0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(normalizeZero(d0));
        }

        double rise = vPerp0 > 0.0
                ? finite(0.5 * vPerp0 * (vPerp0 / gPerp), "perpendicular rise")
                : 0.0;
        return OptionalDouble.of(normalizeZero(finite(d0 + rise, "maximum perpendicular distance")));
    }

    /**
     * Maximum non-negative perpendicular clearance above the landing surface over the physical flight
     * domain, or empty when clearance is unbounded above the surface ({@code g = 0, v_perp0 > 0})
     * or when the trajectory remains strictly below the surface without reaching it.
     */
    public OptionalDouble maxPerpendicularDistance() {
        OptionalDouble maxSigned = maxSignedPerpendicularDistance();
        if (maxSigned.isEmpty() || maxSigned.getAsDouble() < 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(normalizeZero(maxSigned.getAsDouble()));
    }

    private OptionalDouble timeOfFlightHorizontal(double targetLandingY) {
        TimeSolutions crossings = timesAtHeight(targetLandingY);
        if (crossings.infinitelyMany()) {
            return initialVy <= 0.0 ? OptionalDouble.of(0.0) : OptionalDouble.empty();
        }
        if (crossings.times().isEmpty()) return OptionalDouble.empty();
        if (initialY == targetLandingY && initialVy > 0.0 && gravity == 0.0) {
            return OptionalDouble.empty();
        }
        if (initialY == targetLandingY && initialVy <= 0.0) return OptionalDouble.of(0.0);
        return crossings.times().stream()
                .filter(t -> t > 0.0 && isDescendingOrTangentAt(t))
                .mapToDouble(Double::doubleValue).min();
    }

    private boolean isDescendingOrTangentAt(double time) {
        double vy = stateAt(time).vy();
        double scale = Math.abs(initialVy) + Math.abs(gravity * time);
        double tolerance = scale == 0.0 ? 0.0 : ROOT_ROUNDOFF_ULPS * Math.ulp(scale);
        return vy <= tolerance;
    }

    private OptionalDouble timeOfFlightInclined() {
        double f0 = normalizedInitialRelativeHeight();
        double vRel0 = normalizedInitialRelativeVelocity();
        if (gravity == 0.0) {
            if (f0 == 0.0) {
                return vRel0 <= 0.0 ? OptionalDouble.of(0.0) : OptionalDouble.empty();
            }
            if (f0 > 0.0) {
                return vRel0 < 0.0
                        ? OptionalDouble.of(finite(-f0 / vRel0, "inclined flight time"))
                        : OptionalDouble.empty();
            }
            return timeOfFlightHorizontal(landingSurface.anchorY());
        }

        if (f0 < 0.0) {
            return timeOfFlightHorizontal(landingSurface.anchorY());
        }
        if (f0 == 0.0) {
            if (vRel0 <= 0.0) {
                return OptionalDouble.of(0.0);
            }
            return OptionalDouble.of(finite(2.0 * (vRel0 / gravity), "inclined flight time"));
        }

        double squareTerm = finite(vRel0 * vRel0, "inclined discriminant term");
        double verticalTerm = finite(2.0 * gravity * f0, "inclined discriminant term");
        if (!Double.isFinite(verticalTerm) || !Double.isFinite(squareTerm)) {
            throw numericRange("inclined discriminant");
        }
        double discriminant = finite(Math.fma(2.0 * gravity, f0, squareTerm), "inclined discriminant");
        double root = Math.sqrt(discriminant);
        double time = vRel0 >= 0.0
                ? finite((vRel0 + root) / gravity, "inclined flight time")
                : finite((-2.0 * f0) / (vRel0 - root), "inclined flight time");
        return OptionalDouble.of(time);
    }

    private double normalizedInitialRelativeHeight() {
        double raw = finite(initialY - landingSurface.heightAt(initialX), "initial surface clearance");
        if (landingSurface.isHorizontal()) {
            return raw;
        }
        double scale = inclineHeightScale(initialX, initialY, landingSurface);
        double tolerance = scale == 0.0 ? 0.0 : SURFACE_ROUNDOFF_ULPS * Math.ulp(scale);
        return Math.abs(raw) <= tolerance ? 0.0 : raw;
    }

    private double normalizedInitialRelativeVelocity() {
        double slope = landingSurface.slopeTangent();
        double raw = finite(Math.fma(-initialVx, slope, initialVy), "relative surface velocity");
        if (landingSurface.isHorizontal()) {
            return raw;
        }
        double scale = Math.abs(initialVy) + Math.abs(initialVx * slope);
        double tolerance = scale == 0.0 ? 0.0 : ROOT_ROUNDOFF_ULPS * Math.ulp(scale);
        return Math.abs(raw) <= tolerance ? 0.0 : raw;
    }

    private double normalizedInitialPerpendicularDistance() {
        double raw = landingSurface.signedPerpendicularDistance(initialX, initialY);
        if (landingSurface.isHorizontal()) {
            return raw;
        }
        double scale = perpendicularDistanceScale(initialX, initialY);
        double tolerance = scale == 0.0 ? 0.0 : SURFACE_ROUNDOFF_ULPS * Math.ulp(scale);
        return Math.abs(raw) <= tolerance ? 0.0 : raw;
    }

    private double normalizedInitialPerpendicularVelocity() {
        double cosBeta = landingSurface.cosine();
        double sinBeta = landingSurface.sine();
        double raw = finite(Math.fma(initialVy, cosBeta, -initialVx * sinBeta), "normal surface velocity");
        if (landingSurface.isHorizontal()) {
            return raw;
        }
        double scale = Math.abs(initialVy * cosBeta) + Math.abs(initialVx * sinBeta);
        double tolerance = scale == 0.0 ? 0.0 : ROOT_ROUNDOFF_ULPS * Math.ulp(scale);
        return Math.abs(raw) <= tolerance ? 0.0 : raw;
    }

    private double perpendicularDistanceScale(double x, double y) {
        return Math.abs((x - landingSurface.anchorX()) * landingSurface.sine())
                + Math.abs((y - landingSurface.anchorY()) * landingSurface.cosine());
    }

    private static double inclineHeightScale(double x, double y, LandingSurface surface) {
        return Math.abs(y) + Math.abs(surface.anchorY())
                + Math.abs((x - surface.anchorX()) * surface.slopeTangent());
    }

    private static double normalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
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
