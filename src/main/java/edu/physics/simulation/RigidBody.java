package edu.physics.simulation;

import edu.physics.simulation.geometry.CircleShape;
import edu.physics.simulation.geometry.GeometricShape;
import edu.physics.simulation.geometry.PointMassShape;
import edu.physics.simulation.geometry.RectangleShape;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete physical entity representing a rigid body in the simulation world.
 *
 * <p>Unites the immutable physical configuration ({@link PhysicalBodySpec}), authoritative
 * geometry ({@link GeometricShape}), and dynamic mutable simulation state ({@link RigidBodyState}).
 *
 * <p>Maintains a deterministic snapshot of its initial state to guarantee exact resets.
 */
public final class RigidBody {

    private final PhysicalBodySpec spec;
    private final GeometricShape shape;
    private final RigidBodyState state;
    private final RigidBodyState initialState;

    /**
     * Primary constructor uniting physical specification, authoritative geometry, and initial state.
     */
    public RigidBody(PhysicalBodySpec spec, GeometricShape shape, RigidBodyState initialState) {
        this.spec = Objects.requireNonNull(spec, "PhysicalBodySpec is required");
        this.shape = Objects.requireNonNull(shape, "GeometricShape is required");
        this.initialState = Objects.requireNonNull(initialState, "Initial state is required").copy();
        this.state = this.initialState.copy();
    }

    /**
     * Legacy constructor deriving default geometry from the physical specification.
     */
    public RigidBody(PhysicalBodySpec spec, RigidBodyState initialState) {
        this(spec, deriveShapeFromSpec(spec), initialState);
    }

    /**
     * Derives a default {@link GeometricShape} from scalar dimensions in {@link PhysicalBodySpec}.
     *
     * <p>Precedence Safeguard: When {@code spec.radius() > 0.0}, circular geometry takes strict precedence
     * even if width and height are non-zero (as disc and anchor specifications set width = height = 2 * radius).
     * When radius is zero and both width and height are positive, rectangular geometry is created.
     * Otherwise, if all dimensions are zero (or non-positive), the body is treated as a point mass.
     */
    private static GeometricShape deriveShapeFromSpec(PhysicalBodySpec spec) {
        Objects.requireNonNull(spec, "PhysicalBodySpec is required");
        if (spec.radius() > 0.0) {
            return new CircleShape(spec.radius());
        } else if (spec.width() > 0.0 && spec.height() > 0.0) {
            return new RectangleShape(spec.width(), spec.height());
        } else {
            return PointMassShape.INSTANCE;
        }
    }

    public static RigidBody dynamicBlock(String label, double mass, double width, double height, Vector2D position) {
        PhysicalBodySpec spec = PhysicalBodySpec.createBlock(label, mass, width, height);
        return new RigidBody(spec, new RectangleShape(width, height), RigidBodyState.atRest(position));
    }

    public static RigidBody dynamicDisc(String label, double mass, double radius, Vector2D position) {
        PhysicalBodySpec spec = PhysicalBodySpec.createDisc(label, mass, radius);
        return new RigidBody(spec, new CircleShape(radius), RigidBodyState.atRest(position));
    }

    public static RigidBody pointMass(String label, double mass, Vector2D position, Vector2D velocity) {
        PhysicalBodySpec spec = PhysicalBodySpec.createPointMass(label, mass);
        return new RigidBody(spec, PointMassShape.INSTANCE, RigidBodyState.of(position, velocity));
    }

    public static RigidBody staticBox(String label, double width, double height, Vector2D position) {
        PhysicalBodySpec spec = PhysicalBodySpec.createStaticBox(label, width, height);
        return new RigidBody(spec, new RectangleShape(width, height), RigidBodyState.atRest(position));
    }

    public static RigidBody staticAnchor(String label, double radius, Vector2D position) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Anchor radius must be finite and >= 0: " + radius);
        }
        PhysicalBodySpec spec = PhysicalBodySpec.createStaticAnchor(label, radius);
        GeometricShape shape = radius > 0.0 ? new CircleShape(radius) : PointMassShape.INSTANCE;
        return new RigidBody(spec, shape, RigidBodyState.atRest(position));
    }

    public PhysicalBodySpec spec() {
        return spec;
    }

    public GeometricShape shape() {
        return shape;
    }

    public RigidBodyState state() {
        return state;
    }

    public RigidBodyState initialState() {
        return initialState.copy();
    }

    public UUID id() {
        return spec.id();
    }

    public String label() {
        return spec.label();
    }

    public BodyType type() {
        return spec.type();
    }

    public boolean isStatic() {
        return spec.type() == BodyType.STATIC;
    }

    public boolean isDynamic() {
        return spec.type() == BodyType.DYNAMIC;
    }

    public double mass() {
        return spec.mass();
    }

    public double inverseMass() {
        return spec.inverseMass();
    }

    public double momentOfInertia() {
        return spec.momentOfInertia();
    }

    public double inverseInertia() {
        return spec.inverseInertia();
    }

    public Vector2D position() {
        return state.position();
    }

    public Vector2D velocity() {
        return state.velocity();
    }

    public double angle() {
        return state.angle();
    }

    public double angularVelocity() {
        return state.angularVelocity();
    }

    public void applyForce(Vector2D force) {
        if (isDynamic()) {
            state.applyForce(force);
        }
    }

    public void applyForceAtPoint(Vector2D force, Vector2D point) {
        if (isDynamic()) {
            state.applyForceAtPoint(force, point);
        }
    }

    public void applyTorque(double torque) {
        if (isDynamic()) {
            state.applyTorque(torque);
        }
    }

    public void clearForces() {
        state.clearForces();
    }

    /**
     * Restores this body to its exact initial configuration.
     */
    public void reset() {
        state.setPosition(initialState.position());
        state.setVelocity(initialState.velocity());
        state.setAngle(initialState.angle());
        state.setAngularVelocity(initialState.angularVelocity());
        state.clearForces();
        state.setAcceleration(Vector2D.ZERO);
        state.setAngularAcceleration(0.0);
    }

    /**
     * Evaluates total kinetic energy: E_k = 1/2 m v² + 1/2 I ω² (Joules).
     * Returns 0.0 for static bodies.
     */
    public double kineticEnergy() {
        if (isStatic()) return 0.0;
        double linear = 0.5 * spec.mass() * state.velocity().magnitudeSquared();
        double rotational = 0.5 * spec.momentOfInertia() * state.angularVelocity() * state.angularVelocity();
        return linear + rotational;
    }

    /**
     * Evaluates total linear momentum: p = m v (kg·m/s).
     * Returns Vector2D.ZERO for static bodies.
     */
    public Vector2D linearMomentum() {
        if (isStatic()) return Vector2D.ZERO;
        return state.velocity().multiply(spec.mass());
    }

    /**
     * Evaluates angular momentum about the center of mass: L = I ω (kg·m²/s).
     * Returns 0.0 for static bodies.
     */
    public double angularMomentum() {
        if (isStatic()) return 0.0;
        return spec.momentOfInertia() * state.angularVelocity();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RigidBody other)) return false;
        return spec.id().equals(other.spec.id());
    }

    @Override
    public int hashCode() {
        return spec.id().hashCode();
    }

    @Override
    public String toString() {
        return "RigidBody{" + spec.label() + ", id=" + spec.id() + ", pos=" + state.position() + "}";
    }
}

