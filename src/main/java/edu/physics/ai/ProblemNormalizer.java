package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts validated quantities to the canonical units expected by the projectile domain. */
public final class ProblemNormalizer {
    public NormalizedPart normalize(ProjectileProblem.Target target,
                                    Map<ProjectileProblem.Input, PhysicalQuantity> givens) {
        if (target == null || givens == null) throw new IllegalArgumentException("Target and givens are required");
        EnumMap<ProjectileProblem.Input, PhysicalQuantity> original = new EnumMap<>(ProjectileProblem.Input.class);
        original.putAll(givens);
        EnumMap<ProjectileProblem.Input, PhysicalQuantity> normalized = new EnumMap<>(ProjectileProblem.Input.class);
        for (Map.Entry<ProjectileProblem.Input, PhysicalQuantity> entry : original.entrySet()) {
            ProjectileProblem.Input input = entry.getKey();
            PhysicalQuantity quantity = entry.getValue();
            if (input == null || quantity == null) throw new IllegalArgumentException("Input and quantity are required");
            if (input.dimension() != quantity.unit().dimension()) {
                throw new IllegalArgumentException("Incompatible unit for " + input);
            }
            double value = quantity.valueInSi();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Quantity is outside the supported numeric range: " + input);
            if ((input == ProjectileProblem.Input.INITIAL_SPEED || input == ProjectileProblem.Input.GRAVITY
                    || input == ProjectileProblem.Input.TIME || input == ProjectileProblem.Input.TARGET_SPEED) && value < 0.0) {
                throw new IllegalArgumentException("Quantity cannot be negative: " + input);
            }
            normalized.put(input, new PhysicalQuantity(value, canonicalUnit(input)));
        }
        return new NormalizedPart(target, original, normalized);
    }

    static PhysicalUnit canonicalUnit(ProjectileProblem.Input input) {
        return switch (input.dimension()) {
            case DIMENSIONLESS -> PhysicalUnit.ONE;
            case LENGTH -> PhysicalUnit.METRE;
            case TIME -> PhysicalUnit.SECOND;
            case SPEED -> PhysicalUnit.METRE_PER_SECOND;
            case ACCELERATION -> PhysicalUnit.METRE_PER_SECOND_SQUARED;
            // The solver's public angle contract is degrees; conversion from radians occurs here.
            case ANGLE -> PhysicalUnit.DEGREE;
        };
    }

    public record NormalizedPart(ProjectileProblem.Target target,
                                 Map<ProjectileProblem.Input, PhysicalQuantity> originalGivens,
                                 Map<ProjectileProblem.Input, PhysicalQuantity> normalizedGivens) {
        public NormalizedPart {
            if (target == null) throw new IllegalArgumentException("Target is required");
            originalGivens = immutableEnumMap(originalGivens);
            normalizedGivens = immutableEnumMap(normalizedGivens);
        }

        private static Map<ProjectileProblem.Input, PhysicalQuantity> immutableEnumMap(
                Map<ProjectileProblem.Input, PhysicalQuantity> source) {
            EnumMap<ProjectileProblem.Input, PhysicalQuantity> copy = new EnumMap<>(ProjectileProblem.Input.class);
            copy.putAll(source);
            return java.util.Collections.unmodifiableMap(copy);
        }
    }
}
