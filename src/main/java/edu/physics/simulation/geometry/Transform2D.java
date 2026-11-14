package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable two-dimensional rigid transformation (translation and rotation).
 *
 * <p>All trigonometric values (cosine and sine) are computed internally from the validated
 * finite angle to guarantee mathematical consistency.
 *
 * <p>The position represents the world location of the body's center of mass, and angle
 * represents counter-clockwise rotation from the positive x-axis in radians.
 */
public final class Transform2D {

    public static final Transform2D IDENTITY = new Transform2D(Vector2D.ZERO, 0.0);

    private final Vector2D position;
    private final double angle;
    private final double cos;
    private final double sin;

    public Transform2D(Vector2D position, double angle) {
        this.position = Objects.requireNonNull(position, "position is required");
        if (!Double.isFinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite: " + angle);
        }
        this.angle = angle;
        this.cos = Math.cos(angle);
        this.sin = Math.sin(angle);
    }

    public static Transform2D identity() {
        return IDENTITY;
    }

    public static Transform2D translation(Vector2D position) {
        return new Transform2D(position, 0.0);
    }

    public static Transform2D rotation(double angle) {
        return new Transform2D(Vector2D.ZERO, angle);
    }

    public Vector2D position() {
        return position;
    }

    public double angle() {
        return angle;
    }

    public double cos() {
        return cos;
    }

    public double sin() {
        return sin;
    }

    /**
     * Transforms a point from local body coordinates to world coordinates:
     * x_world = position + R(angle) * x_local.
     */
    public Vector2D toWorld(Vector2D localPoint) {
        Objects.requireNonNull(localPoint, "localPoint is required");
        double x = position.x() + localPoint.x() * cos - localPoint.y() * sin;
        double y = position.y() + localPoint.x() * sin + localPoint.y() * cos;
        return new Vector2D(x, y);
    }

    /**
     * Transforms a point from world coordinates to local body coordinates:
     * x_local = R(-angle) * (x_world - position).
     */
    public Vector2D toLocal(Vector2D worldPoint) {
        Objects.requireNonNull(worldPoint, "worldPoint is required");
        double dx = worldPoint.x() - position.x();
        double dy = worldPoint.y() - position.y();
        double x = dx * cos + dy * sin;
        double y = -dx * sin + dy * cos;
        return new Vector2D(x, y);
    }

    /**
     * Rotates a direction vector from local to world space without translating.
     */
    public Vector2D transformDirection(Vector2D localDir) {
        Objects.requireNonNull(localDir, "localDir is required");
        return new Vector2D(localDir.x() * cos - localDir.y() * sin, localDir.x() * sin + localDir.y() * cos);
    }

    /**
     * Rotates a direction vector from world to local space without translating.
     */
    public Vector2D inverseTransformDirection(Vector2D worldDir) {
        Objects.requireNonNull(worldDir, "worldDir is required");
        return new Vector2D(worldDir.x() * cos + worldDir.y() * sin, -worldDir.x() * sin + worldDir.y() * cos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transform2D that)) return false;
        return Double.compare(angle, that.angle) == 0 && position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, angle);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Transform2D(pos=%s, angle=%.4f rad)", position, angle);
    }
}

