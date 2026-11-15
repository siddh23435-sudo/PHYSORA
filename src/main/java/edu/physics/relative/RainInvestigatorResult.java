package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of two-observation inverse rain velocity reconstruction.
 *
 * <p>Represents the deduced ground-frame rain velocity (if solvable), conditioning diagnostics,
 * estimated downward relative uncertainty, and diagnostic feedback.</p>
 *
 * @param status                      Physical and numerical outcome classification
 * @param reconstructedRainVelocity   Deduced ground rain velocity (m/s), non-null iff solvable
 * @param downwardConditionNumber     Dimensionless downward condition metric kappa_down, NaN if unsolvable
 * @param estimatedDownwardRelError   Estimated relative downward uncertainty (kappa_down * sigma_phi), NaN if unsolvable
 * @param diagnosticMessage           Pedagogical feedback or error explanation
 * @param vPerson1                    First observer walking velocity (m/s)
 * @param phiRad1                     First observed umbrella tilt angle (radians)
 * @param vPerson2                    Second observer walking velocity (m/s)
 * @param phiRad2                     Second observed umbrella tilt angle (radians)
 */
public record RainInvestigatorResult(
        RainInvestigatorStatus status,
        Vector2D reconstructedRainVelocity,
        double downwardConditionNumber,
        double estimatedDownwardRelError,
        String diagnosticMessage,
        double vPerson1,
        double phiRad1,
        double vPerson2,
        double phiRad2
) {

    public RainInvestigatorResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(diagnosticMessage, "diagnosticMessage must not be null");

        if (status.isSolvable()) {
            Objects.requireNonNull(reconstructedRainVelocity,
                    "reconstructedRainVelocity must not be null for solvable status: " + status);
            if (!Double.isFinite(reconstructedRainVelocity.x()) || !Double.isFinite(reconstructedRainVelocity.y())) {
                throw new IllegalArgumentException("reconstructedRainVelocity coordinates must be finite: " + reconstructedRainVelocity);
            }
            if (!Double.isFinite(downwardConditionNumber) || downwardConditionNumber < 0.0) {
                throw new IllegalArgumentException("downwardConditionNumber must be non-negative and finite for solvable status: " + downwardConditionNumber);
            }
            if (!Double.isFinite(estimatedDownwardRelError) || estimatedDownwardRelError < 0.0) {
                throw new IllegalArgumentException("estimatedDownwardRelError must be non-negative and finite for solvable status: " + estimatedDownwardRelError);
            }
        } else {
            if (reconstructedRainVelocity != null) {
                throw new IllegalArgumentException("reconstructedRainVelocity must be null for unsolvable status: " + status);
            }
            if (!Double.isNaN(downwardConditionNumber)) {
                throw new IllegalArgumentException("downwardConditionNumber must be Double.NaN for unsolvable status: " + status);
            }
            if (!Double.isNaN(estimatedDownwardRelError)) {
                throw new IllegalArgumentException("estimatedDownwardRelError must be Double.NaN for unsolvable status: " + status);
            }
        }
    }

    /**
     * Factory method for creating a valid or ill-conditioned solvable result.
     */
    public static RainInvestigatorResult solvable(
            RainInvestigatorStatus status,
            Vector2D reconstructedRainVelocity,
            double downwardConditionNumber,
            double estimatedDownwardRelError,
            String diagnosticMessage,
            double vPerson1,
            double phiRad1,
            double vPerson2,
            double phiRad2) {
        if (!status.isSolvable()) {
            throw new IllegalArgumentException("Status must be solvable: " + status);
        }
        return new RainInvestigatorResult(
                status,
                reconstructedRainVelocity,
                downwardConditionNumber,
                estimatedDownwardRelError,
                diagnosticMessage,
                vPerson1, phiRad1, vPerson2, phiRad2
        );
    }

    /**
     * Factory method for creating an unsolvable result with invariant null/NaN fields.
     */
    public static RainInvestigatorResult unsolvable(
            RainInvestigatorStatus status,
            String diagnosticMessage,
            double vPerson1,
            double phiRad1,
            double vPerson2,
            double phiRad2) {
        if (status.isSolvable()) {
            throw new IllegalArgumentException("Status must be unsolvable: " + status);
        }
        return new RainInvestigatorResult(
                status,
                null,
                Double.NaN,
                Double.NaN,
                diagnosticMessage,
                vPerson1, phiRad1, vPerson2, phiRad2
        );
    }

    /**
     * Returns true if a valid or ill-conditioned rain velocity reconstruction was achieved.
     */
    public boolean isSolvable() {
        return status.isSolvable();
    }

    /**
     * Optional accessor for reconstructed true ground rain velocity.
     * Empty if unsolvable.
     */
    public Optional<Vector2D> reconstructedRain() {
        return Optional.ofNullable(reconstructedRainVelocity);
    }
}

