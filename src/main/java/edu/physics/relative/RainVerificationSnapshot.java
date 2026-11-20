package edu.physics.relative;

import edu.physics.simulation.Vector2D;

import java.util.Objects;

/**
 * Immutable snapshot of forward round-trip verification calculations and residuals.
 *
 * <p>Prevents UI consumers from recomputing verification mathematics. Both calculated angles,
 * residuals, maximum residual, verification classification, and sensitivity disclosures
 * are exposed immutably.</p>
 */
public record RainVerificationSnapshot(
        RainVerificationStatus status,
        double calculatedAngleRad1,
        double calculatedAngleRad2,
        double calculatedAngleDeg1,
        double calculatedAngleDeg2,
        double residualRad1,
        double residualRad2,
        double residualDeg1,
        double residualDeg2,
        double maxResidualRad,
        double maxResidualDeg,
        boolean isSensitive,
        String sensitivityNotice,
        String summaryMessage
) {

    public static final double DEFAULT_VERIFICATION_THRESHOLD_DEG = 1e-4; // 0.0001 deg

    public RainVerificationSnapshot {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(sensitivityNotice, "sensitivityNotice must not be null");
        Objects.requireNonNull(summaryMessage, "summaryMessage must not be null");
    }

    /**
     * Factory method creating an unverified snapshot for unsolvable observations.
     */
    public static RainVerificationSnapshot notVerifiable(String summaryMessage) {
        return new RainVerificationSnapshot(
                RainVerificationStatus.NOT_VERIFIABLE,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                false,
                "",
                summaryMessage
        );
    }

    /**
     * Computes verification residuals by evaluating the reconstructed rain vector through forward simulation.
     */
    public static RainVerificationSnapshot compute(
            Vector2D reconstructedRain,
            double vPerson1, double phiRad1,
            double vPerson2, double phiRad2,
            RainInvestigatorStatus evaluatorStatus,
            double conditionNumber,
            double thresholdDeg) {

        if (reconstructedRain == null || !evaluatorStatus.isSolvable()) {
            return notVerifiable("Evaluator result is unsolvable (" + evaluatorStatus + "): verification unavailable.");
        }

        RainManResult forward1 = RainManSolver.solve(reconstructedRain, new Vector2D(vPerson1, 0.0));
        RainManResult forward2 = RainManSolver.solve(reconstructedRain, new Vector2D(vPerson2, 0.0));

        double calcRad1 = forward1.umbrellaAngleRad();
        double calcRad2 = forward2.umbrellaAngleRad();
        double calcDeg1 = Math.toDegrees(calcRad1);
        double calcDeg2 = Math.toDegrees(calcRad2);

        double resRad1 = Math.abs(calcRad1 - phiRad1);
        double resRad2 = Math.abs(calcRad2 - phiRad2);
        double resDeg1 = Math.toDegrees(resRad1);
        double resDeg2 = Math.toDegrees(resRad2);

        double maxResRad = Math.max(resRad1, resRad2);
        double maxResDeg = Math.max(resDeg1, resDeg2);

        boolean isSensitive = (evaluatorStatus == RainInvestigatorStatus.ILL_CONDITIONED);
        String sensitivityNotice = isSensitive
                ? String.format(java.util.Locale.ROOT,
                "Reconstruction is sensitive to measurement error (kappa_down = %.2f). " +
                "Forward check confirms mathematical consistency, but small angular errors in practice produce large velocity variations.",
                conditionNumber)
                : "";

        RainVerificationStatus status = (maxResDeg <= thresholdDeg)
                ? RainVerificationStatus.VERIFIED
                : RainVerificationStatus.NUMERICAL_WARNING;

        String summaryMessage;
        if (status == RainVerificationStatus.VERIFIED) {
            summaryMessage = String.format(java.util.Locale.ROOT,
                    "Reconstruction Verified: Forward simulation reproduces observation angles to within %.6f° (max residual %.6f° <= %.4f° threshold).",
                    maxResDeg, maxResDeg, thresholdDeg);
        } else {
            summaryMessage = String.format(java.util.Locale.ROOT,
                    "Verification Discrepancy: Maximum angular residual is %.6f°, exceeding the %.4f° threshold.",
                    maxResDeg, thresholdDeg);
        }

        return new RainVerificationSnapshot(
                status,
                calcRad1, calcRad2,
                calcDeg1, calcDeg2,
                resRad1, resRad2,
                resDeg1, resDeg2,
                maxResRad, maxResDeg,
                isSensitive,
                sensitivityNotice,
                summaryMessage
        );
    }

    /**
     * Computes verification using default threshold (1e-4 deg).
     */
    public static RainVerificationSnapshot compute(
            Vector2D reconstructedRain,
            double vPerson1, double phiRad1,
            double vPerson2, double phiRad2,
            RainInvestigatorStatus evaluatorStatus,
            double conditionNumber) {
        return compute(reconstructedRain, vPerson1, phiRad1, vPerson2, phiRad2, evaluatorStatus, conditionNumber, DEFAULT_VERIFICATION_THRESHOLD_DEG);
    }
}

