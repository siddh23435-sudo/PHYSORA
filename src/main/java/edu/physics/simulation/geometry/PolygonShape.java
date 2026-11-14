package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Convex polygon 2D geometric boundary.
 *
 * <p>Enforces strict convexity, counter-clockwise (CCW) vertex ordering, scale-aware collinearity
 * rejection, and coordinate-shifted integration to prevent floating-point cancellation.
 *
 * <p>All vertices are automatically centered around the physical centroid such that
 * {@link #centroid()} returns (0, 0) in local body coordinates. The original uncentered centroid
 * is preserved via {@link #originalCentroidOffset()} to allow accurate authoring placement.
 */
public final class PolygonShape implements GeometricShape {

    private final List<Vector2D> centeredVertices;
    private final List<Vector2D> normals;
    private final double area;
    private final Vector2D originalCentroidOffset;
    private final BoundingBox localBoundingBox;

    public PolygonShape(List<Vector2D> rawVertices) {
        Objects.requireNonNull(rawVertices, "rawVertices is required");
        int n = rawVertices.size();
        if (n < 3) {
            throw new IllegalArgumentException("Polygon must have at least 3 vertices, got: " + n);
        }

        // 1. Verify all coordinates are finite
        for (int i = 0; i < n; i++) {
            Vector2D v = rawVertices.get(i);
            Objects.requireNonNull(v, "Vertex at index " + i + " cannot be null");
            if (!Double.isFinite(v.x()) || !Double.isFinite(v.y())) {
                throw new IllegalArgumentException("Vertex at index " + i + " contains non-finite values: " + v);
            }
        }

        // 2. Coordinate-shifted integration relative to v0 to eliminate catastrophic floating-point cancellation
        Vector2D v0 = rawVertices.get(0);
        double shiftedAreaAccumulator = 0.0;
        double shiftedCxAccumulator = 0.0;
        double shiftedCyAccumulator = 0.0;

        for (int i = 0; i < n; i++) {
            Vector2D curr = rawVertices.get(i).subtract(v0);
            Vector2D next = rawVertices.get((i + 1) % n).subtract(v0);

            double cross = curr.x() * next.y() - next.x() * curr.y();
            shiftedAreaAccumulator += cross;
            shiftedCxAccumulator += (curr.x() + next.x()) * cross;
            shiftedCyAccumulator += (curr.y() + next.y()) * cross;
        }

        double calculatedArea = 0.5 * shiftedAreaAccumulator;

        // Reject clockwise or zero area
        if (calculatedArea <= 0.0) {
            throw new IllegalArgumentException(
                    "Polygon vertices must be in counter-clockwise order with positive area. Area=" + calculatedArea);
        }

        this.area = calculatedArea;

        // Centroid in raw coordinate space
        double localCx = shiftedCxAccumulator / (6.0 * calculatedArea);
        double localCy = shiftedCyAccumulator / (6.0 * calculatedArea);
        this.originalCentroidOffset = new Vector2D(v0.x() + localCx, v0.y() + localCy);

        // 3. Center all vertices around the centroid
        List<Vector2D> centered = new ArrayList<>(n);
        double maxRadiusSq = 0.0;
        for (Vector2D v : rawVertices) {
            Vector2D c = v.subtract(this.originalCentroidOffset);
            centered.add(c);
            maxRadiusSq = Math.max(maxRadiusSq, c.magnitudeSquared());
        }
        this.centeredVertices = Collections.unmodifiableList(centered);

        double characteristicSize = Math.sqrt(maxRadiusSq);
        double scaleSq = Math.max(characteristicSize * characteristicSize, 1e-12);
        double crossTolerance = 1e-10 * scaleSq;

        // 4. Scale-aware convexity and collinearity verification
        List<Vector2D> outwardNormals = new ArrayList<>(n);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            Vector2D curr = centeredVertices.get(i);
            Vector2D next = centeredVertices.get((i + 1) % n);
            Vector2D nextNext = centeredVertices.get((i + 2) % n);

            Vector2D edge1 = next.subtract(curr);
            Vector2D edge2 = nextNext.subtract(next);

            double cross = edge1.cross(edge2);
            if (cross <= crossTolerance) {
                throw new IllegalArgumentException(String.format(
                        java.util.Locale.ROOT,
                        "Polygon is not strictly convex or contains collinear/concave vertices at vertex %d (cross=%.4e, tol=%.4e)",
                        (i + 1) % n, cross, crossTolerance));
            }

            // Outward normal in CCW order is (dy, -dx) / len
            double edgeLen = edge1.magnitude();
            if (edgeLen < 1e-12) {
                throw new IllegalArgumentException("Polygon contains duplicate consecutive vertices at index " + i);
            }
            Vector2D normal = new Vector2D(edge1.y() / edgeLen, -edge1.x() / edgeLen);
            outwardNormals.add(normal);

            if (curr.x() < minX) minX = curr.x();
            if (curr.y() < minY) minY = curr.y();
            if (curr.x() > maxX) maxX = curr.x();
            if (curr.y() > maxY) maxY = curr.y();
        }

        this.normals = Collections.unmodifiableList(outwardNormals);
        this.localBoundingBox = new BoundingBox(minX, minY, maxX, maxY);
    }

    public static PolygonShape of(List<Vector2D> vertices) {
        return new PolygonShape(vertices);
    }

    /**
     * Factory for a right triangle with base b and height h in standard JEE incline orientation:
     * raw vertices at (0, 0), (base, 0), (0, height).
     */
    public static PolygonShape createRightTriangle(double base, double height) {
        if (!Double.isFinite(base) || base <= 0.0) {
            throw new IllegalArgumentException("Triangle base must be finite and > 0: " + base);
        }
        if (!Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("Triangle height must be finite and > 0: " + height);
        }
        return new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(base, 0.0),
                new Vector2D(0.0, height)));
    }

    public List<Vector2D> centeredVertices() {
        return centeredVertices;
    }

    public List<Vector2D> localNormals() {
        return normals;
    }

    /**
     * The raw centroid offset relative to original authored input coordinates.
     */
    public Vector2D originalCentroidOffset() {
        return originalCentroidOffset;
    }

    @Override
    public ShapeType shapeType() {
        return ShapeType.POLYGON;
    }

    @Override
    public Vector2D centroid() {
        return Vector2D.ZERO;
    }

    @Override
    public double area() {
        return area;
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

        for (Vector2D v : centeredVertices) {
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
        int n = centeredVertices.size();
        for (int i = 0; i < n; i++) {
            Vector2D curr = centeredVertices.get(i);
            Vector2D next = centeredVertices.get((i + 1) % n);
            Vector2D edge = next.subtract(curr);
            Vector2D toPoint = localPoint.subtract(curr);
            // In CCW polygon, point is inside if on the left of every edge: edge x toPoint >= 0
            if (edge.cross(toPoint) < -1e-11) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates centroidal moment of inertia for a uniform-density planar polygon:
     * I_cm = (M / (12 * A)) * Σ c_i * Q_i
     * where c_i = x_i * y_{i+1} - x_{i+1} * y_i
     * and Q_i = x_i² + x_i * x_{i+1} + x_{i+1}² + y_i² + y_i * y_{i+1} + y_{i+1}².
     */
    @Override
    public double computeMomentOfInertia(double totalMass) {
        if (!Double.isFinite(totalMass) || totalMass <= 0.0) {
            throw new IllegalArgumentException("Total mass must be finite and > 0: " + totalMass);
        }
        int n = centeredVertices.size();
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            Vector2D v1 = centeredVertices.get(i);
            Vector2D v2 = centeredVertices.get((i + 1) % n);

            double cross = v1.x() * v2.y() - v2.x() * v1.y();
            double qX = v1.x() * v1.x() + v1.x() * v2.x() + v2.x() * v2.x();
            double qY = v1.y() * v1.y() + v1.y() * v2.y() + v2.y() * v2.y();

            sum += cross * (qX + qY);
        }

        return (totalMass / (12.0 * area)) * sum;
    }

    @Override
    public Vector2D supportPoint(Vector2D localDirection) {
        Objects.requireNonNull(localDirection, "localDirection is required");
        double bestDot = Double.NEGATIVE_INFINITY;
        Vector2D bestVertex = centeredVertices.get(0);
        for (Vector2D v : centeredVertices) {
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
        if (!(o instanceof PolygonShape that)) return false;
        return centeredVertices.equals(that.centeredVertices);
    }

    @Override
    public int hashCode() {
        return centeredVertices.hashCode();
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "PolygonShape(vertices=%d, area=%.4f m²)", centeredVertices.size(), area);
    }
}

