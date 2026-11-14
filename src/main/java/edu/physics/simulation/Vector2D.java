package edu.physics.simulation;

import java.util.Objects;

/**
 * Immutable two-dimensional Euclidean vector for coordinates, velocities, accelerations, and forces.
 *
 * <p>All components use standard SI units (meters, m/s, m/s², Newtons).
 * The coordinate system is Cartesian with positive x rightward and positive y vertical upward.
 */
public final class Vector2D {

    public static final Vector2D ZERO = new Vector2D(0.0, 0.0);
    public static final Vector2D UNIT_X = new Vector2D(1.0, 0.0);
    public static final Vector2D UNIT_Y = new Vector2D(0.0, 1.0);

    private final double x;
    private final double y;

    public Vector2D(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Vector components must be finite: (" + x + ", " + y + ")");
        }
        this.x = x;
        this.y = y;
    }

    public static Vector2D of(double x, double y) {
        return new Vector2D(x, y);
    }

    public static Vector2D fromPolar(double magnitude, double angleRadians) {
        if (!Double.isFinite(magnitude) || !Double.isFinite(angleRadians)) {
            throw new IllegalArgumentException("Polar components must be finite: magnitude=" + magnitude
                    + ", angle=" + angleRadians);
        }
        return new Vector2D(magnitude * Math.cos(angleRadians), magnitude * Math.sin(angleRadians));
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public Vector2D add(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D multiply(double scalar) {
        if (!Double.isFinite(scalar)) {
            throw new IllegalArgumentException("Scalar must be finite: " + scalar);
        }
        return new Vector2D(x * scalar, y * scalar);
    }

    public Vector2D divide(double scalar) {
        if (!Double.isFinite(scalar) || scalar == 0.0) {
            throw new ArithmeticException("Cannot divide a vector by zero or non-finite scalar: " + scalar);
        }
        return new Vector2D(x / scalar, y / scalar);
    }

    public double magnitude() {
        return Math.hypot(x, y);
    }

    public double magnitudeSquared() {
        return x * x + y * y;
    }

    public Vector2D normalize() {
        double len = magnitude();
        if (len == 0.0) {
            return ZERO;
        }
        return new Vector2D(x / len, y / len);
    }

    public double dot(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        return x * other.x + y * other.y;
    }

    /**
     * 2D scalar cross product (z-component of 3D cross product): x1 * y2 - y1 * x2.
     * Positive indicates a counter-clockwise rotation from this vector to other.
     */
    public double cross(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        return x * other.y - y * other.x;
    }

    public double distanceTo(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        return Math.hypot(x - other.x, y - other.y);
    }

    public double distanceSquaredTo(Vector2D other) {
        Objects.requireNonNull(other, "other vector is required");
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }

    public Vector2D negate() {
        return new Vector2D(-x, -y);
    }

    public Vector2D rotate(double angleRadians) {
        if (!Double.isFinite(angleRadians)) {
            throw new IllegalArgumentException("Angle must be finite: " + angleRadians);
        }
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }

    public Vector2D clampMagnitude(double maxMagnitude) {
        if (!Double.isFinite(maxMagnitude) || maxMagnitude < 0.0) {
            throw new IllegalArgumentException("maxMagnitude must be finite and >= 0: " + maxMagnitude);
        }
        double len = magnitude();
        if (len <= maxMagnitude) {
            return this;
        }
        return multiply(maxMagnitude / len);
    }

    public Vector2D reflect(Vector2D normal) {
        Objects.requireNonNull(normal, "normal is required");
        Vector2D n = normal.normalize();
        if (n.equals(ZERO)) {
            throw new IllegalArgumentException("Cannot reflect about a zero-length normal");
        }
        return subtract(n.multiply(2.0 * dot(n)));
    }

    public boolean approximatelyEquals(Vector2D other, double epsilon) {
        Objects.requireNonNull(other, "other vector is required");
        if (!Double.isFinite(epsilon) || epsilon < 0.0) {
            throw new IllegalArgumentException("epsilon must be finite and >= 0: " + epsilon);
        }
        return Math.abs(x - other.x) <= epsilon && Math.abs(y - other.y) <= epsilon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector2D other)) return false;
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Vector2D(%.4f, %.4f)", x, y);
    }
}

