package edu.physics.simulation;

import edu.physics.simulation.geometry.BoundingBox;
import edu.physics.simulation.geometry.CircleShape;
import edu.physics.simulation.geometry.GeometricShape;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable physical specification of a rigid body, defining its geometry, mass, and inertial properties.
 *
 * <p>Separated from runtime simulation state to ensure deterministic resets, easy serialization,
 * and reliable template instantiation.
 *
 * <p>All physical quantities use standard SI units:
 * <ul>
 *   <li>Mass: kilograms (kg)</li>
 *   <li>Dimensions: meters (m)</li>
 *   <li>Moment of Inertia: kg·m²</li>
 * </ul>
 */
public record PhysicalBodySpec(
        UUID id,
        String label,
        BodyType type,
        double mass,
        double inverseMass,
        double momentOfInertia,
        double inverseInertia,
        double width,
        double height,
        double radius) {

    public PhysicalBodySpec {
        Objects.requireNonNull(id, "Body id is required");
        Objects.requireNonNull(label, "Body label is required");
        Objects.requireNonNull(type, "BodyType is required");

        if (type == BodyType.DYNAMIC) {
            if (!Double.isFinite(mass) || mass <= 0.0) {
                throw new IllegalArgumentException("Dynamic body mass must be finite and > 0: " + mass);
            }
            if (!Double.isFinite(momentOfInertia) || momentOfInertia < 0.0) {
                throw new IllegalArgumentException("Dynamic body moment of inertia must be finite and >= 0: " + momentOfInertia);
            }
            if (Double.compare(inverseMass, 1.0 / mass) != 0 && Math.abs(inverseMass - 1.0 / mass) > 1e-12) {
                throw new IllegalArgumentException("inverseMass must be 1 / mass");
            }
            double expectedInvInertia = momentOfInertia > 0.0 ? 1.0 / momentOfInertia : 0.0;
            if (Double.compare(inverseInertia, expectedInvInertia) != 0 && Math.abs(inverseInertia - expectedInvInertia) > 1e-12) {
                throw new IllegalArgumentException("inverseInertia must be 1 / momentOfInertia (or 0 if point mass)");
            }
        } else {
            // Static body
            if (inverseMass != 0.0 || inverseInertia != 0.0) {
                throw new IllegalArgumentException("Static body must have inverseMass = 0 and inverseInertia = 0");
            }
        }

        if (width < 0.0 || height < 0.0 || radius < 0.0) {
            throw new IllegalArgumentException("Body geometric dimensions cannot be negative");
        }
    }

    /**
     * Creates a rectangular dynamic block with uniform mass distribution.
     * Moment of inertia: I = (1/12) * m * (width² + height²).
     */
    public static PhysicalBodySpec createBlock(UUID id, String label, double mass, double width, double height) {
        if (!Double.isFinite(width) || width <= 0.0 || !Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("Block dimensions must be finite and > 0");
        }
        double inertia = (1.0 / 12.0) * mass * (width * width + height * height);
        return new PhysicalBodySpec(
                id, label, BodyType.DYNAMIC, mass, 1.0 / mass, inertia, 1.0 / inertia, width, height, 0.0);
    }

    public static PhysicalBodySpec createBlock(String label, double mass, double width, double height) {
        return createBlock(UUID.randomUUID(), label, mass, width, height);
    }

    /**
     * Creates a circular dynamic disc or cylinder with uniform mass distribution.
     * Moment of inertia: I = (1/2) * m * radius².
     */
    public static PhysicalBodySpec createDisc(UUID id, String label, double mass, double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Disc radius must be finite and > 0");
        }
        double inertia = 0.5 * mass * radius * radius;
        return new PhysicalBodySpec(
                id, label, BodyType.DYNAMIC, mass, 1.0 / mass, inertia, 1.0 / inertia, radius * 2.0, radius * 2.0, radius);
    }

    public static PhysicalBodySpec createDisc(String label, double mass, double radius) {
        return createDisc(UUID.randomUUID(), label, mass, radius);
    }

    /**
     * Creates a dynamic point mass with negligible dimensions and zero moment of inertia.
     */
    public static PhysicalBodySpec createPointMass(UUID id, String label, double mass) {
        return new PhysicalBodySpec(
                id, label, BodyType.DYNAMIC, mass, 1.0 / mass, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static PhysicalBodySpec createPointMass(String label, double mass) {
        return createPointMass(UUID.randomUUID(), label, mass);
    }

    /**
     * Creates an immovable static box (e.g. table, floor, wall).
     */
    public static PhysicalBodySpec createStaticBox(UUID id, String label, double width, double height) {
        if (!Double.isFinite(width) || width <= 0.0 || !Double.isFinite(height) || height <= 0.0) {
            throw new IllegalArgumentException("Static box dimensions must be finite and > 0");
        }
        return new PhysicalBodySpec(
                id, label, BodyType.STATIC, Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY, 0.0,
                width, height, 0.0);
    }

    public static PhysicalBodySpec createStaticBox(String label, double width, double height) {
        return createStaticBox(UUID.randomUUID(), label, width, height);
    }

    /**
     * Creates an immovable static anchor or pin (e.g. fixed pulley axle).
     */
    public static PhysicalBodySpec createStaticAnchor(UUID id, String label, double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Anchor radius must be finite and >= 0");
        }
        return new PhysicalBodySpec(
                id, label, BodyType.STATIC, Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY, 0.0,
                radius * 2.0, radius * 2.0, radius);
    }

    public static PhysicalBodySpec createStaticAnchor(String label, double radius) {
        return createStaticAnchor(UUID.randomUUID(), label, radius);
    }

    /**
     * Creates a physical body specification from a geometric shape, optionally using an explicit
     * moment of inertia.
     *
     * <p>If explicitMomentOfInertia is provided, it takes strict precedence over the uniform-density
     * geometric calculation (useful for hollow cylinders, thick rims, or non-uniform JEE objects).
     */
    public static PhysicalBodySpec createWithShape(
            UUID id,
            String label,
            BodyType type,
            double mass,
            GeometricShape shape,
            Double explicitMomentOfInertia) {

        Objects.requireNonNull(id, "Body id is required");
        Objects.requireNonNull(label, "Body label is required");
        Objects.requireNonNull(type, "BodyType is required");
        Objects.requireNonNull(shape, "GeometricShape is required");

        BoundingBox bb = shape.localBoundingBox();
        double width = bb.width();
        double height = bb.height();
        double radius = shape instanceof CircleShape c ? c.radius() : 0.0;

        if (type == BodyType.STATIC) {
            if (explicitMomentOfInertia != null && explicitMomentOfInertia != Double.POSITIVE_INFINITY) {
                throw new IllegalArgumentException("Static body moment of inertia must be infinite or null");
            }
            return new PhysicalBodySpec(
                    id, label, BodyType.STATIC,
                    Double.POSITIVE_INFINITY, 0.0,
                    Double.POSITIVE_INFINITY, 0.0,
                    width, height, radius);
        }

        // Dynamic body validation
        if (!Double.isFinite(mass) || mass <= 0.0) {
            throw new IllegalArgumentException("Dynamic body mass must be finite and > 0: " + mass);
        }

        double inertia;
        if (explicitMomentOfInertia != null) {
            if (!Double.isFinite(explicitMomentOfInertia) || explicitMomentOfInertia < 0.0) {
                throw new IllegalArgumentException("Explicit moment of inertia must be finite and >= 0: " + explicitMomentOfInertia);
            }
            inertia = explicitMomentOfInertia;
        } else {
            inertia = shape.computeMomentOfInertia(mass);
        }

        if (!Double.isFinite(inertia) || inertia < 0.0) {
            throw new IllegalArgumentException("Computed moment of inertia must be finite and >= 0: " + inertia);
        }

        double invMass = 1.0 / mass;
        double invInertia = inertia > 0.0 ? 1.0 / inertia : 0.0;

        return new PhysicalBodySpec(
                id, label, BodyType.DYNAMIC,
                mass, invMass, inertia, invInertia,
                width, height, radius);
    }

    public static PhysicalBodySpec createWithShape(
            UUID id, String label, BodyType type, double mass, GeometricShape shape) {
        return createWithShape(id, label, type, mass, shape, null);
    }

    public static PhysicalBodySpec createWithShape(
            String label, BodyType type, double mass, GeometricShape shape) {
        return createWithShape(UUID.randomUUID(), label, type, mass, shape, null);
    }
}

