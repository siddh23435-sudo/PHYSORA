package edu.physics.simulation;

import edu.physics.simulation.constraint.MechanicalConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Top-level container representing a physical simulation environment.
 *
 * <p>Owns all physical bodies, environmental force fields, fixed-timestep advancement,
 * time accumulators for frame-rate decoupling, and deterministic reset capabilities.
 *
 * <p>All physics calculations are strictly decoupled from JavaFX or UI display rates.
 */
public final class SimulationWorld {

    public static final double DEFAULT_FIXED_TIMESTEP = 1.0 / 120.0; // 120 Hz fixed sub-stepping
    public static final double MAX_FRAME_DELTA = 0.25; // Prevents accumulator spiral-of-death during pauses

    private final double fixedTimestep;
    private final SymplecticIntegrator integrator;
    private final Map<UUID, RigidBody> bodies = new LinkedHashMap<>();
    private final List<ForceGenerator> forceGenerators = new ArrayList<>();
    private final List<MechanicalConstraint> constraints = new ArrayList<>();

    private UniformGravity gravity;
    private double simulationTime = 0.0;
    private double accumulator = 0.0;
    private long stepCount = 0;

    public SimulationWorld() {
        this(DEFAULT_FIXED_TIMESTEP, UniformGravity.standardEarth());
    }

    public SimulationWorld(double fixedTimestep, UniformGravity gravity) {
        if (!Double.isFinite(fixedTimestep) || fixedTimestep <= 0.0) {
            throw new IllegalArgumentException("fixedTimestep must be finite and > 0: " + fixedTimestep);
        }
        this.fixedTimestep = fixedTimestep;
        this.gravity = Objects.requireNonNull(gravity, "Gravity generator is required");
        this.integrator = new SymplecticIntegrator();
        this.forceGenerators.add(this.gravity);
    }

    public static SimulationWorld zeroGravity(double fixedTimestep) {
        return new SimulationWorld(fixedTimestep, new UniformGravity(Vector2D.ZERO));
    }

    public static SimulationWorld withGravity(double gMagnitude) {
        return new SimulationWorld(DEFAULT_FIXED_TIMESTEP, UniformGravity.downward(gMagnitude));
    }

    public double fixedTimestep() {
        return fixedTimestep;
    }

    public double simulationTime() {
        return simulationTime;
    }

    public double accumulator() {
        return accumulator;
    }

    public long stepCount() {
        return stepCount;
    }

    public UniformGravity gravity() {
        return gravity;
    }

    public void setGravity(Vector2D gravityAcceleration) {
        Objects.requireNonNull(gravityAcceleration, "Gravity acceleration is required");
        this.forceGenerators.remove(this.gravity);
        this.gravity = new UniformGravity(gravityAcceleration);
        this.forceGenerators.add(0, this.gravity);
    }

    public void setGravity(double downwardMagnitude) {
        if (!Double.isFinite(downwardMagnitude) || downwardMagnitude < 0.0) {
            throw new IllegalArgumentException("Gravity magnitude must be finite and >= 0: " + downwardMagnitude);
        }
        setGravity(new Vector2D(0.0, -downwardMagnitude));
    }

    public void addBody(RigidBody body) {
        Objects.requireNonNull(body, "RigidBody is required");
        if (bodies.containsKey(body.id())) {
            throw new IllegalArgumentException("Body with id " + body.id() + " is already in the world");
        }
        bodies.put(body.id(), body);
    }

    public boolean removeBody(RigidBody body) {
        if (body == null) return false;
        return bodies.remove(body.id()) != null;
    }

    public Optional<RigidBody> getBody(UUID id) {
        return Optional.ofNullable(bodies.get(id));
    }

    public List<RigidBody> bodies() {
        return Collections.unmodifiableList(new ArrayList<>(bodies.values()));
    }

    public int bodyCount() {
        return bodies.size();
    }

    public void addForceGenerator(ForceGenerator generator) {
        Objects.requireNonNull(generator, "ForceGenerator is required");
        if (!forceGenerators.contains(generator)) {
            forceGenerators.add(generator);
        }
    }

    public boolean removeForceGenerator(ForceGenerator generator) {
        return forceGenerators.remove(generator);
    }

    public List<ForceGenerator> forceGenerators() {
        return Collections.unmodifiableList(forceGenerators);
    }

