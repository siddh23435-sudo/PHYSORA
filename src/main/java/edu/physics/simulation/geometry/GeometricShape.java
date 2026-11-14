package edu.physics.simulation.geometry;

import edu.physics.simulation.Vector2D;

/**
 * Common geometric contract for two-dimensional physical boundaries.
 *
 * <p>A geometric shape defines spatial boundaries, area, local centroid, bounding boxes,
 * and support points for collision mapping.
 *
 * <p>Mass separation invariant: A geometric shape alone does NOT own mass. It provides
 * {@link #computeMomentOfInertia(double)} to evaluate moment of inertia assuming uniform density,
 * but physical mass distribution belongs to {@link edu.physics.simulation.PhysicalBodySpec}.
 */
public interface GeometricShape {

    ShapeType shapeType();

    /**
     * Centroid in local body coordinates. By physical definition, for a body centered
     * at its center of mass, this is (0, 0).
     */
    Vector2D centroid();

    /**
     * Area in square meters (m²).
     */
    double area();

    /**
     * Axis-aligned bounding box in local coordinates.
     */
    BoundingBox localBoundingBox();

    /**
     * Axis-aligned bounding box in world coordinates under the given transformation.
     */
    BoundingBox worldBoundingBox(Transform2D transform);

    /**
     * Tests whether a point in local body coordinates lies inside or on the boundary of this shape.
     */
    boolean containsPoint(Vector2D localPoint);

    /**
     * Computes the centroidal moment of inertia in kg·m² assuming total mass is distributed
     * uniformly across the area of this shape.
     *
     * @param totalMass total physical mass in kg (must be finite and > 0)
     */
    double computeMomentOfInertia(double totalMass);

    /**
     * Evaluates the support point (furthest vertex or boundary point) in a given local direction.
     * Used for Gilbert-Johnson-Keerthi (GJK) and Separating Axis Theorem (SAT) collision queries.
     */
    Vector2D supportPoint(Vector2D localDirection);
}

