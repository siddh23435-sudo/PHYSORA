package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReferenceFrame & TwoProjectilePursuitModel - Mathematical & Transformation Tests")
class ReferenceFrameTest {

    @Test
    @DisplayName("Observer A is strictly stationary at origin in Observer A frame")
    void testObserverAStationaryInFrameA() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        for (double t = 0.0; t <= model.effectiveDuration(); t += 0.5) {
            Vector2D posA = model.positionInFrame(true, t, ReferenceFrame.OBSERVER_A);
            Vector2D velA = model.velocityInFrame(true, t, ReferenceFrame.OBSERVER_A);

            assertEquals(0.0, posA.x(), 1e-12, "posA.x in frame A must be identically 0 at t=" + t);
            assertEquals(0.0, posA.y(), 1e-12, "posA.y in frame A must be identically 0 at t=" + t);
            assertEquals(0.0, velA.x(), 1e-12, "velA.x in frame A must be identically 0 at t=" + t);
            assertEquals(0.0, velA.y(), 1e-12, "velA.y in frame A must be identically 0 at t=" + t);
        }
    }

    @Test
    @DisplayName("Observer B is strictly stationary at origin in Observer B frame")
    void testObserverBStationaryInFrameB() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        for (double t = 0.0; t <= model.effectiveDuration(); t += 0.5) {
            Vector2D posB = model.positionInFrame(false, t, ReferenceFrame.OBSERVER_B);
            Vector2D velB = model.velocityInFrame(false, t, ReferenceFrame.OBSERVER_B);

            assertEquals(0.0, posB.x(), 1e-12, "posB.x in frame B must be identically 0 at t=" + t);
            assertEquals(0.0, posB.y(), 1e-12, "posB.y in frame B must be identically 0 at t=" + t);
            assertEquals(0.0, velB.x(), 1e-12, "velB.x in frame B must be identically 0 at t=" + t);
            assertEquals(0.0, velB.y(), 1e-12, "velB.y in frame B must be identically 0 at t=" + t);
        }
    }

    @Test
    @DisplayName("Relative velocity vector remains strictly constant under uniform downward gravity")
    void testConstantRelativeVelocityUnderGravity() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        Vector2D vRelInitial = model.relativeVelocityBA(0.0);

        for (double t = 0.1; t <= model.effectiveDuration(); t += 0.4) {
            Vector2D vRelCurrent = model.relativeVelocityBA(t);
            // v_B - v_A = (-20 - 20) i + (vyB0 - gt - (vyA0 - gt)) j = -40 i + 0 j
            assertEquals(vRelInitial.x(), vRelCurrent.x(), 1e-12, "vRel.x must be invariant");
            assertEquals(vRelInitial.y(), vRelCurrent.y(), 1e-12, "vRel.y must be invariant (gravity cancelled!)");
        }
    }

    @Test
    @DisplayName("Velocity triangle vector identity holds continuously: v_B(t) = v_A(t) + v_{B/A}")
    void testVelocityTriangleVectorIdentity() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        for (double t = 0.0; t <= model.effectiveDuration(); t += 0.25) {
            Vector2D vA = model.velocityA(t);
            Vector2D vB = model.velocityB(t);
            Vector2D vBA = model.relativeVelocityBA(t);

            // Vector addition: vA + vBA == vB
            Vector2D sum = vA.add(vBA);
            assertEquals(vB.x(), sum.x(), 1e-12, "vB.x must equal vA.x + vBA.x at t=" + t);
            assertEquals(vB.y(), sum.y(), 1e-12, "vB.y must equal vA.y + vBA.y at t=" + t);
        }
    }

    @Test
    @DisplayName("Observer A and B views obey anti-symmetric inversion")
    void testAntiSymmetricObserverViews() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        for (double t = 0.0; t <= model.effectiveDuration(); t += 0.5) {
            Vector2D posBInA = model.positionInFrame(false, t, ReferenceFrame.OBSERVER_A);
            Vector2D posAInB = model.positionInFrame(true, t, ReferenceFrame.OBSERVER_B);

            assertEquals(-posBInA.x(), posAInB.x(), 1e-12);
            assertEquals(-posBInA.y(), posAInB.y(), 1e-12);

            Vector2D velBInA = model.velocityInFrame(false, t, ReferenceFrame.OBSERVER_A);
            Vector2D velAInB = model.velocityInFrame(true, t, ReferenceFrame.OBSERVER_B);

            assertEquals(-velBInA.x(), velAInB.x(), 1e-12);
            assertEquals(-velBInA.y(), velAInB.y(), 1e-12);
        }
    }

    @Test
    @DisplayName("Mid-air collision timing and coordinate alignment across frames")
    void testCollisionCoordinationAcrossFrames() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.flagshipCollisionPreset();

        assertTrue(model.hasCollision());
        assertTrue(model.collisionTime().isPresent());
        double tCol = model.collisionTime().getAsDouble();
        assertEquals(2.5, tCol, 1e-9);

        // In Ground frame at collision: posA == posB == (50, 30.625) with g=9.8
        Vector2D posA = model.positionA(tCol);
        Vector2D posB = model.positionB(tCol);
        assertEquals(50.0, posA.x(), 1e-9);
        assertEquals(50.0, posB.x(), 1e-9);
        assertEquals(posA.y(), posB.y(), 1e-9);

        // In Observer A frame at collision: posB == origin (0, 0)
        Vector2D posBInA = model.positionInFrame(false, tCol, ReferenceFrame.OBSERVER_A);
        assertEquals(0.0, posBInA.x(), 1e-9);
        assertEquals(0.0, posBInA.y(), 1e-9);

        // In Observer B frame at collision: posA == origin (0, 0)
        Vector2D posAInB = model.positionInFrame(true, tCol, ReferenceFrame.OBSERVER_B);
        assertEquals(0.0, posAInB.x(), 1e-9);
        assertEquals(0.0, posAInB.y(), 1e-9);
    }

    @Test
    @DisplayName("Near-miss preset minimum separation and closest approach time agree across all three frames")
    void testNearMissClosestApproachAcrossFrames() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.nearMissPreset();

        assertFalse(model.hasCollision(), "Near-miss preset must not be classified as a collision");
        double dMin = model.analyticalResult().dMinInterval();
        double tMin = model.analyticalResult().tMinInterval();

        assertEquals(2.5, tMin, 1e-9, "Closest approach time must be 2.5 s");
        assertEquals(2.0, dMin, 1e-9, "Minimum separation must be exactly 2.0 m");

        // Ground frame separation at tMin
        Vector2D posA = model.positionA(tMin);
        Vector2D posB = model.positionB(tMin);
        assertEquals(dMin, posA.distanceTo(posB), 1e-9, "Ground frame distance must equal dMin");

        // Observer A frame: distance of B to origin (0, 0)
        Vector2D posBInA = model.positionInFrame(false, tMin, ReferenceFrame.OBSERVER_A);
        assertEquals(dMin, posBInA.magnitude(), 1e-9, "Observer A frame distance of B to origin must equal dMin");

        // Observer B frame: distance of A to origin (0, 0)
        Vector2D posAInB = model.positionInFrame(true, tMin, ReferenceFrame.OBSERVER_B);
        assertEquals(dMin, posAInB.magnitude(), 1e-9, "Observer B frame distance of A to origin must equal dMin");
    }

    @Test
    @DisplayName("Angled collision preset demonstrates collinearity and mid-air intercept from elevated platform")
    void testAngledCollisionPresetCollinearityAndCoordination() {
        TwoProjectilePursuitModel model = TwoProjectilePursuitModel.angledCollisionPreset();

        assertTrue(model.hasCollision(), "Angled intercept preset must produce collision");
        double tCol = model.collisionTime().getAsDouble();
        assertEquals(3.0, tCol, 1e-9, "Collision must occur at t = 3.0 s");

        // Ground frame collision position at (90.0, 60.9) m
        Vector2D posA = model.positionA(tCol);
        Vector2D posB = model.positionB(tCol);
        assertEquals(90.0, posA.x(), 1e-9);
        assertEquals(90.0, posB.x(), 1e-9);
        assertEquals(60.9, posA.y(), 1e-4);
        assertEquals(60.9, posB.y(), 1e-4);

        // Observer A frame: Body B arrives at origin (0, 0) at t = 3.0 s
        Vector2D posBInA = model.positionInFrame(false, tCol, ReferenceFrame.OBSERVER_A);
        assertEquals(0.0, posBInA.x(), 1e-9);
        assertEquals(0.0, posBInA.y(), 1e-9);

        // Collinearity check: r_rel(0) = (120, 30), v_rel = (-40, -10). Cross product must be 0!
        Vector2D r0 = model.relativePositionBA(0.0);
        Vector2D vRel = model.relativeVelocityBA(0.0);
        double crossProduct2D = r0.x() * vRel.y() - r0.y() * vRel.x();
        assertEquals(0.0, crossProduct2D, 1e-9, "Initial relative position and relative velocity must be collinear");
    }

    @Test
    @DisplayName("ReferenceFrame distinguishes inertial laboratory from non-inertial accelerating frames")
    void testFrameClassificationAndInertialProperties() {
        assertTrue(ReferenceFrame.GROUND.isInertial(), "Ground frame must be classified as inertial");
        assertFalse(ReferenceFrame.OBSERVER_A.isInertial(), "Observer A frame is non-inertial (accelerates with A)");
        assertFalse(ReferenceFrame.OBSERVER_B.isInertial(), "Observer B frame is non-inertial (accelerates with B)");

        assertEquals("INERTIAL LAB FRAME", ReferenceFrame.GROUND.frameClassification());
        assertEquals("NON-INERTIAL (ACCELERATING) FRAME", ReferenceFrame.OBSERVER_A.frameClassification());
        assertEquals("NON-INERTIAL (ACCELERATING) FRAME", ReferenceFrame.OBSERVER_B.frameClassification());
    }
}
