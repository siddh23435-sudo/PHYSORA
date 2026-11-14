package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Axis-Aligned Bounding Box (AABB) in 2D Cartesian space.
 */
public record BoundingBox(double minX, double minY, double maxX, double maxY) {

    public BoundingBox {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            throw new IllegalArgumentException("BoundingBox coordinates must be finite: [" + minX + ", " + minY
                    + "] to [" + maxX + ", " + maxY + "]");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("min coordinates cannot exceed max: min=[" + minX + ", " + minY
                    + "], max=[" + maxX + ", " + maxY + "]");
        }
    }

    public static BoundingBox of(double minX, double minY, double maxX, double maxY) {
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    public static BoundingBox point(Vector2D p) {
        Objects.requireNonNull(p, "point is required");
        return new BoundingBox(p.x(), p.y(), p.x(), p.y());
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public Vector2D center() {
        return new Vector2D(0.5 * (minX + maxX), 0.5 * (minY + maxY));
    }

    public boolean contains(Vector2D point) {
        Objects.requireNonNull(point, "point is required");
        return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY;
    }

    public boolean intersects(BoundingBox other) {
        Objects.requireNonNull(other, "other BoundingBox is required");
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY;
    }

    public BoundingBox expand(Vector2D point) {
        Objects.requireNonNull(point, "point is required");
        return new BoundingBox(
                Math.min(minX, point.x()),
                Math.min(minY, point.y()),
                Math.max(maxX, point.x()),
                Math.max(maxY, point.y()));
    }
}

