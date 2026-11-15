package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Authoritative physical simulation model for two-projectile relative motion.
 *
 * <p>Constitutes the single source of truth for the physical scenario. Ground-frame
 * trajectories are authoritative, while observer-frame coordinates (Observer A and Observer B)
 * are derived mathematically through {@link GalileanTransform}.</p>
 */
public final class TwoProjectilePursuitModel {

    public static final double DEFAULT_GRAVITY = 9.8; // m/s²

    private final RelativeBody2D bodyA;
    private final RelativeBody2D bodyB;
    private final double gravity;
    private final ClosestApproachResult analyticalResult;
    private final double effectiveDuration;

    public TwoProjectilePursuitModel(RelativeBody2D bodyA, RelativeBody2D bodyB, double gravity) {
        this.bodyA = Objects.requireNonNull(bodyA, "bodyA must not be null");
        this.bodyB = Objects.requireNonNull(bodyB, "bodyB must not be null");
        if (!Double.isFinite(gravity) || gravity < 0.0) {
            throw new IllegalArgumentException("gravity must be non-negative and finite: " + gravity);
        }
        this.gravity = gravity;
        this.analyticalResult = ClosestApproachSolver.solve(bodyA, bodyB, gravity);

        double duration = analyticalResult.simultaneousFlightDuration();
        if (Double.isInfinite(duration) || duration <= 0.0) {
            this.effectiveDuration = 5.0; // fallback standard duration for open infinite flight
        } else {
            this.effectiveDuration = duration;
        }
    }

    public RelativeBody2D bodyA() {
        return bodyA;
    }

    public RelativeBody2D bodyB() {
        return bodyB;
    }

    public double gravity() {
        return gravity;
    }

    public ClosestApproachResult analyticalResult() {
        return analyticalResult;
    }

    public double effectiveDuration() {
        return effectiveDuration;
    }

    public boolean hasCollision() {
        return analyticalResult.hasContact();
    }

    public OptionalDouble collisionTime() {
        return hasCollision() ? OptionalDouble.of(analyticalResult.tMinInterval()) : OptionalDouble.empty();
    }

    public Vector2D collisionPositionGround() {
        return analyticalResult.positionAAtClosest();
    }

    // --- Authoritative Ground Frame Kinematics ---

    public Vector2D positionA(double t) {
        return computeGroundPosition(bodyA.initialPosition(), bodyA.initialVelocity(), t);
    }

    public Vector2D velocityA(double t) {
        return computeGroundVelocity(bodyA.initialVelocity(), t);
    }

    public Vector2D positionB(double t) {
        return computeGroundPosition(bodyB.initialPosition(), bodyB.initialVelocity(), t);
    }

    public Vector2D velocityB(double t) {
        return computeGroundVelocity(bodyB.initialVelocity(), t);
    }

    // --- Relative Vector Kinematics (v_{B/A} = v_B - v_A, r_{B/A} = r_B - r_A) ---

    public Vector2D relativePositionBA(double t) {
        return positionB(t).subtract(positionA(t));
    }

    public Vector2D relativeVelocityBA(double t) {
        return velocityB(t).subtract(velocityA(t));
    }

    public Vector2D relativePositionAB(double t) {
        return positionA(t).subtract(positionB(t));
    }

    public Vector2D relativeVelocityAB(double t) {
        return velocityA(t).subtract(velocityB(t));
    }

    // --- Derived Frame Perspective Queries ---

    public Vector2D positionInFrame(boolean forBodyA, double t, ReferenceFrame frame) {
        Vector2D posA = positionA(t);
        Vector2D posB = positionB(t);
        return GalileanTransform.positionInFrame(posA, posB, forBodyA, frame);
    }

    public Vector2D velocityInFrame(boolean forBodyA, double t, ReferenceFrame frame) {
        Vector2D velA = velocityA(t);
        Vector2D velB = velocityB(t);
        return GalileanTransform.velocityInFrame(velA, velB, forBodyA, frame);
    }

    private Vector2D computeGroundPosition(Vector2D r0, Vector2D v0, double t) {
        double clampedTime = Math.max(0.0, t);
        double x = r0.x() + v0.x() * clampedTime;
        double y = r0.y() + v0.y() * clampedTime - 0.5 * gravity * clampedTime * clampedTime;
        return new Vector2D(x, y);
    }

    private Vector2D computeGroundVelocity(Vector2D v0, double t) {
        double clampedTime = Math.max(0.0, t);
        double vx = v0.x();
        double vy = v0.y() - gravity * clampedTime;
        return new Vector2D(vx, vy);
    }

    // --- Standard Educational Presets ---

    /**
     * Flagship JEE Mid-Air Collision Scenario:
     * Two projectiles launched towards each other that collide at t = 2.500 s at (50.0, 30.6) m.
     */
    public static TwoProjectilePursuitModel flagshipCollisionPreset() {
        RelativeBody2D a = RelativeBody2D.ofGround("Projectile A", new Vector2D(0.0, 0.0), new Vector2D(20.0, 24.5), 0.2);
        RelativeBody2D b = RelativeBody2D.ofGround("Projectile B", new Vector2D(100.0, 0.0), new Vector2D(-20.0, 24.5), 0.2);
        return new TwoProjectilePursuitModel(a, b, DEFAULT_GRAVITY);
    }

    /**
     * Near-Miss Pursuit Scenario:
     * Projectile B launched with vertical offset of 2.0 m, resulting in a close near-miss at t = 2.500 s
     * with minimum separation d_min = 2.00 m.
     */
    public static TwoProjectilePursuitModel nearMissPreset() {
        RelativeBody2D a = RelativeBody2D.ofGround("Projectile A", new Vector2D(0.0, 0.0), new Vector2D(20.0, 24.5), 0.2);
        RelativeBody2D b = RelativeBody2D.ofGround("Projectile B", new Vector2D(100.0, 2.0), new Vector2D(-20.0, 24.5), 0.2);
        return new TwoProjectilePursuitModel(a, b, DEFAULT_GRAVITY);
    }

    /**
     * Overtaking Pursuit Scenario:
     * Projectile A launched with high forward speed (35 m/s) overtakes Projectile B (15 m/s) from behind
     * with collision at t = 2.000 s at (70.0, 29.4) m.
     */
    public static TwoProjectilePursuitModel overtakePreset() {
        RelativeBody2D a = RelativeBody2D.ofGround("Projectile A", new Vector2D(0.0, 0.0), new Vector2D(35.0, 24.5), 0.2);
        RelativeBody2D b = RelativeBody2D.ofGround("Projectile B", new Vector2D(40.0, 0.0), new Vector2D(15.0, 24.5), 0.2);
        return new TwoProjectilePursuitModel(a, b, DEFAULT_GRAVITY);
    }

    /**
     * Angled Intercept (Elevated Platform Launch):
     * Projectile A launched from ground (0, 0) and Projectile B launched from an elevated platform
     * at (120, 30) m. Demonstrates an inclined straight-line relative motion path in the observer frame,
     * colliding at t = 3.000 s at (90.0, 60.9) m.
     */
    public static TwoProjectilePursuitModel angledCollisionPreset() {
        RelativeBody2D a = RelativeBody2D.ofGround("Projectile A", new Vector2D(0.0, 0.0), new Vector2D(30.0, 35.0), 0.2);
        RelativeBody2D b = RelativeBody2D.ofGround("Projectile B", new Vector2D(120.0, 30.0), new Vector2D(-10.0, 25.0), 0.2);
        return new TwoProjectilePursuitModel(a, b, DEFAULT_GRAVITY);
    }
}
