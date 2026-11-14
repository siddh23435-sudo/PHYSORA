package edu.physics.simulation;

import java.util.Objects;

/**
 * Environmental force generator applying uniform gravitational acceleration: F = m * g.
 */
public final class UniformGravity implements ForceGenerator {

    public static final Vector2D EARTH_GRAVITY = new Vector2D(0.0, -9.81);

    private final Vector2D acceleration;

    public UniformGravity(Vector2D acceleration) {
        this.acceleration = Objects.requireNonNull(acceleration, "Gravity acceleration is required");
    }

    public static UniformGravity standardEarth() {
        return new UniformGravity(EARTH_GRAVITY);
    }

    public static UniformGravity downward(double gMagnitude) {
        if (!Double.isFinite(gMagnitude) || gMagnitude < 0.0) {
            throw new IllegalArgumentException("Gravity magnitude must be finite and >= 0: " + gMagnitude);
        }
        return new UniformGravity(new Vector2D(0.0, -gMagnitude));
    }

    public Vector2D acceleration() {
        return acceleration;
    }

    @Override
    public void apply(RigidBody body, double simulationTime) {
        if (body.isDynamic()) {
            body.applyForce(acceleration.multiply(body.mass()));
        }
    }
}

