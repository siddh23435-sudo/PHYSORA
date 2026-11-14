package edu.physics.simulation.scene;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Declarative topology declaring that a rigid body rests on or contacts a static surface.
 */
public record SurfaceContactTopology(
        String relationshipId,
        UUID bodyId,
        String surfaceId) implements StructuralRelationship {

    public SurfaceContactTopology {
        Objects.requireNonNull(relationshipId, "relationshipId is required");
        if (relationshipId.isBlank()) throw new IllegalArgumentException("relationshipId cannot be blank");
        Objects.requireNonNull(bodyId, "bodyId is required");
        Objects.requireNonNull(surfaceId, "surfaceId is required");
        if (surfaceId.isBlank()) throw new IllegalArgumentException("surfaceId cannot be blank");
    }

    public static SurfaceContactTopology of(String id, UUID bodyId, String surfaceId) {
        return new SurfaceContactTopology(id, bodyId, surfaceId);
    }

    @Override
    public RelationshipType type() {
        return RelationshipType.SURFACE_CONTACT;
    }

    @Override
    public void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics) {
        if (!scene.bodies().containsKey(bodyId)) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_BODY,
                    relationshipId,
                    "Surface contact target body does not exist in scene: " + bodyId));
        }
        if (scene.findSurface(surfaceId).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_SURFACE,
                    relationshipId,
                    "Surface contact target surface does not exist in scene: " + surfaceId));
        }
    }
}