    public void addConstraint(MechanicalConstraint constraint) {
        Objects.requireNonNull(constraint, "Constraint is required");
        if (!constraints.contains(constraint)) {
            constraints.add(constraint);
        }
    }

    public boolean removeConstraint(MechanicalConstraint constraint) {
        return constraints.remove(constraint);
    }

    public List<MechanicalConstraint> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    public void clearConstraints() {
        constraints.clear();
    }

    /**
     * Advances the simulation by exactly one fixed sub-timestep (Δt).
     */
    public void stepSingle() {
        List<RigidBody> bodyList = new ArrayList<>(bodies.values());
        integrator.step(bodyList, forceGenerators, constraints, simulationTime, fixedTimestep);
        simulationTime += fixedTimestep;
        stepCount++;
    }

    /**
     * Deterministically steps the world forward by a specified duration using fixed sub-steps.
     * Rounds to the nearest exact number of sub-steps.
     */
    public void stepExact(double durationSeconds) {
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0.0) {
            throw new IllegalArgumentException("Duration must be finite and >= 0: " + durationSeconds);
        }
        int steps = (int) Math.round(durationSeconds / fixedTimestep);
        for (int i = 0; i < steps; i++) {
            stepSingle();
        }
    }

    /**
     * Advances simulation using an accumulator pattern, decoupling physical sub-stepping
     * from the rendering/display frame interval.
     *
     * <p>Regardless of whether this is invoked at 30 Hz, 60 Hz, or 144 Hz, the physical states
     * at integer multiples of fixedTimestep will be identical.
     */
    public void step(double frameDeltaSeconds) {
        if (!Double.isFinite(frameDeltaSeconds) || frameDeltaSeconds <= 0.0) {
            return;
        }
        double clampedDelta = Math.min(frameDeltaSeconds, MAX_FRAME_DELTA);
        accumulator += clampedDelta;

        while (accumulator >= fixedTimestep) {
            stepSingle();
            accumulator -= fixedTimestep;
        }
    }

    /**
     * Restores all bodies to their exact initial state and resets simulation time to zero.
     */
    public void reset() {
        simulationTime = 0.0;
        accumulator = 0.0;
        stepCount = 0;
        for (RigidBody body : bodies.values()) {
            body.reset();
        }
        integrator.evaluateForces(new ArrayList<>(bodies.values()), forceGenerators, 0.0);
    }

    /**
     * Calculates total kinetic energy across all bodies: E_k = Σ (½ m v² + ½ I ω²).
     */
    public double totalKineticEnergy() {
        double total = 0.0;
        for (RigidBody body : bodies.values()) {
            total += body.kineticEnergy();
        }
        return total;
    }

    /**
     * Calculates total gravitational potential energy relative to a horizontal reference height:
     * E_p = Σ m * g_magnitude * (y - referenceY).
     */
    public double totalGravitationalPotentialEnergy(double referenceY) {
        double gMag = Math.abs(gravity.acceleration().y());
        double total = 0.0;
        for (RigidBody body : bodies.values()) {
            if (body.isDynamic()) {
                total += body.mass() * gMag * (body.position().y() - referenceY);
            }
        }
        return total;
    }

    /**
     * Calculates total mechanical energy: E_mech = E_k + E_p.
     */
    public double totalMechanicalEnergy(double referenceY) {
        return totalKineticEnergy() + totalGravitationalPotentialEnergy(referenceY);
    }

    /**
     * Calculates total linear momentum: P = Σ m * v.
     */
    public Vector2D totalLinearMomentum() {
        Vector2D total = Vector2D.ZERO;
        for (RigidBody body : bodies.values()) {
            total = total.add(body.linearMomentum());
        }
        return total;
    }

    /**
     * Calculates total angular momentum about a specified 2D origin point:
     * L_total = Σ (I ω + (r_i - r_origin) × (m v_i)).
     */
    public double totalAngularMomentumAbout(Vector2D origin) {
        Objects.requireNonNull(origin, "Origin point is required");
        double total = 0.0;
        for (RigidBody body : bodies.values()) {
            if (body.isDynamic()) {
                total += body.angularMomentum();
                Vector2D r = body.position().subtract(origin);
                total += r.cross(body.linearMomentum());
            }
        }
        return total;
    }
}

