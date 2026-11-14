package edu.physics.simulation;

import java.util.Objects;

/**
 * Mutable runtime state of a rigid body during simulation.
 *
 * <p>Tracks dynamic kinematic degrees of freedom (position, velocity, angle, angular velocity)
 * as well as instantaneous accumulators for applied forces, torques, and accelerations.
 *
 * <p>Coordinate Conventions:
 * <ul>
 *   <li>Position: (x, y) in meters, with positive x right and positive y up.</li>
 *   <li>Angle: θ in radians, measured counter-clockwise from the positive x-axis.</li>
 *   <li>Velocity: (vx, vy) in m/s.</li>
 *   <li>Angular velocity: ω in rad/s (positive counter-clockwise).</li>
 * </ul>
 */
public final class RigidBodyState {

    private Vector2D position;
    private Vector2D velocity;
    private double angle;
    private double angularVelocity;

    private Vector2D netForce;
    private double netTorque;
    private Vector2D acceleration;
    private double angularAcceleration;

    public RigidBodyState(Vector2D position, Vector2D velocity, double angle, double angularVelocity) {
        this.position = Objects.requireNonNull(position, "Position is required");
        this.velocity = Objects.requireNonNull(velocity, "Velocity is required");
        if (!Double.isFinite(angle) || !Double.isFinite(angularVelocity)) {
            throw new IllegalArgumentException("Angle and angular velocity must be finite");
        }
        this.angle = angle;
        this.angularVelocity = angularVelocity;
        this.netForce = Vector2D.ZERO;
        this.netTorque = 0.0;
        this.acceleration = Vector2D.ZERO;
        this.angularAcceleration = 0.0;
    }

    public static RigidBodyState atRest(Vector2D position) {
        return new RigidBodyState(position, Vector2D.ZERO, 0.0, 0.0);
    }

    public static RigidBodyState of(Vector2D position, Vector2D velocity) {
        return new RigidBodyState(position, velocity, 0.0, 0.0);
    }

    public static RigidBodyState of(Vector2D position, Vector2D velocity, double angle, double angularVelocity) {
        return new RigidBodyState(position, velocity, angle, angularVelocity);
    }

    public RigidBodyState copy() {
        RigidBodyState clone = new RigidBodyState(position, velocity, angle, angularVelocity);
        clone.netForce = this.netForce;
        clone.netTorque = this.netTorque;
        clone.acceleration = this.acceleration;
        clone.angularAcceleration = this.angularAcceleration;
        return clone;
    }

    public Vector2D position() {
        return position;
    }

    public void setPosition(Vector2D position) {
        this.position = Objects.requireNonNull(position, "Position is required");
    }

    public Vector2D velocity() {
        return velocity;
    }

    public void setVelocity(Vector2D velocity) {
        this.velocity = Objects.requireNonNull(velocity, "Velocity is required");
    }

    public double angle() {
        return angle;
    }

    public void setAngle(double angle) {
        if (!Double.isFinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite: " + angle);
        }
        this.angle = angle;
    }

    public double angularVelocity() {
        return angularVelocity;
    }

    public void setAngularVelocity(double angularVelocity) {
        if (!Double.isFinite(angularVelocity)) {
            throw new IllegalArgumentException("Angular velocity must be finite: " + angularVelocity);
        }
        this.angularVelocity = angularVelocity;
    }

    public Vector2D netForce() {
        return netForce;
    }

    public double netTorque() {
        return netTorque;
    }

    public Vector2D acceleration() {
        return acceleration;
    }

    public void setAcceleration(Vector2D acceleration) {
        this.acceleration = Objects.requireNonNull(acceleration, "Acceleration is required");
    }

    public double angularAcceleration() {
        return angularAcceleration;
    }

    public void setAngularAcceleration(double angularAcceleration) {
        if (!Double.isFinite(angularAcceleration)) {
            throw new IllegalArgumentException("Angular acceleration must be finite: " + angularAcceleration);
        }
        this.angularAcceleration = angularAcceleration;
    }

    /**
     * Applies a force acting directly at the center of mass (generates zero torque).
     */
    public void applyForce(Vector2D force) {
        Objects.requireNonNull(force, "Force is required");
        this.netForce = this.netForce.add(force);
    }

    /**
     * Applies a force at a specified world coordinate point.
     * Torque is generated via the 2D cross product: τ = (point - position) × force.
     */
    public void applyForceAtPoint(Vector2D force, Vector2D point) {
        Objects.requireNonNull(force, "Force is required");
        Objects.requireNonNull(point, "Point is required");
        this.netForce = this.netForce.add(force);
        Vector2D leverArm = point.subtract(this.position);
        this.netTorque += leverArm.cross(force);
    }

    /**
     * Applies a pure rotational torque in N·m (positive counter-clockwise).
     */
    public void applyTorque(double torque) {
        if (!Double.isFinite(torque)) {
            throw new IllegalArgumentException("Torque must be finite: " + torque);
        }
        this.netTorque += torque;
    }

    /**
     * Resets accumulated forces and torques to zero for the next integration step.
     */
    public void clearForces() {
        this.netForce = Vector2D.ZERO;
        this.netTorque = 0.0;
    }
}

