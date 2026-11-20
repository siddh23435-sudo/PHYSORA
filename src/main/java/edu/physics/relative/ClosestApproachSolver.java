package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Analytical solver for two-projectile pursuit, closest approach, and collision
 * detection.
 *
 * <p>
 * Under uniform downward gravity g:
 * 
 * <pre>
 *   a_{B/A} = (-g j) - (-g j) = 0
 *   r_{B/A}(t) = r_0 + v_{rel} * t
 * </pre>
 * 
 * Free flight is physically bounded by the simultaneous flight window:
 * 
 * <pre>
 *   t in [0, T], where T = min(t_land,A, t_land,B)
 * </pre>
 * 
 * The true physical closest approach time is clamped:
 * 
 * <pre>
 *   t* = clamp(t_min, 0, T) = max(0, min(t_min, T))
 *   d_{min,interval} = |r_0 + v_{rel} * t*|
 * </pre>
 * 
 * Post-landing line minima (t_min > T) never produce physical collisions.
 * </p>
 */
public final class ClosestApproachSolver {

    private ClosestApproachSolver() {
        // Utility class
    }

    /**
     * Solves closest approach between two bodies under uniform gravity g using
     * default tolerance policy.
     */
    public static ClosestApproachResult solve(RelativeBody2D bodyA, RelativeBody2D bodyB, double gravity) {
        return solve(bodyA, bodyB, gravity, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves closest approach between two bodies under uniform gravity g with a
     * specific tolerance policy.
     */
    public static ClosestApproachResult solve(RelativeBody2D bodyA,
            RelativeBody2D bodyB,
            double gravity,
            RelativeTolerancePolicy policy) {
        Objects.requireNonNull(bodyA, "bodyA must not be null");
        Objects.requireNonNull(bodyB, "bodyB must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        // 1. Compute physical landing times for both bodies to establish active
        // simultaneous flight interval
        LandingCalculationResult landingA = LandingSolver.solve(bodyA, gravity);
        LandingCalculationResult landingB = LandingSolver.solve(bodyB, gravity);

        double tLandA = landingA.physicalLandingTime().orElse(Double.POSITIVE_INFINITY);
        double tLandB = landingB.physicalLandingTime().orElse(Double.POSITIVE_INFINITY);
        double durationT = Math.min(tLandA, tLandB);

        // If duration is infinite (zero gravity or no landing), set duration to
        // positive infinity
        double effectiveT = Double.isInfinite(durationT) ? Double.POSITIVE_INFINITY : Math.max(0.0, durationT);

        // 2. Relative initial vectors
        Vector2D r0 = GalileanTransform.relativePosition(bodyB.initialPosition(), bodyA.initialPosition());
        Vector2D vRel = GalileanTransform.relativeVelocity(bodyB.initialVelocity(), bodyA.initialVelocity());

        double initialDist = r0.magnitude();
        double vRelMag = vRel.magnitude();

        double tMinUnconstrained;
        double dMinLine;
        double tMinInterval;
        double dMinInterval;
        RelativeKinematicsState kinematicsState;

        double effectiveContactRadius = (bodyA.radius() + bodyB.radius() > 0.0)
                ? (bodyA.radius() + bodyB.radius())
                : policy.contactRadius();
        double effectiveProximityRadius = Math.max(policy.proximityRadius(), effectiveContactRadius);

        // 3. Stationary relative motion (|v_rel| ≈ 0)
        if (policy.isZeroVelocity(vRelMag, 1.0)) {
            kinematicsState = RelativeKinematicsState.STATIONARY_SEPARATION;
            tMinUnconstrained = 0.0;
            dMinLine = initialDist;
            tMinInterval = 0.0;
            dMinInterval = initialDist;
        } else {
            kinematicsState = RelativeKinematicsState.UNIFORM_RELATIVE_VELOCITY;
            double vRelSq = vRelMag * vRelMag;

            // t_min = - (r0 · v_rel) / |v_rel|²
            tMinUnconstrained = -r0.dot(vRel) / vRelSq;

            // d_min,line = |r0 × v_rel| / |v_rel|
            dMinLine = Math.abs(r0.cross(vRel)) / vRelMag;

            // Clamped interval time t* = clamp(t_min, 0, T)
            if (tMinUnconstrained < 0.0) {
                // Divergent trajectories forward in time
                tMinInterval = 0.0;
                dMinInterval = initialDist;
            } else if (Double.isFinite(effectiveT) && tMinUnconstrained > effectiveT) {
                // Approaches during flight, but terminated by surface impact before reaching
                // line vertex
                tMinInterval = effectiveT;
                Vector2D rAtT = GalileanTransform.straightLineRelativePosition(r0, vRel, effectiveT);
                dMinInterval = rAtT.magnitude();
            } else {
                // Internal minimum during active simultaneous flight
                tMinInterval = tMinUnconstrained;
                dMinInterval = dMinLine;
            }
        }

        // 4. Contact Classification evaluated strictly on interval separation
        // d_min,interval
        ContactClassification contactClassification;
        if (policy.isZeroPosition(dMinInterval, 1.0)) {
            contactClassification = ContactClassification.EXACT_CONTACT;
        } else if (dMinInterval <= effectiveContactRadius) {
            contactClassification = ContactClassification.TOLERANCE_CONTACT;
        } else if (dMinInterval <= effectiveProximityRadius) {
            contactClassification = ContactClassification.NEAR_MISS;
        } else {
            contactClassification = ContactClassification.NO_CONTACT;
        }

        // 5. Positions at t* in ground frame
        Vector2D posAAtClosest = computeGroundPosition(bodyA.initialPosition(), bodyA.initialVelocity(), gravity,
                tMinInterval);
        Vector2D posBAtClosest = computeGroundPosition(bodyB.initialPosition(), bodyB.initialVelocity(), gravity,
                tMinInterval);

        double safeDuration = effectiveT;

        return new ClosestApproachResult(
                tMinUnconstrained,
                dMinLine,
                tMinInterval,
                dMinInterval,
                contactClassification,
                kinematicsState,
                posAAtClosest,
                posBAtClosest,
                safeDuration,
                effectiveContactRadius,
                effectiveProximityRadius);
    }

    private static Vector2D computeGroundPosition(Vector2D r0, Vector2D v0, double gravity, double time) {
        double x = r0.x() + v0.x() * time;
        double y = r0.y() + v0.y() * time - 0.5 * gravity * time * time;
        return new Vector2D(x, y);
    }
}
