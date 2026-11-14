package edu.physics.simulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vector2D - 2D Vector Math and Operations")
class Vector2DTest {

    private static final double TOL = 1e-12;

    @Test
    @DisplayName("Constructs vector and exposes Cartesian components")
    void constructsVectorAndExposesComponents() {
        Vector2D v = new Vector2D(3.5, -4.2);
        assertEquals(3.5, v.x(), TOL);
        assertEquals(-4.2, v.y(), TOL);

        Vector2D v2 = Vector2D.of(-10.0, 20.0);
        assertEquals(-10.0, v2.x(), TOL);
        assertEquals(20.0, v2.y(), TOL);

        assertEquals(0.0, Vector2D.ZERO.x(), TOL);
        assertEquals(0.0, Vector2D.ZERO.y(), TOL);
        assertEquals(1.0, Vector2D.UNIT_X.x(), TOL);
        assertEquals(0.0, Vector2D.UNIT_X.y(), TOL);
        assertEquals(0.0, Vector2D.UNIT_Y.x(), TOL);
        assertEquals(1.0, Vector2D.UNIT_Y.y(), TOL);
    }

    @Test
    @DisplayName("Constructs vector from polar coordinates (magnitude and angle in radians)")
    void constructsFromPolarCoordinates() {
        double mag = 10.0;
        double angle = Math.PI / 6.0; // 30 degrees
        Vector2D v = Vector2D.fromPolar(mag, angle);

        assertEquals(10.0 * Math.cos(Math.PI / 6.0), v.x(), TOL);
        assertEquals(10.0 * Math.sin(Math.PI / 6.0), v.y(), TOL);
        assertEquals(mag, v.magnitude(), TOL);
    }

    @Test
    @DisplayName("Performs vector addition and subtraction correctly")
    void additionAndSubtraction() {
        Vector2D a = new Vector2D(2.0, 5.0);
        Vector2D b = new Vector2D(-3.0, 1.5);

        Vector2D sum = a.add(b);
        assertEquals(-1.0, sum.x(), TOL);
        assertEquals(6.5, sum.y(), TOL);

        Vector2D diff = a.subtract(b);
        assertEquals(5.0, diff.x(), TOL);
        assertEquals(3.5, diff.y(), TOL);
    }

    @Test
    @DisplayName("Performs scalar multiplication and division")
    void scalarMultiplicationAndDivision() {
        Vector2D v = new Vector2D(4.0, -6.0);

        Vector2D scaled = v.multiply(2.5);
        assertEquals(10.0, scaled.x(), TOL);
        assertEquals(-15.0, scaled.y(), TOL);

        Vector2D divided = v.divide(2.0);
        assertEquals(2.0, divided.x(), TOL);
        assertEquals(-3.0, divided.y(), TOL);

        assertThrows(ArithmeticException.class, () -> v.divide(0.0));
        assertThrows(ArithmeticException.class, () -> v.divide(Double.NaN));
    }

    @Test
    @DisplayName("Calculates magnitude and magnitude squared")
    void magnitudeCalculations() {
        Vector2D v = new Vector2D(3.0, 4.0);
        assertEquals(25.0, v.magnitudeSquared(), TOL);
        assertEquals(5.0, v.magnitude(), TOL);

        Vector2D zero = Vector2D.ZERO;
        assertEquals(0.0, zero.magnitudeSquared(), TOL);
        assertEquals(0.0, zero.magnitude(), TOL);
    }

    @Test
    @DisplayName("Normalizes vector to unit length and handles zero vector safely")
    void normalization() {
        Vector2D v = new Vector2D(0.0, -8.0);
        Vector2D unit = v.normalize();
        assertEquals(0.0, unit.x(), TOL);
        assertEquals(-1.0, unit.y(), TOL);
        assertEquals(1.0, unit.magnitude(), TOL);

        Vector2D zeroUnit = Vector2D.ZERO.normalize();
        assertEquals(0.0, zeroUnit.x(), TOL);
        assertEquals(0.0, zeroUnit.y(), TOL);
    }

    @Test
    @DisplayName("Calculates scalar dot product")
    void dotProduct() {
        Vector2D a = new Vector2D(2.0, 3.0);
        Vector2D b = new Vector2D(4.0, -1.0);
        // dot = 2*4 + 3*(-1) = 8 - 3 = 5
        assertEquals(5.0, a.dot(b), TOL);

        // Orthogonal vectors dot product is zero
        assertEquals(0.0, Vector2D.UNIT_X.dot(Vector2D.UNIT_Y), TOL);
    }

