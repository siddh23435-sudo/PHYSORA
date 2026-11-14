package edu.physics.simulation.scene;

import java.util.List;

/**
 * Sealed common contract for declarative structural relationship topologies in a laboratory scene.
 */
public sealed interface StructuralRelationship permits
        CableTopology,
        RevoluteJointTopology,
        SurfaceContactTopology,
        DistanceLinkTopology,
        LinearSpringTopology {

    String relationshipId();

    RelationshipType type();

    /**
     * Inspects topological references against the scene and collects structured diagnostics.
     */
    void collectValidationErrors(LaboratoryScene scene, List<SceneDiagnostic> diagnostics);
}

