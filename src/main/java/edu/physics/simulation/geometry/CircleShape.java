package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Circular 2D geometric boundary centered at local origin (0, 0).
 */
public final class CircleShape implements GeometricShape {

    private final double radius;
    private final BoundingBox localBoundingBox;

    public CircleShape(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Circle radius must be finite and > 0: " + radius);
        }
        this.radius = radius;
        this.localBoundingBox = new BoundingBox(-radius, -radius, radius, radius);
    }

    public double radius() {
        return radius;
    }

    @Override
    public ShapeType shapeType() {
        return ShapeType.CIRCLE;
    }

    @Override
    public Vector2D centroid() {
        return Vector2D.ZERO;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public BoundingBox localBoundingBox() {
        return localBoundingBox;
    }

    @Override
    public BoundingBox worldBoundingBox(Transform2D transform) {
        Objects.requireNonNull(transform, "transform is required");
        Vector2D worldCenter = transform.position();
        return new BoundingBox(
                worldCenter.x() - radius,
                worldCenter.y() - radius,
                worldCenter.x() + radius,
                worldCenter.y() + radius);
    }

    @Override
    public boolean containsPoint(Vector2D localPoint) {
        Objects.requireNonNull(localPoint, "localPoint is required");
        return localPoint.magnitudeSquared() <= radius * radius + 1e-12;
    }

    @Override
    public double computeMomentOfInertia(double totalMass) {
        if (!Double.isFinite(totalMass) || totalMass <= 0.0) {
            throw new IllegalArgumentException("Total mass must be finite and > 0: " + totalMass);
        }
        // Solid uniform disc: I = 1/2 * m * r²
        return 0.5 * totalMass * radius * radius;
    }

    @Override
    public Vector2D supportPoint(Vector2D localDirection) {
        Objects.requireNonNull(localDirection, "localDirection is required");
        double mag = localDirection.magnitude();
        if (mag == 0.0) {
            return Vector2D.ZERO;
        }
        return localDirection.multiply(radius / mag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CircleShape that)) return false;
        return Double.compare(radius, that.radius) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(radius);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "CircleShape(radius=%.4f m)", radius);
    }
}

