package edu.physics.simulation.scene;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative topology of a cable or string running between two anchors and routed over zero or more pulleys.
 *
 * <p>Invariant: Stage 3B strictly requires an explicitly supplied positive cable length.
 * Cable length is not inferred from endpoint distances; wrap angle and tangent kinematics
 * are reserved for Stage 3C.
 */
public record CableTopology(
        String relationshipId,
        AnchorRef startAnchor,
        AnchorRef endAnchor,
        List<AnchorRef> pulleyPath,
        Double specifiedLength) implements StructuralRelationship {

    public CableTopology {
        Objects.requireNonNull(relationshipId, "relationshipId is required");
        if (relationshipId.isBlank()) throw new IllegalArgumentException("relationshipId cannot be blank");
        Objects.requireNonNull(startAnchor, "startAnchor is required");
        Objects.requireNonNull(endAnchor, "endAnchor is required");
        pulleyPath = pulleyPath != null ? List.copyOf(pulleyPath) : List.of();
    }

    public static CableTopology of(String id, AnchorRef start, AnchorRef end, List<AnchorRef> pulleys, double length) {
        return new CableTopology(id, start, end, pulleys, length);
    }

    public static CableTopology of(String id, AnchorRef start, AnchorRef end, double length) {
        return new CableTopology(id, start, end, List.of(), length);
    }

    @Override
    public RelationshipType type() {
        return RelationshipType.PULLEY_CABLE;
    }

    @Override
    public void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics) {
        if (scene.resolveAnchor(startAnchor).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Cable start anchor unresolved: " + startAnchor));
        }
        if (scene.resolveAnchor(endAnchor).isEmpty()) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                    relationshipId,
                    "Cable end anchor unresolved: " + endAnchor));
        }
        for (AnchorRef pulleyRef : pulleyPath) {
            if (scene.resolveAnchor(pulleyRef).isEmpty()) {
                diagnostics.add(SceneDiagnostic.error(
                        SceneDiagnostic.DiagnosticCode.MISSING_ANCHOR,
                        relationshipId,
                        "Cable intermediate pulley anchor unresolved: " + pulleyRef));
            }
        }
        if (specifiedLength == null || !Double.isFinite(specifiedLength) || specifiedLength <= 0.0) {
            diagnostics.add(SceneDiagnostic.error(
                    SceneDiagnostic.DiagnosticCode.INVALID_CABLE_LENGTH,
                    relationshipId,
                    "Cable " + relationshipId + " must explicitly specify a finite positive length (> 0)"));
        }
    }
}

