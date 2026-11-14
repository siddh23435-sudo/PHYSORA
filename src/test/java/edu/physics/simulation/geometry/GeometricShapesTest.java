package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeometricShapes - Circles, Rectangles, and Convex Polygons")
class GeometricShapesTest {

    private static final double TOL = 1e-10;

    @Test
    @DisplayName("Critical regression: General polygon moment of inertia matches right triangle M/18*(b² + h²)")
    void polygonInertiaMatchesRightTriangleFormula() {
        double b = 3.0;
        double h = 4.0;
        double mass = 12.0;

        // 1. Analytical benchmark: I_cm = M / 18 * (b² + h²)
        double expectedAnalyticalInertia = (mass / 18.0) * (b * b + h * h);
        // (12.0 / 18.0) * (9.0 + 16.0) = (2/3) * 25.0 = 16.666666666666668 kg·m²
        assertEquals(50.0 / 3.0, expectedAnalyticalInertia, 1e-12);

        // 2. Constructed via factory (evaluates M / (12*A) * sum(c_i * Q_i) on centered vertices)
        PolygonShape triangle = PolygonShape.createRightTriangle(b, h);
        double computedInertia = triangle.computeMomentOfInertia(mass);

        assertEquals(expectedAnalyticalInertia, computedInertia, TOL,
                "General polygon formula M/(12A)*sum(c_i*Q_i) must match exact right triangle M/18*(b²+h²)");
        assertEquals(6.0, triangle.area(), 1e-12); // Area = 1/2 * 3 * 4 = 6
    }