    @Test
    @DisplayName("Calculates 2D cross product (z-component)")
    void crossProduct2D() {
        Vector2D i = Vector2D.UNIT_X;
        Vector2D j = Vector2D.UNIT_Y;

        // i x j = 1.0 (positive counter-clockwise)
        assertEquals(1.0, i.cross(j), TOL);
        // j x i = -1.0
        assertEquals(-1.0, j.cross(i), TOL);
        // parallel vectors cross product is 0
        assertEquals(0.0, i.cross(i), TOL);

        Vector2D a = new Vector2D(2.0, 3.0);
        Vector2D b = new Vector2D(5.0, 7.0);
        // cross = 2*7 - 3*5 = 14 - 15 = -1
        assertEquals(-1.0, a.cross(b), TOL);
    }

    @Test
    @DisplayName("Calculates Euclidean distance and distance squared")
    void distanceCalculations() {
        Vector2D p1 = new Vector2D(1.0, 2.0);
        Vector2D p2 = new Vector2D(4.0, 6.0);

        assertEquals(25.0, p1.distanceSquaredTo(p2), TOL);
        assertEquals(5.0, p1.distanceTo(p2), TOL);
    }

    @Test
    @DisplayName("Negates and rotates vector correctly")
    void negationAndRotation() {
        Vector2D v = new Vector2D(2.0, -3.0);
        Vector2D neg = v.negate();
        assertEquals(-2.0, neg.x(), TOL);
        assertEquals(3.0, neg.y(), TOL);

        // Rotate (1, 0) by 90 degrees CCW gives (0, 1)
        Vector2D rotated90 = Vector2D.UNIT_X.rotate(Math.PI / 2.0);
        assertTrue(rotated90.approximatelyEquals(Vector2D.UNIT_Y, TOL));

        // Rotate by 180 degrees CCW gives (-1, 0)
        Vector2D rotated180 = Vector2D.UNIT_X.rotate(Math.PI);
        assertTrue(rotated180.approximatelyEquals(new Vector2D(-1.0, 0.0), TOL));
    }

    @Test
    @DisplayName("Clamps vector magnitude to specified maximum")
    void clampMagnitude() {
        Vector2D v = new Vector2D(6.0, 8.0); // magnitude = 10
        Vector2D clamped = v.clampMagnitude(5.0);
        assertEquals(5.0, clamped.magnitude(), TOL);
        assertEquals(3.0, clamped.x(), TOL);
        assertEquals(4.0, clamped.y(), TOL);

        // Vector with magnitude already below max is unchanged
        Vector2D small = new Vector2D(1.0, 1.0);
        assertSame(small, small.clampMagnitude(5.0));
    }

    @Test
    @DisplayName("Reflects vector off a surface normal")
    void reflection() {
        // Incident vector hitting horizontal floor going down and right: (2, -3)
        // Floor normal pointing up: (0, 1)
        // Reflected vector should be (2, 3)
        Vector2D incident = new Vector2D(2.0, -3.0);
        Vector2D reflected = incident.reflect(Vector2D.UNIT_Y);
        assertEquals(2.0, reflected.x(), TOL);
        assertEquals(3.0, reflected.y(), TOL);

        // Incident vector hitting vertical wall: (-4, 2)
        // Wall normal pointing right: (1, 0)
        // Reflected vector should be (4, 2)
        Vector2D incidentWall = new Vector2D(-4.0, 2.0);
        Vector2D reflectedWall = incidentWall.reflect(Vector2D.UNIT_X);
        assertEquals(4.0, reflectedWall.x(), TOL);
        assertEquals(2.0, reflectedWall.y(), TOL);
    }

    @Test
    @DisplayName("Checks approximate equality with custom tolerance")
    void approximatelyEquals() {
        Vector2D a = new Vector2D(1.0000000001, 2.0);
        Vector2D b = new Vector2D(1.0, 2.0000000001);
        assertTrue(a.approximatelyEquals(b, 1e-8));
        assertFalse(a.approximatelyEquals(b, 1e-12));
    }

    @Test
    @DisplayName("Implements equals, hashCode, and toString contracts")
    void equalsHashCodeAndToString() {
        Vector2D a = new Vector2D(1.5, 2.5);
        Vector2D b = new Vector2D(1.5, 2.5);
        Vector2D c = new Vector2D(1.5, 3.0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);

        String s = a.toString();
        assertTrue(s.contains("1.5000") && s.contains("2.5000"));
    }

    @Test
    @DisplayName("Rejects non-finite inputs with appropriate exceptions")
    void inputValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Vector2D(0.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Vector2D.fromPolar(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Vector2D.UNIT_X.multiply(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Vector2D.UNIT_X.rotate(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Vector2D.UNIT_X.clampMagnitude(-1.0));
        assertThrows(IllegalArgumentException.class, () -> Vector2D.UNIT_X.reflect(Vector2D.ZERO));
    }
}

