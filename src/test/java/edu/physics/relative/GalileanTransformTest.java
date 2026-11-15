package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GalileanTransform - Galilean Frame Transformations")
class GalileanTransformTest {

    @Test
    @DisplayName("Relative position, velocity, and acceleration obey vector subtractions")
    void testBasicRelativeVectors() {
        Vector2D posA = new Vector2D(10.0, 20.0);
        Vector2D posB = new Vector2D(40.0, 60.0);
        Vector2D rBA = GalileanTransform.relativePosition(posB, posA);
        assertEquals(30.0, rBA.x(), 1e-9);
        assertEquals(40.0, rBA.y(), 1e-9);

        Vector2D velA = new Vector2D(5.0, 15.0);
        Vector2D velB = new Vector2D(12.0, 8.0);
        Vector2D vBA = GalileanTransform.relativeVelocity(velB, velA);
        assertEquals(7.0, vBA.x(), 1e-9);
        assertEquals(-7.0, vBA.y(), 1e-9);

        Vector2D gA = new Vector2D(0.0, -9.81);
        Vector2D gB = new Vector2D(0.0, -9.81);
        Vector2D aBA = GalileanTransform.relativeAcceleration(gB, gA);
        assertEquals(0.0, aBA.x(), 1e-12);
        assertEquals(0.0, aBA.y(), 1e-12);
    }

    @Test
    @DisplayName("Transform to observer frame and back to ground frame preserves coordinates")
    void testRoundTripTransformation() {
        Vector2D groundPos = new Vector2D(100.0, 250.0);
        Vector2D observerOrigin = new Vector2D(40.0, 110.0);

        Vector2D inObserverFrame = GalileanTransform.toObserverFrame(groundPos, observerOrigin);
        assertEquals(60.0, inObserverFrame.x(), 1e-9);
        assertEquals(140.0, inObserverFrame.y(), 1e-9);

        Vector2D backInGround = GalileanTransform.toGroundFrame(inObserverFrame, observerOrigin);
        assertEquals(groundPos.x(), backInGround.x(), 1e-9);
        assertEquals(groundPos.y(), backInGround.y(), 1e-9);
    }

    @Test
    @DisplayName("Straight-line relative motion trajectory evaluates correctly")
    void testStraightLineRelativeMotion() {
        Vector2D r0 = new Vector2D(10.0, 5.0);
        Vector2D vRel = new Vector2D(3.0, 4.0);

        Vector2D at2Seconds = GalileanTransform.straightLineRelativePosition(r0, vRel, 2.0);
        assertEquals(16.0, at2Seconds.x(), 1e-9);
        assertEquals(13.0, at2Seconds.y(), 1e-9);
    }
}

