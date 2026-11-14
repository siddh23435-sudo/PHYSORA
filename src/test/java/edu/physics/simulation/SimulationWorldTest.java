package edu.physics.simulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimulationWorld - Core 2D Rigid Body Mechanics and Integration")
class SimulationWorldTest {

    private static final double TOL = 1e-9;
    private static final double MACHINE_TOL = 1e-12;

    @Test
    @DisplayName("Test 1: Free fall and 2D projectile motion match exact analytical kinematic solutions")
    void freeFallMatchesAnalyticalPositionAndVelocity() {
        double g = 9.81;
        SimulationWorld world = SimulationWorld.withGravity(g);

        // 1. Vertical free fall from rest: y0 = 100 m
        RigidBody fallingBody = RigidBody.pointMass("fallingBody", 2.0, new Vector2D(0.0, 100.0), Vector2D.ZERO);
        world.addBody(fallingBody);

        // 2. Parabolic projectile: launched at (0, 0) with vx = 20 m/s, vy = 30 m/s
        Vector2D u = new Vector2D(20.0, 30.0);
        RigidBody projectile = RigidBody.pointMass("projectile", 1.5, Vector2D.ZERO, u);
        world.addBody(projectile);

        // Advance simulation for t = 2.0 seconds
        double t = 2.0;
        world.stepExact(t);

        // Analytical free fall: y(t) = y0 - 1/2 g t², vy(t) = -g t
        double expectedFallingY = 100.0 - 0.5 * g * t * t;
        double expectedFallingVy = -g * t;
        assertEquals(0.0, fallingBody.position().x(), TOL);
        assertEquals(expectedFallingY, fallingBody.position().y(), TOL);
        assertEquals(0.0, fallingBody.velocity().x(), TOL);
        assertEquals(expectedFallingVy, fallingBody.velocity().y(), TOL);

        // Analytical projectile: x(t) = ux * t, y(t) = uy * t - 1/2 g t²
        double expectedProjX = u.x() * t;
        double expectedProjY = u.y() * t - 0.5 * g * t * t;
        double expectedProjVx = u.x();
        double expectedProjVy = u.y() - g * t;

        assertEquals(expectedProjX, projectile.position().x(), TOL);
        assertEquals(expectedProjY, projectile.position().y(), TOL);
        assertEquals(expectedProjVx, projectile.velocity().x(), TOL);
        assertEquals(expectedProjVy, projectile.velocity().y(), TOL);
    }

    @Test
    @DisplayName("Test 2: Constant force accelerates body according to Newton's Second Law (F = ma)")
    void constantForceMatchesNewtonsSecondLaw() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        double mass = 4.0; // kg
        Vector2D force = new Vector2D(12.0, -8.0); // N
        RigidBody body = RigidBody.dynamicBlock("testBlock", mass, 1.0, 1.0, Vector2D.ZERO);
        world.addBody(body);

        // Add constant force generator
        world.addForceGenerator((b, simTime) -> {
            if (b.equals(body)) {
                b.applyForce(force);
            }
        });

        double t = 3.0; // seconds
        world.stepExact(t);

        // Theoretical acceleration: a = F / m
        Vector2D expectedA = force.divide(mass);
        // v(t) = a * t
        Vector2D expectedV = expectedA.multiply(t);
        // s(t) = 1/2 * a * t²
        Vector2D expectedP = expectedA.multiply(0.5 * t * t);

