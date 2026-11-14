package edu.physics.simulation.scene;

import java.util.List;
import java.util.Objects;

/**
 * Declarative topology specifying a fixed distance constraint between two anchors (e.g. rigid link or pendulum rod).
 */
public record DistanceLinkTopology(
        String relationshipId,
        AnchorRef endA,
        AnchorRef endB,
        double targetDistance) implements StructuralRelationship {

    public DistanceLinkTopology {
        Objects.requireNonNull(relationshipId, "relationshipId is required");
        if (relationshipId.isBlank()) throw new IllegalArgumentException("relationshipId cannot be blank");
        Objects.requireNonNull(endA, "endA is required");
        Objects.requireNonNull(endB, "endB is required");
        if (!Double.isFinite(targetDistance) || targetDistance <= 0.0) {
            throw new IllegalArgumentException("targetDistance must be finite and > 0: " + targetDistance);
        }
    }

    public static DistanceLinkTopology of(String id, AnchorRef endA, AnchorRef endB, double targetDistance) {
        return new DistanceLinkTopology(id, endA, endB, targetDistance);
    }

    @Override
    public RelationshipType type() {
        return RelationshipType.DISTANCE_LINK;
    }

    @Override
    public void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics) {
        if (scene.resolveAnchor(endA).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Distance link endA unresolved: " + endA));
        }
        if (scene.resolveAnchor(endB).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Distance link endB unresolved: " + endB));
        }
        if (targetDistance <= 0.0 || !Double.isFinite(targetDistance)) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.INVALID_PARAMETER,
                    relationshipId,
                    "Distance link must have finite positive targetDistance: " + targetDistance));
        }
    }
}

