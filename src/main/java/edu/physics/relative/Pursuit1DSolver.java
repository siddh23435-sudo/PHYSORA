package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Deterministic analytical solver for 1D relative pursuit and overtaking problems.
 *
 * <p>Solves relative separation:
 * <pre>
 *   x_rel(t) = x0 + v_rel0 * t + 0.5 * a_rel * t² = 0
 * </pre>
 * Key features:
 * <ul>
 *   <li>Strict initial collision precedence: x0 = 0 is immediately recognized as collision at t = 0.</li>
 *   <li>Numerically stable Citardauq quadratic root evaluation (eliminates subtractive cancellation).</li>
 *   <li>Scale-aware tolerance policy for acceleration, velocity, and near-zero discriminant (repeated roots).</li>
 *   <li>Closed-form interval minimum separation d_min evaluated across endpoints and valid interior stationary point.</li>
 * </ul>
 * </p>
 */
public final class Pursuit1DSolver {

    private Pursuit1DSolver() {
        // Utility class
    }

    /**
     * Solves 1D pursuit over an unbounded interval [0, ∞) using default tolerance policy.
     */
    public static Pursuit1DResult solve(double x0, double vRel0, double aRel) {
        return solve(x0, vRel0, aRel, Double.POSITIVE_INFINITY, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves 1D pursuit over a finite active interval [0, activeIntervalDuration] using default tolerance policy.
     */
    public static Pursuit1DResult solve(double x0, double vRel0, double aRel, double activeIntervalDuration) {
        return solve(x0, vRel0, aRel, activeIntervalDuration, RelativeTolerancePolicy.DEFAULT);
    }

    /**
     * Solves 1D pursuit with fully specified parameters and scale-aware numerical tolerance policy.
     */
    public static Pursuit1DResult solve(double x0,
                                        double vRel0,
                                        double aRel,
                                        double activeIntervalDuration,
                                        RelativeTolerancePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (!Double.isFinite(x0) || !Double.isFinite(vRel0) || !Double.isFinite(aRel)) {
            throw new IllegalArgumentException("Parameters must be finite: x0=" + x0 + ", vRel0=" + vRel0 + ", aRel=" + aRel);
        }
        if (activeIntervalDuration < 0.0) {
            throw new IllegalArgumentException("activeIntervalDuration must be non-negative: " + activeIntervalDuration);
        }

        double characteristicScale = Math.max(Math.abs(x0), 1.0);
        double discriminant = vRel0 * vRel0 - 2.0 * aRel * x0;
        double tExt = (aRel != 0.0) ? -vRel0 / aRel : Double.NaN;

        // 1. Initial Collision Precedence (exact or machine zero x0 == 0)
        boolean isCoLocated = (x0 == 0.0) || policy.isMachineZero(x0, characteristicScale);
        if (isCoLocated) {
            if (policy.isZeroAcceleration(aRel, 1.0) && policy.isZeroVelocity(vRel0, 1.0)) {
                return new Pursuit1DResult(
                        0.0,
                        vRel0,
                        aRel,
                        discriminant,
                        OptionalDouble.of(0.0),
                        OptionalDouble.empty(),
                        0.0,
                        activeIntervalDuration,
                        Pursuit1DStatus.PERMANENT_CO_LOCATION
                );
            }

            // Check if subsequent non-zero root exists for educational explanation
            OptionalDouble subsequentRoot = OptionalDouble.empty();
            if (!policy.isZeroAcceleration(aRel, 1.0)) {
                // x_rel(t) = t * (0.5 * aRel * t + vRel0) = 0 => t_sub = -2 * vRel0 / aRel
                double tSub = -2.0 * vRel0 / aRel;
                if (tSub > policy.velocityTolerance() && tSub <= activeIntervalDuration) {
                    subsequentRoot = OptionalDouble.of(tSub);
                }
            }

            return new Pursuit1DResult(
                    0.0,
                    vRel0,
                    aRel,
                    discriminant,
                    OptionalDouble.of(0.0),
                    subsequentRoot,
                    0.0,
                    activeIntervalDuration,
                    Pursuit1DStatus.INITIAL_COLLISION_CO_LOCATED
            );
        }

        // 2. Zero-Acceleration Linear Degeneracy (a_rel == 0)
        if (policy.isZeroAcceleration(aRel, 1.0)) {
            if (policy.isZeroVelocity(vRel0, 1.0)) {
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        0.0,
                        discriminant,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        Math.abs(x0),
                        activeIntervalDuration,
                        Pursuit1DStatus.CONSTANT_NONZERO_SEPARATION
                );
            }

            double tLin = -x0 / vRel0;
            if (tLin > 0.0) {
                if (tLin <= activeIntervalDuration) {
                    return new Pursuit1DResult(
                            x0,
                            vRel0,
                            0.0,
                            discriminant,
                            OptionalDouble.of(tLin),
                            OptionalDouble.empty(),
                            0.0,
                            activeIntervalDuration,
                            Pursuit1DStatus.LINEAR_COLLISION
                    );
                } else {
                    double dMin = evaluateMinimumSeparation(x0, vRel0, 0.0, activeIntervalDuration, Double.NaN);
                    return new Pursuit1DResult(
                            x0,
                            vRel0,
                            0.0,
                            discriminant,
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            dMin,
                            activeIntervalDuration,
                            Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED
                    );
                }
            } else {
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        0.0,
                        discriminant,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        Math.abs(x0),
                        activeIntervalDuration,
                        Pursuit1DStatus.NO_COLLISION_DIVERGING
                );
            }
        }

        // 3. Accelerated Quadratic Pursuit (a_rel != 0)
        double fourAc = 2.0 * aRel * x0;
        double machineThreshold = Math.max(1e-24, RelativeTolerancePolicy.MACHINE_EPSILON * Math.max(vRel0 * vRel0, Math.abs(fourAc)));

        // Genuinely negative discriminant (Delta < -machineThreshold): No real collision roots
        if (discriminant < -machineThreshold) {
            double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
            Pursuit1DStatus status;
            if (tExt > 0.0 && tExt <= activeIntervalDuration) {
                status = Pursuit1DStatus.NO_COLLISION_MINIMUM_SEPARATION;
            } else if (tExt <= 0.0) {
                status = Pursuit1DStatus.NO_COLLISION_DIVERGING;
            } else {
                status = Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED;
            }

            return new Pursuit1DResult(
                    x0,
                    vRel0,
                    aRel,
                    discriminant,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    dMin,
                    activeIntervalDuration,
                    status
            );
        }

        // Exact machine zero discriminant: repeated root / glancing contact
        if (Math.abs(discriminant) <= machineThreshold) {
            double tRepeated = -vRel0 / aRel;
            return handleRepeatedRoot(x0, vRel0, aRel, tRepeated, activeIntervalDuration, tExt);
        }

        // Positive Discriminant (Delta > machineThreshold): Two real roots computed via stable Citardauq
        double sqrtDelta = Math.sqrt(Math.max(0.0, discriminant));
        double sgnB = (vRel0 >= 0.0) ? 1.0 : -1.0;
        double q = -0.5 * (vRel0 + sgnB * sqrtDelta);
        double tA = q / (0.5 * aRel);
        double tB = (q != 0.0) ? x0 / q : 0.0;

        double tEarly = Math.min(tA, tB);
        double tLate = Math.max(tA, tB);

        // Time-Domain Coalescence Contract:
        // A small positive discriminant produces two distinct real roots unless the time separation
        // |tLate - tEarly| falls within the numerical time resolution scale.
        double timeScale = Math.max(Math.max(Math.abs(tEarly), Math.abs(tLate)), 1.0);
        if (policy.areTimesEqual(tEarly, tLate, timeScale)) {
            double tGlancing = 0.5 * (tEarly + tLate);
            return handleRepeatedRoot(x0, vRel0, aRel, tGlancing, activeIntervalDuration, tExt);
        }

        // Two distinct, numerically distinguishable collision roots preserved:
        if (tEarly > 0.0) {
            if (tEarly <= activeIntervalDuration) {
                OptionalDouble subsequent = (tLate > 0.0 && tLate <= activeIntervalDuration)
                        ? OptionalDouble.of(tLate)
                        : OptionalDouble.empty();
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        discriminant,
                        OptionalDouble.of(tEarly),
                        subsequent,
                        0.0,
                        activeIntervalDuration,
                        Pursuit1DStatus.COLLISION_FIRST_PASS
                );
            } else {
                double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        discriminant,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        dMin,
                        activeIntervalDuration,
                        Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED
                );
            }
        } else if (tLate > 0.0) {
            if (tLate <= activeIntervalDuration) {
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        discriminant,
                        OptionalDouble.of(tLate),
                        OptionalDouble.empty(),
                        0.0,
                        activeIntervalDuration,
                        Pursuit1DStatus.COLLISION_OVERTAKE
                );
            } else {
                double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        discriminant,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        dMin,
                        activeIntervalDuration,
                        Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED
                );
            }
        } else {
            // Both roots non-positive
            double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
            return new Pursuit1DResult(
                    x0,
                    vRel0,
                    aRel,
                    discriminant,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    dMin,
                    activeIntervalDuration,
                    Pursuit1DStatus.NO_COLLISION_DIVERGING
            );
        }
    }

    private static Pursuit1DResult handleRepeatedRoot(double x0, double vRel0, double aRel, double tRepeated,
                                                      double activeIntervalDuration, double tExt) {
        if (tRepeated > 0.0) {
            if (tRepeated <= activeIntervalDuration) {
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        0.0,
                        OptionalDouble.of(tRepeated),
                        OptionalDouble.empty(),
                        0.0,
                        activeIntervalDuration,
                        Pursuit1DStatus.GLANCING_CONTACT
                );
            } else {
                double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
                return new Pursuit1DResult(
                        x0,
                        vRel0,
                        aRel,
                        0.0,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        dMin,
                        activeIntervalDuration,
                        Pursuit1DStatus.NO_COLLISION_INTERVAL_EXPIRED
                );
            }
        } else {
            double dMin = evaluateMinimumSeparation(x0, vRel0, aRel, activeIntervalDuration, tExt);
            return new Pursuit1DResult(
                    x0,
                    vRel0,
                    aRel,
                    0.0,
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    dMin,
                    activeIntervalDuration,
                    Pursuit1DStatus.NO_COLLISION_DIVERGING
            );
        }
    }

    /**
     * Evaluates the minimum absolute separation min |x_rel(t)| across endpoints and valid turning point.
     */
    private static double evaluateMinimumSeparation(double x0, double vRel0, double aRel, double activeIntervalDuration, double tExt) {
        double dMin = Math.abs(x0);

        if (Double.isFinite(activeIntervalDuration) && activeIntervalDuration > 0.0) {
            double xT = x0 + vRel0 * activeIntervalDuration + 0.5 * aRel * activeIntervalDuration * activeIntervalDuration;
            dMin = Math.min(dMin, Math.abs(xT));
        }

        if (Double.isFinite(tExt) && aRel != 0.0 && tExt > 0.0) {
            if (!Double.isFinite(activeIntervalDuration) || tExt < activeIntervalDuration) {
                double xExt = x0 - (vRel0 * vRel0) / (2.0 * aRel);
                dMin = Math.min(dMin, Math.abs(xExt));
            }
        }

        return dMin;
    }
}

