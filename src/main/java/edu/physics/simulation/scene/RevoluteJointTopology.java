package edu.physics.simulation.scene;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Declarative topology pinning a rigid body's center of mass or local point to a world anchor
 * or another body (e.g. pendulum pivot, pulley axle pin).
 */
public record RevoluteJointTopology(
        String relationshipId,
        UUID bodyId,
        AnchorRef pinAnchor) implements StructuralRelationship {

    public RevoluteJointTopology {
        Objects.requireNonNull(relationshipId, "relationshipId is required");
        if (relationshipId.isBlank()) throw new IllegalArgumentException("relationshipId cannot be blank");
        Objects.requireNonNull(bodyId, "bodyId is required");
        Objects.requireNonNull(pinAnchor, "pinAnchor is required");
    }

    public static RevoluteJointTopology of(String id, UUID bodyId, AnchorRef pinAnchor) {
        return new RevoluteJointTopology(id, bodyId, pinAnchor);
    }

    @Override
    public RelationshipType type() {
        return RelationshipType.REVOLUTE_JOINT;
    }

    @Override
    public void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics) {
        if (!scene.bodies().containsKey(bodyId)) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_BODY,
                    relationshipId,
                    "Revolute joint target body does not exist in scene: " + bodyId));
        }
        if (scene.resolveAnchor(pinAnchor).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Revolute joint pin anchor unresolved: " + pinAnchor));
        }
    }
}