    @Test
    @DisplayName("Polygon centers vertices around centroid and exposes original centroid offset")
    void polygonAutomaticCentering() {
        // Triangle with raw vertices (0,0), (6,0), (0,3)
        // Area = 1/2 * 6 * 3 = 9.0 m²
        // Centroid = (6/3, 3/3) = (2.0, 1.0)
        PolygonShape poly = new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(6.0, 0.0),
                new Vector2D(0.0, 3.0)));

        assertEquals(Vector2D.ZERO, poly.centroid());
        assertEquals(9.0, poly.area(), TOL);

        Vector2D offset = poly.originalCentroidOffset();
        assertEquals(2.0, offset.x(), TOL);
        assertEquals(1.0, offset.y(), TOL);

        // Centered vertices should be raw - (2, 1)
        List<Vector2D> centered = poly.centeredVertices();
        assertEquals(new Vector2D(-2.0, -1.0), centered.get(0));
        assertEquals(new Vector2D(4.0, -1.0), centered.get(1));
        assertEquals(new Vector2D(-2.0, 2.0), centered.get(2));
    }

    @Test
    @DisplayName("Polygon shifted far from origin maintains numerical stability without floating-point cancellation")
    void polygonCoordinateShiftedStabilityFarFromOrigin() {
        // Triangle shifted by (100,000 m, 100,000 m)
        double shift = 100000.0;
        PolygonShape shiftedTriangle = new PolygonShape(List.of(
                new Vector2D(shift, shift),
                new Vector2D(shift + 3.0, shift),
                new Vector2D(shift, shift + 4.0)));

        PolygonShape baseTriangle = PolygonShape.createRightTriangle(3.0, 4.0);

        assertEquals(6.0, shiftedTriangle.area(), 1e-11);
        assertEquals(baseTriangle.area(), shiftedTriangle.area(), 1e-11);

        double mass = 12.0;
        assertEquals(baseTriangle.computeMomentOfInertia(mass),
                shiftedTriangle.computeMomentOfInertia(mass), 1e-10);
    }

    @Test
    @DisplayName("Scale-aware tolerance validates millimeter-scale and kilometer-scale polygons without false rejection")
    void polygonScaleAwareSmallAndLarge() {
        // Millimeter triangle: b = 0.003 m (3 mm), h = 0.004 m (4 mm)
        PolygonShape mmTriangle = PolygonShape.createRightTriangle(0.003, 0.004);
        assertEquals(0.5 * 0.003 * 0.004, mmTriangle.area(), 1e-14);

        // Kilometer triangle: b = 3000 m, h = 4000 m
        PolygonShape kmTriangle = PolygonShape.createRightTriangle(3000.0, 4000.0);
        assertEquals(0.5 * 3000.0 * 4000.0, kmTriangle.area(), 1e-5);
    }

    @Test
    @DisplayName("Rejects degenerate, concave, clockwise, collinear, and non-finite polygons")
    void polygonRejectionOfDegeneracies() {
        // 1. Clockwise vertices: (0,0), (0,3), (3,0)
        assertThrows(IllegalArgumentException.class, () -> new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(0.0, 3.0),
                new Vector2D(3.0, 0.0))));

        // 2. Collinear vertices along bottom edge: (0,0), (2,0), (4,0), (0,3)
        assertThrows(IllegalArgumentException.class, () -> new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(2.0, 0.0),
                new Vector2D(4.0, 0.0),
                new Vector2D(0.0, 3.0))));

        // 3. Concave dart: (0,0), (4,0), (1,1), (0,4)
        assertThrows(IllegalArgumentException.class, () -> new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(4.0, 0.0),
                new Vector2D(1.0, 1.0),
                new Vector2D(0.0, 4.0))));

        // 4. Non-finite coordinate
        assertThrows(IllegalArgumentException.class, () -> new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(Double.NaN, 0.0),
                new Vector2D(0.0, 3.0))));

        // 5. Fewer than 3 vertices
        assertThrows(IllegalArgumentException.class, () -> new PolygonShape(List.of(
                new Vector2D(0.0, 0.0),
                new Vector2D(1.0, 1.0))));
    }

    @Test
    @DisplayName("CircleShape evaluates area, moment of inertia, containment, and support points")
    void circleProperties() {
        double r = 0.5;
        double mass = 4.0;
        CircleShape circle = new CircleShape(r);

        assertEquals(ShapeType.CIRCLE, circle.shapeType());
        assertEquals(Vector2D.ZERO, circle.centroid());
        assertEquals(Math.PI * 0.25, circle.area(), TOL);

        // I = 1/2 * m * r² = 0.5 * 4 * 0.25 = 0.5 kg·m²
        assertEquals(0.5, circle.computeMomentOfInertia(mass), TOL);

        // Containment
        assertTrue(circle.containsPoint(new Vector2D(0.3, 0.4))); // magnitude = 0.5 (on boundary)
        assertTrue(circle.containsPoint(new Vector2D(0.0, 0.0)));
        assertFalse(circle.containsPoint(new Vector2D(0.31, 0.4)));

        // Support point in direction (3, 4)
        Vector2D sup = circle.supportPoint(new Vector2D(3.0, 4.0));
        assertEquals(0.3, sup.x(), TOL);
        assertEquals(0.4, sup.y(), TOL);
    }

    @Test
    @DisplayName("RectangleShape evaluates area, moment of inertia, local vertices, normals, and containment")
    void rectangleProperties() {
        double w = 2.0;
        double h = 3.0;
        double mass = 12.0;
        RectangleShape rect = new RectangleShape(w, h);

        assertEquals(ShapeType.RECTANGLE, rect.shapeType());
        assertEquals(Vector2D.ZERO, rect.centroid());
        assertEquals(6.0, rect.area(), TOL);

        // I = 1/12 * m * (w² + h²) = 1/12 * 12 * (4 + 9) = 13.0 kg·m²
        assertEquals(13.0, rect.computeMomentOfInertia(mass), TOL);

        // Vertices at (-1, -1.5), (1, -1.5), (1, 1.5), (-1, 1.5)
        List<Vector2D> v = rect.localVertices();
        assertEquals(new Vector2D(-1.0, -1.5), v.get(0));
        assertEquals(new Vector2D(1.0, -1.5), v.get(1));
        assertEquals(new Vector2D(1.0, 1.5), v.get(2));
        assertEquals(new Vector2D(-1.0, 1.5), v.get(3));

        // Containment
        assertTrue(rect.containsPoint(new Vector2D(0.99, 1.49)));
        assertTrue(rect.containsPoint(new Vector2D(1.0, 1.5)));
        assertFalse(rect.containsPoint(new Vector2D(1.01, 0.0)));
        assertFalse(rect.containsPoint(new Vector2D(0.0, 1.51)));

        // Support point in direction (1, 1) should be top-right vertex (1, 1.5)
        Vector2D sup = rect.supportPoint(new Vector2D(1.0, 1.0));
        assertEquals(1.0, sup.x(), TOL);
        assertEquals(1.5, sup.y(), TOL);
    }

    @Test
    @DisplayName("PointMassShape satisfies ideal zero-extent particle contract and strict origin containment")
    void pointMassShapeContract() {
        PointMassShape point = PointMassShape.INSTANCE;

        assertSame(PointMassShape.INSTANCE, point);
        assertEquals(ShapeType.POINT_MASS, point.shapeType());
        assertEquals(Vector2D.ZERO, point.centroid());
        assertEquals(0.0, point.area(), TOL);
        assertEquals(0.0, point.computeMomentOfInertia(50.0), TOL);

        BoundingBox localBb = point.localBoundingBox();
        assertEquals(0.0, localBb.minX(), TOL);
        assertEquals(0.0, localBb.maxX(), TOL);
        assertEquals(0.0, localBb.minY(), TOL);
        assertEquals(0.0, localBb.maxY(), TOL);

        Transform2D transform = new Transform2D(new Vector2D(7.5, -3.2), Math.PI / 4.0);
        BoundingBox worldBb = point.worldBoundingBox(transform);
        assertEquals(7.5, worldBb.minX(), TOL);
        assertEquals(7.5, worldBb.maxX(), TOL);
        assertEquals(-3.2, worldBb.minY(), TOL);
        assertEquals(-3.2, worldBb.maxY(), TOL);

        // 1. Origin is strictly contained
        Vector2D origin = Vector2D.ZERO;
        assertTrue(point.containsPoint(origin));

        // 2. Negative-zero coordinates are accepted
        assertTrue(point.containsPoint(new Vector2D(-0.0, 0.0)));
        assertTrue(point.containsPoint(new Vector2D(0.0, -0.0)));
        assertTrue(point.containsPoint(new Vector2D(-0.0, -0.0)));

        // 3. Sub-micrometre non-origin points are NOT contained (zero effective radius)
        Vector2D offOriginX = new Vector2D(1e-7, 0.0);
        Vector2D offOriginY = new Vector2D(0.0, 1e-7);
        assertFalse(point.containsPoint(offOriginX));
        assertFalse(point.containsPoint(offOriginY));
        assertFalse(point.containsPoint(new Vector2D(-1e-7, 0.0)));
        assertFalse(point.containsPoint(new Vector2D(0.0, -1e-7)));
        assertFalse(point.containsPoint(new Vector2D(0.1, 0.1)));

        // 4. Bounding-box and containment semantics remain strictly consistent
        assertTrue(localBb.contains(origin));
        assertTrue(point.containsPoint(origin));
        assertFalse(localBb.contains(offOriginX));
        assertFalse(point.containsPoint(offOriginX));
        assertFalse(localBb.contains(offOriginY));
        assertFalse(point.containsPoint(offOriginY));

        // 5. NaN and infinite coordinates are rejected at Vector2D boundary
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(0.0, Double.NEGATIVE_INFINITY));

        // 6. Support point is always origin
        assertEquals(Vector2D.ZERO, point.supportPoint(new Vector2D(1.0, 0.0)));
        assertEquals(Vector2D.ZERO, point.supportPoint(new Vector2D(-5.0, 10.0)));
    }
}

