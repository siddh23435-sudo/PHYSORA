package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Geometric shape representing an idealized point mass with zero spatial extent.
 *
 * <p>A point mass has zero area, zero centroidal moment of inertia, and a degenerate
 * bounding box centered at its position.
 *
 * <p>Implemented as an immutable singleton.
 */
public final class PointMassShape implements GeometricShape {

    public static final PointMassShape INSTANCE = new PointMassShape();

    private PointMassShape() {
    }

    @Override
    public ShapeType shapeType() {
        return ShapeType.POINT_MASS;
    }

    @Override
    public Vector2D centroid() {
        return Vector2D.ZERO;
    }

    @Override
    public double area() {
        return 0.0;
    }

    @Override
    public BoundingBox localBoundingBox() {
        return BoundingBox.point(Vector2D.ZERO);
    }

    @Override
    public BoundingBox worldBoundingBox(Transform2D transform) {
        Objects.requireNonNull(transform, "Transform is required");
        return BoundingBox.point(transform.position());
    }

    /**
     * Tests whether a local body point coincides strictly with the point mass at the origin.
     *
     * <p>An idealized point mass has strictly zero spatial extent (r = 0, area = 0).
     * Both IEEE 754 positive zero (+0.0) and negative zero (-0.0) are accepted as the origin.
     * Non-origin points are not contained, introducing no artificial collision or containment radius.
     *
     * @param localPoint point in local body coordinates (must not be null)
     * @return true if localPoint coincides with the local origin (0.0, 0.0)
     */
    @Override
    public boolean containsPoint(Vector2D localPoint) {
        Objects.requireNonNull(localPoint, "localPoint is required");
        return localPoint.x() == 0.0 && localPoint.y() == 0.0;
    }

    /**
     * Centroidal moment of inertia for an idealized zero-extent point mass is strictly zero.
     *
     * @param totalMass total physical mass in kg
     * @return 0.0
     */
    @Override
    public double computeMomentOfInertia(double totalMass) {
        return 0.0;
    }

    @Override
    public Vector2D supportPoint(Vector2D localDirection) {
        return Vector2D.ZERO;
    }

    @Override
    public String toString() {
        return "PointMassShape";
    }
}

