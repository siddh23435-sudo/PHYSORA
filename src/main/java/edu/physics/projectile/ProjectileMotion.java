package edu.physics.projectile;

import java.util.OptionalDouble;

/**
 * Analytical two-dimensional projectile motion with constant downward gravity
 * and no air resistance. All distances are metres, time is seconds, speed is
 * metres per second, gravity is metres per second squared, and the launch angle
 * is measured in degrees counterclockwise from the positive horizontal axis.
 */
public final class ProjectileMotion {
    /** Position and velocity at one instant, expressed in SI units. */
    public record State(double x, double y, double vx, double vy) {
    }

    private final double gravity;
    private final double initialX;
    private final double initialY;
    private final double landingY;
    private final double initialVx;
    private final double initialVy;

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
        double launchAngleRadians = Math.toRadians(launchAngleDegrees);
        this.initialVx = initialSpeed * Math.cos(launchAngleRadians);
        this.initialVy = initialSpeed * Math.sin(launchAngleRadians);
    }

    public State stateAt(double time) {
        requireFinite("time", time);
        if (time < 0.0) {
            throw new IllegalArgumentException("Time must be non-negative");
        }
        return new State(
                initialX + initialVx * time,
                initialY + initialVy * time - 0.5 * gravity * time * time,
                initialVx,
                initialVy - gravity * time);
    }

    /**
     * Returns the first future time at which the projectile reaches the landing
     * height. A launch upward from that height returns the later crossing; a
     * launch downward from it has a flight time of zero. Empty means no crossing.
     */
    public OptionalDouble timeOfFlight() {
        double heightDifference = initialY - landingY;
        if (gravity == 0.0) {
            if (initialVy == 0.0) {
                return heightDifference == 0.0
                        ? OptionalDouble.of(0.0)
                        : OptionalDouble.empty();
            }
            if (heightDifference == 0.0) {
                return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(0.0);
            }
            double time = -heightDifference / initialVy;
            return time >= 0.0 ? OptionalDouble.of(time) : OptionalDouble.empty();
        }

        double discriminant = initialVy * initialVy + 2.0 * gravity * heightDifference;
        if (discriminant < 0.0) {
            return OptionalDouble.empty();
        }

        double root = Math.sqrt(discriminant);
        double t1 = (initialVy - root) / gravity;
        double t2 = (initialVy + root) / gravity;
        double earliest = Double.POSITIVE_INFINITY;
        if (t1 >= 0.0 && !(t1 == 0.0 && initialVy > 0.0)) {
            earliest = t1;
        }
        if (t2 >= 0.0 && !(t2 == 0.0 && initialVy > 0.0)) {
            earliest = Math.min(earliest, t2);
        }
        return Double.isFinite(earliest)
                ? OptionalDouble.of(earliest)
                : OptionalDouble.empty();
    }

    /** Returns the greatest vertical coordinate reached, or empty if unbounded. */
    public OptionalDouble maximumHeight() {
        if (gravity == 0.0) {
            return initialVy > 0.0 ? OptionalDouble.empty() : OptionalDouble.of(initialY);
        }
        double upwardSpeed = Math.max(0.0, initialVy);
        return OptionalDouble.of(initialY + upwardSpeed * upwardSpeed / (2.0 * gravity));
    }

    /**
     * Horizontal displacement from launch to the selected landing height, if
     * reached.
     */
    public OptionalDouble horizontalRange() {
        OptionalDouble flightTime = timeOfFlight();
        return flightTime.isPresent()
                ? OptionalDouble.of(initialVx * flightTime.getAsDouble())
                : OptionalDouble.empty();
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
