package edu.physics.problem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ordered multipart problem. Later parts may refer to one named result from an earlier part. */
public record ProjectileProblemSequence(
        String statement,
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens,
        List<Part> parts) {

    public record ResultReference(int partIndex, String quantityName) {
        public ResultReference {
            if (partIndex < 0) throw new IllegalArgumentException("Referenced part index must be non-negative");
            if (quantityName == null || quantityName.isBlank()) throw new IllegalArgumentException("Referenced quantity name is required");
        }
    }

    public record Part(
            ProjectileProblem.Target target,
            Map<ProjectileProblem.Input, PhysicalQuantity> givens,
            Map<ProjectileProblem.Input, ResultReference> references) {
        public Part {
            if (target == null) throw new IllegalArgumentException("Part target is required");
            givens = immutableEnumMap(givens);
            references = immutableEnumMap(references);
            if (givens.keySet().stream().anyMatch(references::containsKey)) {
                throw new IllegalArgumentException("A part input cannot be both given and referenced");
            }
        }
    }

    public ProjectileProblemSequence {
        if (statement == null || statement.isBlank()) throw new IllegalArgumentException("Sequence statement is required");
        sharedGivens = immutableEnumMap(sharedGivens);
        parts = List.copyOf(parts);
        if (parts.isEmpty()) throw new IllegalArgumentException("A sequence must contain at least one part");
    }

    private static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(Map<K, V> source) {
        if (source == null) throw new IllegalArgumentException("Input map is required");
        if (source.isEmpty()) return Map.of();
        EnumMap<K, V> copy = new EnumMap<>(source);
        if (copy.containsValue(null)) throw new IllegalArgumentException("Input values cannot be null");
        return Collections.unmodifiableMap(copy);
    }
}
