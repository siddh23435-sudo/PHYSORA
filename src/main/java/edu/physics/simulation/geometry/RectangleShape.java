package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.List;
import java.util.Objects;

/**
 * Centered rectangular 2D geometric boundary.
 *
 * <p>Centered at local origin (0, 0) with 4 vertices ordered counter-clockwise (CCW):
 * <ul>
 *   <li>v0 = (-w/2, -h/2)</li>
 *   <li>v1 = (+w/2, -h/2)</li>
 *   <li>v2 = (+w/2, +h/2)</li>
 *   <li>v3 = (-w/2, +h/2)</li>
 * </ul>
 */
public final class RectangleShape implements GeometricShape {

    private final double width;
    private final double height;
    private final BoundingBox localBoundingBox;
    private final List<Vector2D> vertices;
    private final List<Vector2D> normals;

    public RectangleShape(double width, double height) {
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("Width must be finite and > 0: " + width);
        }
        if (!Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("Height must be finite and > 0: " + height);
        }
        this.width = width;
        this.height = height;

        double hw = 0.5 * width;
        double hh = 0.5 * height;

        this.localBoundingBox = new BoundingBox(-hw, -hh, hw, hh);

        this.vertices = List.of(
                new Vector2D(-hw, -hh),
                new Vector2D(hw, -hh),
                new Vector2D(hw, hh),
                new Vector2D(-hw, hh));

        // Outward normals corresponding to bottom, right, top, left edges
        this.normals = List.of(
                new Vector2D(0.0, -1.0),
                new Vector2D(1.0, 0.0),
                new Vector2D(0.0, 1.0),
                new Vector2D(-1.0, 0.0));
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public List<Vector2D> localVertices() {
        return vertices;
    }

    public List<Vector2D> localNormals() {
        return normals;
    }

    @Override
    public ShapeType shapeType() {
        return ShapeType.RECTANGLE;
    }

    @Override
    public Vector2D centroid() {
        return Vector2D.ZERO;
    }

    @Override
    public double area() {
        return width * height;
    }

    @Override
    public BoundingBox localBoundingBox() {
        return localBoundingBox;
    }

    @Override
    public BoundingBox worldBoundingBox(Transform2D transform) {
        Objects.requireNonNull(transform, "transform is required");
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Vector2D v : vertices) {
            Vector2D wv = transform.toWorld(v);
            if (wv.x() < minX) minX = wv.x();
            if (wv.y() < minY) minY = wv.y();
            if (wv.x() > maxX) maxX = wv.x();
            if (wv.y() > maxY) maxY = wv.y();
        }
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    @Override
    public boolean containsPoint(Vector2D localPoint) {
        Objects.requireNonNull(localPoint, "localPoint is required");
        double hw = 0.5 * width + 1e-12;
        double hh = 0.5 * height + 1e-12;
        return Math.abs(localPoint.x()) <= hw && Math.abs(localPoint.y()) <= hh;
    }

    @Override
    public double computeMomentOfInertia(double totalMass) {
        if (!Double.isFinite(totalMass) || totalMass <= 0.0) {
            throw new IllegalArgumentException("Total mass must be finite and > 0: " + totalMass);
        }
        // Uniform rectangular plate: I = 1/12 * m * (w² + h²)
        return (1.0 / 12.0) * totalMass * (width * width + height * height);
    }

    @Override
    public Vector2D supportPoint(Vector2D localDirection) {
        Objects.requireNonNull(localDirection, "localDirection is required");
        double bestDot = Double.NEGATIVE_INFINITY;
        Vector2D bestVertex = vertices.get(0);
        for (Vector2D v : vertices) {
            double dot = v.dot(localDirection);
            if (dot > bestDot) {
                bestDot = dot;
                bestVertex = v;
            }
        }
        return bestVertex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RectangleShape that)) return false;
        return Double.compare(width, that.width) == 0 && Double.compare(height, that.height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "RectangleShape(width=%.4f m, height=%.4f m)", width, height);
    }
}

