package edu.physics.simulation.scene;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable planar or linear physical boundary in a laboratory scene (floor, wall, ramp).
 *
 * <p>Orientation Convention:
 * The outward unit normal specifies the admissible contact half-space where physical
 * bodies reside. For any point p, signed clearance from the surface is h(p) = (p - startPoint) · outwardNormal.
 * Regions with h > 0 denote the interior of the simulation laboratory.
 *
 * <p>Material parameters (frictionCoefficient and restitution) are declarative metadata for
 * future contact/friction resolution in Stage 3D; Stage 3B computes no collision forces.
 */
public record StaticSurface(
        String surfaceId,
        String label,
        Vector2D startPoint,
        Vector2D endPoint,
        Vector2D outwardNormal,
        double frictionCoefficient,
        double restitution) {

    /**
     * Contact side orientation for inclined surfaces.
     */
    public enum RampContactSide {
        /** Normal points upward (positive y-component), allowing contact from above. */
        UPPER,
        /** Normal points downward (negative y-component), allowing contact from below. */
        LOWER
    }

    public StaticSurface {
        Objects.requireNonNull(surfaceId, "surfaceId is required");
        if (surfaceId.isBlank()) {
            throw new IllegalArgumentException("surfaceId cannot be blank");
        }
        Objects.requireNonNull(label, "label is required");
        Objects.requireNonNull(startPoint, "startPoint is required");
        Objects.requireNonNull(endPoint, "endPoint is required");
        Objects.requireNonNull(outwardNormal, "outwardNormal is required");

        // Scale-aware minimum length: minimum 10^-7 m (0.1 micrometers), allowing millimeter-scale apparatus
        double distSq = startPoint.distanceSquaredTo(endPoint);
        if (!Double.isFinite(distSq) || distSq < 1e-14) {
            throw new IllegalArgumentException(String.format(
                    java.util.Locale.ROOT,
                    "Surface endpoints must be distinct (minimum length 1e-7 m): start=%s, end=%s, distSq=%.4e",
                    startPoint, endPoint, distSq));
        }

        // Validate unit normal length
        double normSq = outwardNormal.magnitudeSquared();
        if (!Double.isFinite(normSq) || Math.abs(normSq - 1.0) > 1e-5) {
            throw new IllegalArgumentException("Outward normal must be a normalized unit vector: " + outwardNormal);
        }

        // Validate perpendicularity: |segment · normal| / |segment| <= 1e-5
        Vector2D segment = endPoint.subtract(startPoint);
        double projection = Math.abs(segment.dot(outwardNormal)) / Math.sqrt(distSq);
        if (projection > 1e-5) {
            throw new IllegalArgumentException(String.format(
                    java.util.Locale.ROOT,
                    "Outward normal must be perpendicular to surface segment: projection=%.4e", projection));
        }

        // Validate material properties
        if (!Double.isFinite(frictionCoefficient) || frictionCoefficient < 0.0) {
            throw new IllegalArgumentException("frictionCoefficient must be finite and >= 0: " + frictionCoefficient);
        }
        if (!Double.isFinite(restitution) || restitution < 0.0 || restitution > 1.0) {
            throw new IllegalArgumentException("restitution must be between 0.0 and 1.0: " + restitution);
        }
    }

    /**
     * Creates a horizontal floor from x1 to x2 at height y with upward normal (0, 1).
     */
    public static StaticSurface horizontalFloor(String id, String label, double x1, double x2, double y, double mu, double e) {
        if (x2 <= x1) {
            throw new IllegalArgumentException("Floor requires x2 > x1: x1=" + x1 + ", x2=" + x2);
        }
        return new StaticSurface(id, label, new Vector2D(x1, y), new Vector2D(x2, y), Vector2D.UNIT_Y, mu, e);
    }

    /**
     * Creates a vertical wall from y1 to y2 at coordinate x.
     *
     * @param faceLeft true if outward normal points into left half-space (-1, 0), false for right (+1, 0)
     */
    public static StaticSurface verticalWall(String id, String label, double x, double y1, double y2, boolean faceLeft, double mu, double e) {
        if (y2 <= y1) {
            throw new IllegalArgumentException("Wall requires y2 > y1: y1=" + y1 + ", y2=" + y2);
        }
        Vector2D normal = faceLeft ? new Vector2D(-1.0, 0.0) : new Vector2D(1.0, 0.0);
        return new StaticSurface(id, label, new Vector2D(x, y1), new Vector2D(x, y2), normal, mu, e);
    }

    /**
     * Creates an inclined ramp between start and end with an explicit contact side convention.
     *
     * <p>Regardless of whether start and end are supplied bottom-to-top or top-to-bottom,
     * {@link RampContactSide#UPPER} guarantees the normal has a positive y-component (pointing upward).
     */
    public static StaticSurface inclinedRamp(
            String id, String label, Vector2D start, Vector2D end, RampContactSide side, double mu, double e) {
        Objects.requireNonNull(side, "RampContactSide is required");
        Vector2D d = end.subtract(start);
        // CCW perpendicular normal: (-dy, dx)
        Vector2D n = new Vector2D(-d.y(), d.x()).normalize();

        if (side == RampContactSide.UPPER) {
            if (n.y() < 0.0 || (n.y() == 0.0 && n.x() < 0.0)) {
                n = n.negate();
            }
        } else {
            if (n.y() > 0.0 || (n.y() == 0.0 && n.x() > 0.0)) {
                n = n.negate();
            }
        }
        return new StaticSurface(id, label, start, end, n, mu, e);
    }

    /**
     * Creates an inclined ramp defaulting to {@link RampContactSide#UPPER} (contact from above).
     */
    public static StaticSurface inclinedRamp(String id, String label, Vector2D start, Vector2D end, double mu, double e) {
        return inclinedRamp(id, label, start, end, RampContactSide.UPPER, mu, e);
    }
}