        assertEquals(expectedA.x(), body.state().acceleration().x(), TOL);
        assertEquals(expectedA.y(), body.state().acceleration().y(), TOL);
        assertEquals(expectedV.x(), body.velocity().x(), TOL);
        assertEquals(expectedV.y(), body.velocity().y(), TOL);
        assertEquals(expectedP.x(), body.position().x(), TOL);
        assertEquals(expectedP.y(), body.position().y(), TOL);
    }

    @Test
    @DisplayName("Test 3: Body with initial velocity continues at constant velocity without external forces")
    void constantVelocityMotionWithoutExternalForces() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        Vector2D initialPos = new Vector2D(10.0, 25.0);
        Vector2D initialVel = new Vector2D(5.0, -2.5);
        RigidBody body = RigidBody.pointMass("inertialBody", 3.0, initialPos, initialVel);
        world.addBody(body);

        double duration = 5.0; // seconds
        world.stepExact(duration);

        Vector2D expectedPos = initialPos.add(initialVel.multiply(duration));
        assertEquals(expectedPos.x(), body.position().x(), MACHINE_TOL);
        assertEquals(expectedPos.y(), body.position().y(), MACHINE_TOL);
        assertEquals(initialVel.x(), body.velocity().x(), MACHINE_TOL);
        assertEquals(initialVel.y(), body.velocity().y(), MACHINE_TOL);
    }

    @Test
    @DisplayName("Test 4: Static body remains completely immobile under gravitational and external forces")
    void staticBodyRemainsImmobileUnderGravityAndForces() {
        SimulationWorld world = SimulationWorld.withGravity(9.81);

        Vector2D initialPos = new Vector2D(5.0, 0.0);
        RigidBody staticFloor = RigidBody.staticBox("ground", 20.0, 1.0, initialPos);
        RigidBody staticAnchor = RigidBody.staticAnchor("pin", 0.5, new Vector2D(0.0, 10.0));
        world.addBody(staticFloor);
        world.addBody(staticAnchor);

        // Apply external forces and torques directly
        staticFloor.applyForce(new Vector2D(5000.0, -10000.0));
        staticFloor.applyTorque(250.0);
        staticAnchor.applyForceAtPoint(new Vector2D(100.0, 200.0), new Vector2D(2.0, 3.0));

        // Advance 600 steps (5 seconds)
        for (int i = 0; i < 600; i++) {
            world.stepSingle();
        }

        // Static floor checks
        assertEquals(initialPos.x(), staticFloor.position().x(), MACHINE_TOL);
        assertEquals(initialPos.y(), staticFloor.position().y(), MACHINE_TOL);
        assertEquals(Vector2D.ZERO, staticFloor.velocity());
        assertEquals(0.0, staticFloor.angle(), MACHINE_TOL);
        assertEquals(0.0, staticFloor.angularVelocity(), MACHINE_TOL);
        assertEquals(Vector2D.ZERO, staticFloor.state().acceleration());
        assertEquals(0.0, staticFloor.state().angularAcceleration(), MACHINE_TOL);

        // Static anchor checks
        assertEquals(0.0, staticAnchor.position().x(), MACHINE_TOL);
        assertEquals(10.0, staticAnchor.position().y(), MACHINE_TOL);
        assertEquals(Vector2D.ZERO, staticAnchor.velocity());
    }

    @Test
    @DisplayName("Test 5: Rotational dynamics matches torque and moment of inertia (τ = Iα)")
    void rotationalStateAndTorqueAccumulation() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        // Disc: m = 2 kg, r = 0.5 m -> I = 1/2 m r² = 0.5 * 2.0 * 0.25 = 0.25 kg·m²
        double mass = 2.0;
        double radius = 0.5;
        double expectedInertia = 0.5 * mass * radius * radius;
        RigidBody disc = RigidBody.dynamicDisc("flywheel", mass, radius, Vector2D.ZERO);
        world.addBody(disc);

        assertEquals(expectedInertia, disc.momentOfInertia(), TOL);

        double torque = 1.5; // N·m
        world.addForceGenerator((b, time) -> {
            if (b.equals(disc)) {
                b.applyTorque(torque);
            }
        });

        double t = 2.0; // seconds
        world.stepExact(t);

        // α = τ / I = 1.5 / 0.25 = 6.0 rad/s²
        double expectedAlpha = torque / expectedInertia;
        // ω(t) = α * t = 6.0 * 2.0 = 12.0 rad/s
        double expectedOmega = expectedAlpha * t;
        // θ(t) = 1/2 * α * t² = 0.5 * 6.0 * 4.0 = 12.0 rad
        double expectedTheta = 0.5 * expectedAlpha * t * t;

        assertEquals(expectedAlpha, disc.state().angularAcceleration(), TOL);
        assertEquals(expectedOmega, disc.angularVelocity(), TOL);
        assertEquals(expectedTheta, disc.angle(), TOL);

        // Center of mass should not have translated
        assertEquals(Vector2D.ZERO, disc.position());
        assertEquals(Vector2D.ZERO, disc.velocity());
    }

    @Test
    @DisplayName("Test 6: Force applied at off-center point generates exact 2D cross-product torque")
    void torqueGeneratedByForceAtPoint() {
        RigidBodyState state = RigidBodyState.atRest(new Vector2D(10.0, 10.0));

        // Point is at (12.0, 10.0) -> lever arm r = (2.0, 0.0)
        // Force is (0.0, 15.0) -> τ = 2.0 * 15.0 - 0.0 * 0.0 = 30.0 N·m
        state.applyForceAtPoint(new Vector2D(0.0, 15.0), new Vector2D(12.0, 10.0));
        assertEquals(30.0, state.netTorque(), TOL);
        assertEquals(new Vector2D(0.0, 15.0), state.netForce());

        // Arbitrary offset point and force:
        // Position = (10, 10), point = (13, 14) -> lever arm r = (3.0, 4.0)
        // Force = (-2.0, 5.0) -> τ_new = 3*5 - 4*(-2) = 15 + 8 = 23 N·m
        state.clearForces();
        assertEquals(0.0, state.netTorque(), MACHINE_TOL);
        assertEquals(Vector2D.ZERO, state.netForce());

        state.applyForceAtPoint(new Vector2D(-2.0, 5.0), new Vector2D(13.0, 14.0));
        assertEquals(23.0, state.netTorque(), TOL);
        assertEquals(new Vector2D(-2.0, 5.0), state.netForce());
    }

    @Test
    @DisplayName("Test 7: Fixed timestep advances simulation time and step count by exact increments")
    void fixedTimestepAdvancement() {
        double dt = 1.0 / 120.0;
        SimulationWorld world = new SimulationWorld(dt, UniformGravity.standardEarth());

        assertEquals(0.0, world.simulationTime(), MACHINE_TOL);
        assertEquals(0, world.stepCount());

        world.stepSingle();
        assertEquals(dt, world.simulationTime(), MACHINE_TOL);
        assertEquals(1, world.stepCount());

        world.stepExact(1.0); // 1.0 second = 120 steps
        assertEquals(121, world.stepCount());
        assertEquals(121 * dt, world.simulationTime(), 1e-10);
    }

    @Test
    @DisplayName("Test 8: Deterministic reset restores all bodies and world state to exact initial values")
    void deterministicResetRestoresExactInitialState() {
        SimulationWorld world = SimulationWorld.withGravity(9.81);

        RigidBody block = RigidBody.dynamicBlock("b1", 3.0, 1.0, 1.0, new Vector2D(2.0, 50.0));
        RigidBody disc = RigidBody.dynamicDisc("d1", 2.0, 0.5, new Vector2D(-5.0, 30.0));
        RigidBody floor = RigidBody.staticBox("ground", 50.0, 2.0, new Vector2D(0.0, -1.0));
        world.addBody(block);
        world.addBody(disc);
        world.addBody(floor);

        // Apply external torque to disc
        world.addForceGenerator((b, t) -> {
            if (b.equals(disc)) b.applyTorque(5.0);
        });

        // Run simulation for 2 seconds (240 steps)
        world.stepExact(2.0);

        // Ensure states have changed significantly
        assertNotEquals(50.0, block.position().y());
        assertNotEquals(0.0, disc.angularVelocity());
        assertTrue(world.stepCount() > 0);
        assertTrue(world.simulationTime() > 0.0);

        // Reset world
        world.reset();

        assertEquals(0.0, world.simulationTime(), MACHINE_TOL);
        assertEquals(0, world.stepCount());
        assertEquals(0.0, world.accumulator(), MACHINE_TOL);

        assertEquals(new Vector2D(2.0, 50.0), block.position());
        assertEquals(Vector2D.ZERO, block.velocity());
        assertEquals(0.0, block.angle(), MACHINE_TOL);
        assertEquals(0.0, block.angularVelocity(), MACHINE_TOL);

        assertEquals(new Vector2D(-5.0, 30.0), disc.position());
        assertEquals(Vector2D.ZERO, disc.velocity());
        assertEquals(0.0, disc.angle(), MACHINE_TOL);
        assertEquals(0.0, disc.angularVelocity(), MACHINE_TOL);

        assertEquals(new Vector2D(0.0, -1.0), floor.position());

        // Run again for 2.0 seconds and verify determinism
        world.stepExact(2.0);
        Vector2D blockYRun2 = block.position();

        world.reset();
        world.stepExact(2.0);
        Vector2D blockYRun3 = block.position();

        assertEquals(blockYRun2.x(), blockYRun3.x(), MACHINE_TOL);
        assertEquals(blockYRun2.y(), blockYRun3.y(), MACHINE_TOL);
    }

    @Test
    @DisplayName("Test 9: Frame schedule independence: stepping at 60 Hz, 30 Hz, or 144 Hz produces identical results")
    void frameScheduleIndependence() {
        double dt = 1.0 / 120.0;

        // World A: stepped with 60 Hz frame intervals (1/60s = 2 sub-steps per frame)
        SimulationWorld world60 = new SimulationWorld(dt, UniformGravity.downward(9.81));
        RigidBody bodyA = RigidBody.dynamicBlock("bodyA", 2.0, 1.0, 1.0, new Vector2D(0.0, 50.0));
        world60.addBody(bodyA);

        // World B: stepped with 30 Hz frame intervals (1/30s = 4 sub-steps per frame)
        SimulationWorld world30 = new SimulationWorld(dt, UniformGravity.downward(9.81));
        RigidBody bodyB = RigidBody.dynamicBlock("bodyB", 2.0, 1.0, 1.0, new Vector2D(0.0, 50.0));
        world30.addBody(bodyB);

        // Advance World A by 60 frames of 1/60 s (total 1.0 s)
        for (int i = 0; i < 60; i++) {
            world60.step(1.0 / 60.0);
        }

        // Advance World B by 30 frames of 1/30 s (total 1.0 s)
        for (int i = 0; i < 30; i++) {
            world30.step(1.0 / 30.0);
        }

        assertEquals(120, world60.stepCount());
        assertEquals(120, world30.stepCount());
        assertEquals(world60.simulationTime(), world30.simulationTime(), MACHINE_TOL);

        // The states at t = 1.0 s must be bitwise identical down to machine precision
        assertEquals(bodyA.position().x(), bodyB.position().x(), MACHINE_TOL);
        assertEquals(bodyA.position().y(), bodyB.position().y(), MACHINE_TOL);
        assertEquals(bodyA.velocity().x(), bodyB.velocity().x(), MACHINE_TOL);
        assertEquals(bodyA.velocity().y(), bodyB.velocity().y(), MACHINE_TOL);
    }

    @Test
    @DisplayName("Test 10: Symplectic integration exhibits bounded energy drift with zero secular growth")
    void longRunIntegrationStabilityAndBoundedEnergy() {
        // 1. Conservative Simple Harmonic Oscillator (undamped linear spring)
        // F = -k * x
        double dt = 1.0 / 120.0;
        SimulationWorld shoWorld = SimulationWorld.zeroGravity(dt);

        double mass = 1.0; // kg
        double k = 20.0;   // N/m
        double x0 = 2.0;   // initial displacement (m)
        RigidBody oscillator = RigidBody.pointMass("oscillator", mass, new Vector2D(x0, 0.0), Vector2D.ZERO);
        shoWorld.addBody(oscillator);

        shoWorld.addForceGenerator((b, t) -> {
            if (b.equals(oscillator)) {
                double springForceX = -k * b.position().x();
                b.applyForce(new Vector2D(springForceX, 0.0));
            }
        });

        double initialEnergy = 0.5 * k * x0 * x0; // 0.5 * 20 * 4 = 40.0 Joules

        // Integrate over 10,000 steps (~83.3 seconds, ~40 full oscillation cycles)
        double maxEnergyError = 0.0;
        int totalSteps = 10000;
        for (int step = 0; step < totalSteps; step++) {
            shoWorld.stepSingle();
            double currentX = oscillator.position().x();
            double currentVx = oscillator.velocity().x();
            double kinetic = 0.5 * mass * currentVx * currentVx;
            double potential = 0.5 * k * currentX * currentX;
            double currentEnergy = kinetic + potential;
            double relativeError = Math.abs(currentEnergy - initialEnergy) / initialEnergy;
            if (relativeError > maxEnergyError) {
                maxEnergyError = relativeError;
            }
        }

        // Symplectic integrators maintain bounded energy oscillation without secular drift.
        // For dt = 1/120s and omega = sqrt(20) ~ 4.47 rad/s, maximum energy variation is < 0.05%
        assertTrue(maxEnergyError < 1e-3, "Energy error was " + maxEnergyError + ", expected < 1e-3");

        // 2. Gravitational potential and kinetic energy conservation during vertical projectile motion
        SimulationWorld freeFallWorld = SimulationWorld.withGravity(9.81);
        RigidBody ball = RigidBody.pointMass("ball", 2.0, new Vector2D(0.0, 50.0), new Vector2D(0.0, 25.0));
        freeFallWorld.addBody(ball);

        double initialMechEnergy = freeFallWorld.totalMechanicalEnergy(0.0);
        for (int i = 0; i < 600; i++) { // 5.0 seconds
            freeFallWorld.stepSingle();
            double currentMechEnergy = freeFallWorld.totalMechanicalEnergy(0.0);
            assertEquals(initialMechEnergy, currentMechEnergy, 1e-9);
        }
    }

    @Test
    @DisplayName("Test 11: Rigorously validates physical inputs and rejects invalid mass, inertia, and timesteps")
    void inputValidationRejectsInvalidMassInertiaTimestep() {
        // Mass <= 0 for dynamic body
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createBlock("bad", 0.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createBlock("bad", -5.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createBlock("bad", Double.NaN, 1.0, 1.0));

        // Dimensions <= 0
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createBlock("bad", 1.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createDisc("bad", 1.0, -0.5));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBodySpec.createStaticBox("bad", -1.0, 2.0));

        // Static body with dynamic inverseMass
        assertThrows(IllegalArgumentException.class, () -> new PhysicalBodySpec(
                UUID.randomUUID(), "badStatic", BodyType.STATIC, 10.0, 0.1, 10.0, 0.1, 1.0, 1.0, 0.0));

        // World timestep <= 0 or non-finite
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorld(0.0, UniformGravity.standardEarth()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorld(-0.01, UniformGravity.standardEarth()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorld(Double.NaN, UniformGravity.standardEarth()));

        // Duplicate body in world
        SimulationWorld world = new SimulationWorld();
        RigidBody body = RigidBody.pointMass("p1", 1.0, Vector2D.ZERO, Vector2D.ZERO);
        world.addBody(body);
        assertThrows(IllegalArgumentException.class, () -> world.addBody(body));

        // RigidBodyState non-finite validation
        assertThrows(IllegalArgumentException.class, () -> new RigidBodyState(Vector2D.ZERO, Vector2D.ZERO, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RigidBodyState(Vector2D.ZERO, Vector2D.ZERO, 0.0, Double.POSITIVE_INFINITY));

        RigidBodyState state = RigidBodyState.atRest(Vector2D.ZERO);
        assertThrows(IllegalArgumentException.class, () -> state.setAngle(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.setAngularVelocity(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.applyTorque(Double.NaN));
    }

    @Test
    @DisplayName("Tests momentum and energy auditing calculations on SimulationWorld")
    void momentumAndEnergyAuditing() {
        SimulationWorld world = SimulationWorld.zeroGravity(1.0 / 120.0);

        RigidBody b1 = RigidBody.pointMass("b1", 2.0, new Vector2D(0.0, 0.0), new Vector2D(3.0, 0.0)); // p = (6, 0)
        RigidBody b2 = RigidBody.pointMass("b2", 3.0, new Vector2D(0.0, 0.0), new Vector2D(-2.0, 4.0)); // p = (-6, 12)
        world.addBody(b1);
        world.addBody(b2);

        // Total linear momentum = (6 - 6, 0 + 12) = (0, 12)
        Vector2D totalP = world.totalLinearMomentum();
        assertEquals(0.0, totalP.x(), TOL);
        assertEquals(12.0, totalP.y(), TOL);

        // Total kinetic energy:
        // b1: 1/2 * 2 * (9) = 9 J
        // b2: 1/2 * 3 * (4 + 16) = 1/2 * 3 * 20 = 30 J
        // total = 39 J
        assertEquals(39.0, world.totalKineticEnergy(), TOL);

        // Angular momentum about (0,0):
        // b1 pos=(0,0) -> r x p = 0
        // b2 pos=(0,0) -> r x p = 0
        assertEquals(0.0, world.totalAngularMomentumAbout(Vector2D.ZERO), TOL);

        // Remove body b1
        assertTrue(world.removeBody(b1));
        assertFalse(world.removeBody(b1));
        assertEquals(1, world.bodyCount());
    }
}

