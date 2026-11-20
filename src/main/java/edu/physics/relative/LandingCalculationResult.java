package edu.physics.relative;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Result of analytical landing and surface intersection calculations.
 *
 * @param firstFutureIntersectionTime Earliest time t > 0 where trajectory intersects y = y_surface (if any)
 * @param firstDownwardCrossingTime   Earliest time t > 0 where trajectory crosses downward (v_y < 0)
 * @param tangentialContactTime       Time t > 0 where trajectory apex touches surface tangentially (v_y = 0)
 * @param physicalLandingTime         Effective flight termination time for open-terrain projectile motion
 * @param classification              Geometric and physical classification of the surface interaction
 */
public record LandingCalculationResult(
        OptionalDouble firstFutureIntersectionTime,
        OptionalDouble firstDownwardCrossingTime,
        OptionalDouble tangentialContactTime,
        OptionalDouble physicalLandingTime,
        LandingClassification classification
) {

    public LandingCalculationResult {
        Objects.requireNonNull(firstFutureIntersectionTime, "firstFutureIntersectionTime must not be null");
        Objects.requireNonNull(firstDownwardCrossingTime, "firstDownwardCrossingTime must not be null");
        Objects.requireNonNull(tangentialContactTime, "tangentialContactTime must not be null");
        Objects.requireNonNull(physicalLandingTime, "physicalLandingTime must not be null");
        Objects.requireNonNull(classification, "classification must not be null");
    }

    /**
     * Returns true if flight physically terminates on the surface in finite future time.
     */
    public boolean hasPhysicalLanding() {
        return physicalLandingTime.isPresent();
    }
}

