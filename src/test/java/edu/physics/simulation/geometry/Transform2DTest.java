package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transform2D - 2D Rigid Transformation and Coordinate Mapping")
class Transform2DTest {

    private static final double TOL = 1e-12;

    @Test
    @DisplayName("Maintains mathematical integrity by computing cos and sin internally")
    void transformTrigonometricIntegrity() {
        double angle = Math.PI / 3.0; // 60 degrees
        Transform2D t = new Transform2D(new Vector2D(5.0, 10.0), angle);

        assertEquals(0.5, t.cos(), TOL);
        assertEquals(Math.sqrt(3.0) / 2.0, t.sin(), TOL);
        assertEquals(5.0, t.position().x(), TOL);
        assertEquals(10.0, t.position().y(), TOL);

        assertThrows(IllegalArgumentException.class, () -> new Transform2D(Vector2D.ZERO, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Transform2D(Vector2D.ZERO, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new Transform2D(null, 0.0));
    }

    @Test
    @DisplayName("Transforms local points to world space and inverts back losslessly")
    void localWorldPointRoundTrip() {
        Vector2D pos = new Vector2D(12.5, -8.3);
        double angle = 0.7853981633974483; // 45 degrees
        Transform2D transform = new Transform2D(pos, angle);

        Vector2D local = new Vector2D(2.0, 3.5);
        Vector2D world = transform.toWorld(local);

        double expectedX = pos.x() + local.x() * Math.cos(angle) - local.y() * Math.sin(angle);
        double expectedY = pos.y() + local.x() * Math.sin(angle) + local.y() * Math.cos(angle);
        assertEquals(expectedX, world.x(), TOL);
        assertEquals(expectedY, world.y(), TOL);

        Vector2D recoveredLocal = transform.toLocal(world);
        assertEquals(local.x(), recoveredLocal.x(), TOL);
        assertEquals(local.y(), recoveredLocal.y(), TOL);
    }

    @Test
    @DisplayName("Transforms directions without being affected by translation")
    void directionTransform() {
        Transform2D transform = new Transform2D(new Vector2D(100.0, -500.0), Math.PI / 2.0); // 90 degrees CCW

        Vector2D unitX = Vector2D.UNIT_X; // (1, 0)
        Vector2D worldDir = transform.transformDirection(unitX);

        assertEquals(0.0, worldDir.x(), TOL);
        assertEquals(1.0, worldDir.y(), TOL); // rotated to (0, 1)

        Vector2D recoveredLocal = transform.inverseTransformDirection(worldDir);
        assertEquals(unitX.x(), recoveredLocal.x(), TOL);
        assertEquals(unitX.y(), recoveredLocal.y(), TOL);
    }

    @Test
    @DisplayName("Factory methods produce identity, pure translation, and pure rotation")
    void factoryMethods() {
        Transform2D identity = Transform2D.identity();
        assertEquals(Vector2D.ZERO, identity.position());
        assertEquals(0.0, identity.angle(), TOL);
        assertEquals(1.0, identity.cos(), TOL);
        assertEquals(0.0, identity.sin(), TOL);

        Vector2D p = new Vector2D(7.0, -4.0);
        Transform2D trans = Transform2D.translation(p);
        assertEquals(p, trans.position());
        assertEquals(0.0, trans.angle(), TOL);

        Transform2D rot = Transform2D.rotation(Math.PI);
        assertEquals(Vector2D.ZERO, rot.position());
        assertEquals(Math.PI, rot.angle(), TOL);
        assertEquals(-1.0, rot.cos(), TOL);
        assertEquals(0.0, rot.sin(), TOL);
    }

    @Test
    @DisplayName("Computes bounding box in world space under rotation")
    void worldBoundingBox() {
        RectangleShape box = new RectangleShape(2.0, 2.0); // local [-1, -1] to [1, 1]
        Transform2D rot45AtOrigin = new Transform2D(new Vector2D(10.0, 20.0), Math.PI / 4.0);

        BoundingBox worldBb = box.worldBoundingBox(rot45AtOrigin);
        double sqrt2 = Math.sqrt(2.0);

        assertEquals(10.0 - sqrt2, worldBb.minX(), TOL);
        assertEquals(20.0 - sqrt2, worldBb.minY(), TOL);
        assertEquals(10.0 + sqrt2, worldBb.maxX(), TOL);
        assertEquals(20.0 + sqrt2, worldBb.maxY(), TOL);
    }
}

