package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Deterministic analytical solver for projectile landing and surface intersection events.
 *
 * <p>Strictly distinguishes:
 * <ul>
 *   <li>Upward crossing of surface plane (v_y > 0)</li>
 *   <li>Tangential surface contact at apex (v_y = 0 at t > 0)</li>
 *   <li>Downward crossing of surface plane (v_y < 0)</li>
 * </ul>
 * For standard open-terrain physics, physical flight termination occurs upon either
 * tangential apex contact or downward surface crossing.</p>
 */
public final class LandingSolver {

    private LandingSolver() {
        // Utility class
    }

    /**
     * Solves landing times for a body against its defined landingSurfaceY under gravity g.
     */
    public static LandingCalculationResult solve(RelativeBody2D body, double gravity) {
        Objects.requireNonNull(body, "body must not be null");
        return solve(body.initialPosition().y(), body.initialVelocity().y(), body.landingSurfaceY(), gravity, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves landing times for given vertical launch coordinates, surface altitude, and gravity.
     */
    public static LandingCalculationResult solve(double y0,
                                                 double vy0,
                                                 double ySurface,
                                                 double gravity,
                                                 RelativeTolerancePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (!Double.isFinite(y0) || !Double.isFinite(vy0) || !Double.isFinite(ySurface) || !Double.isFinite(gravity)) {
            throw new IllegalArgumentException("Parameters must be finite: y0=" + y0 + ", vy0=" + vy0
                    + ", ySurface=" + ySurface + ", gravity=" + gravity);
        }

        if (gravity < 0.0) {
            return new LandingCalculationResult(
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    LandingClassification.INVALID_GRAVITY
            );
        }

        double deltaY = ySurface - y0;

        // 1. Zero Gravity (unaccelerated vertical motion: y(t) = y0 + vy0 * t = ySurface => vy0 * t = deltaY)
        if (policy.isZeroAcceleration(gravity, 9.81)) {
            if (policy.isZeroVelocity(vy0, 1.0)) {
                if (policy.isZeroPosition(deltaY, 1.0)) {
                    return new LandingCalculationResult(
                            OptionalDouble.of(0.0),
                            OptionalDouble.empty(),
                            OptionalDouble.of(0.0),
                            OptionalDouble.of(0.0),
                            LandingClassification.TANGENTIAL_CONTACT
                    );
                }
                return new LandingCalculationResult(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        LandingClassification.NO_LANDING_PARALLEL
                );
            }

            double tIntersection = deltaY / vy0;
            if (tIntersection > 0.0) {
                if (vy0 < 0.0) {
                    // Downward crossing
                    return new LandingCalculationResult(
                            OptionalDouble.of(tIntersection),
                            OptionalDouble.of(tIntersection),
                            OptionalDouble.empty(),
                            OptionalDouble.of(tIntersection),
                            LandingClassification.DOWNWARD_CROSSING
                    );
                } else {
                    // Upward crossing
                    return new LandingCalculationResult(
                            OptionalDouble.of(tIntersection),
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            LandingClassification.UPWARD_CROSSING
                    );
                }
            } else {
                return new LandingCalculationResult(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        LandingClassification.NO_LANDING_RECEDING
                );
            }
        }

        // 2. Uniform Gravity g > 0
        // Ground-level launch (y0 == ySurface => deltaY == 0)
        boolean isGroundLevel = (deltaY == 0.0) || policy.isMachineZero(deltaY, Math.max(Math.abs(y0), 1.0));
        if (isGroundLevel) {
            if (vy0 > 0.0) {
                // Launching upward from ground. Flight ends at forward downward crossing t = 2 * vy0 / g
                double tLand = 2.0 * vy0 / gravity;
                return new LandingCalculationResult(
                        OptionalDouble.of(tLand),
                        OptionalDouble.of(tLand),
                        OptionalDouble.empty(),
                        OptionalDouble.of(tLand),
                        LandingClassification.DOWNWARD_CROSSING
                );
            } else {
                // Resting or directed downward into surface at ground
                return new LandingCalculationResult(
                        OptionalDouble.of(0.0),
                        OptionalDouble.of(0.0),
                        OptionalDouble.empty(),
                        OptionalDouble.of(0.0),
                        LandingClassification.DOWNWARD_CROSSING
                );
            }
        }

        // Surface quadratic: 0.5 * g * t^2 - vy0 * t + deltaY = 0
        // Discriminant: Δ = vy0^2 - 2 * g * deltaY = vy0^2 + 2 * g * (y0 - ySurface)
        double fourAc = 2.0 * gravity * deltaY;
        double discriminant = vy0 * vy0 - fourAc;
        double machineThreshold = Math.max(1e-24, RelativeTolerancePolicy.MACHINE_EPSILON * Math.max(vy0 * vy0, Math.abs(fourAc)));

        // Genuinely Negative Discriminant (Delta < -machineThreshold): Apex never reaches surface (y_max < y_surface)
        if (discriminant < -machineThreshold) {
            LandingClassification classification = vy0 > 0.0
                    ? LandingClassification.NO_LANDING_APEX_BELOW
                    : LandingClassification.NO_LANDING_RECEDING;
            return new LandingCalculationResult(
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    classification
            );
        }

        // Exact machine zero: Tangential Apex Contact
        if (Math.abs(discriminant) <= machineThreshold) {
            double tApex = vy0 / gravity;
            if (tApex > 0.0) {
                return new LandingCalculationResult(
                        OptionalDouble.of(tApex),
                        OptionalDouble.empty(),
                        OptionalDouble.of(tApex),
                        OptionalDouble.of(tApex),
                        LandingClassification.TANGENTIAL_CONTACT
                );
            } else {
                return new LandingCalculationResult(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        LandingClassification.NO_LANDING_RECEDING
                );
            }
        }

        // Positive Discriminant: Numerically stable Citardauq quadratic solution:
        // A = 0.5 * g, B = -vy0, C = deltaY
        double sqrtDelta = Math.sqrt(Math.max(0.0, discriminant));
        double sgnB = (vy0 <= 0.0) ? 1.0 : -1.0; // sgn(-vy0)
        double q = -0.5 * (-vy0 + sgnB * sqrtDelta);
        double tA = q / (0.5 * gravity);
        double tB = (q != 0.0) ? deltaY / q : 0.0;

        double tEarly = Math.min(tA, tB);
        double tLate = Math.max(tA, tB);

        // Time-Domain Coalescence Contract:
        // Only classify as tangential apex contact if the emergence and descent times coalesce
        // within the scale-aware time tolerance.
        double timeScale = Math.max(Math.max(Math.abs(tEarly), Math.abs(tLate)), 1.0);
        if (policy.areTimesEqual(tEarly, tLate, timeScale)) {
            double tApex = 0.5 * (tEarly + tLate);
            if (tApex > 0.0) {
                return new LandingCalculationResult(
                        OptionalDouble.of(tApex),
                        OptionalDouble.empty(),
                        OptionalDouble.of(tApex),
                        OptionalDouble.of(tApex),
                        LandingClassification.TANGENTIAL_CONTACT
                );
            } else {
                return new LandingCalculationResult(
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        LandingClassification.NO_LANDING_RECEDING
                );
            }
        }

        // Elevated launch (y0 > ySurface => deltaY < 0)
        if (deltaY < 0.0) {
            // Exactly one root is strictly positive (tLate > 0), where v_y = -sqrt(Δ) < 0
            return new LandingCalculationResult(
                    OptionalDouble.of(tLate),
                    OptionalDouble.of(tLate),
                    OptionalDouble.empty(),
                    OptionalDouble.of(tLate),
                    LandingClassification.DOWNWARD_CROSSING
            );
        }

        // Sub-surface launch (y0 < ySurface => deltaY > 0)
        // With positive discriminant, vy0 must be > sqrt(2g * deltaY) > 0, so both roots are positive
        if (vy0 > 0.0 && tEarly > 0.0) {
            // tEarly has v_y > 0 (upward emergence)
            // tLate has v_y < 0 (downward crossing / physical landing on open terrain)
            return new LandingCalculationResult(
                    OptionalDouble.of(tEarly),
                    OptionalDouble.of(tLate),
                    OptionalDouble.empty(),
                    OptionalDouble.of(tLate),
                    LandingClassification.DOWNWARD_CROSSING
            );
        }

        return new LandingCalculationResult(
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                LandingClassification.NO_LANDING_RECEDING
        );
    }
}
