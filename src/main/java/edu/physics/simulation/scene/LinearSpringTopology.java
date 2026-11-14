package edu.physics.simulation.scene;

import java.util.List;
import java.util.Objects;

/**
 * Declarative topology specifying a linear spring connection between two anchors.
 */
public record LinearSpringTopology(
        String relationshipId,
        AnchorRef endA,
        AnchorRef endB,
        double stiffness,
        double restLength) implements StructuralRelationship {

    public LinearSpringTopology {
        Objects.requireNonNull(relationshipId, "relationshipId is required");
        if (relationshipId.isBlank()) throw new IllegalArgumentException("relationshipId cannot be blank");
        Objects.requireNonNull(endA, "endA is required");
        Objects.requireNonNull(endB, "endB is required");
        if (!Double.isFinite(stiffness) || stiffness <= 0.0) {
            throw new IllegalArgumentException("stiffness must be finite and > 0: " + stiffness);
        }
        if (!Double.isFinite(restLength) || restLength < 0.0) {
            throw new IllegalArgumentException("restLength must be finite and >= 0: " + restLength);
        }
    }

    public static LinearSpringTopology of(String id, AnchorRef endA, AnchorRef endB, double stiffness, double restLength) {
        return new LinearSpringTopology(id, endA, endB, stiffness, restLength);
    }

    @Override
    public RelationshipType type() {
        return RelationshipType.LINEAR_SPRING;
    }

    @Override
    public void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics) {
        if (scene.resolveAnchor(endA).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Spring endA unresolved: " + endA));
        }
        if (scene.resolveAnchor(endB).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Spring endB unresolved: " + endB));
        }
        if (stiffness <= 0.0 || !Double.isFinite(stiffness)) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.INVALID_PARAMETER,
                    relationshipId,
                    "Spring stiffness must be positive: " + stiffness));
        }
    }
}

