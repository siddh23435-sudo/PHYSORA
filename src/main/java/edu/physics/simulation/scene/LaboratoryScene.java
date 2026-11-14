package edu.physics.simulation.scene;

import edu.physics.simulation.RigidBody;
import edu.physics.simulation.RigidBodyState;
import edu.physics.simulation.SimulationWorld;
import edu.physics.simulation.UniformGravity;
import edu.physics.simulation.Vector2D;
import edu.physics.simulation.geometry.Transform2D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable declarative blueprint describing a complete physics laboratory arrangement.
 *
 * <p>Captures physical bodies, geometric shapes, initial poses, world anchors, static surfaces,
 * and structural relationship topologies.
 *
 * <p>Provides validation and acts as a factory for {@link SimulationWorld}. Contains no mutable
 * simulation runtime variables.
 */
public final class LaboratoryScene {

    private final String sceneId;
    private final String title;
    private final Vector2D gravity;

    private final Map<UUID, SceneBody> bodies;
    private final Map<String, SceneAnchor> anchors;
    private final Map<String, StaticSurface> surfaces;
    private final List<StructuralRelationship> relationships;

    public LaboratoryScene(
            String sceneId,
            String title,
            Vector2D gravity,
            Collection<SceneBody> bodies,
            Collection<SceneAnchor> anchors,
            Collection<StaticSurface> surfaces,
            Collection<StructuralRelationship> relationships) {

        this.sceneId = Objects.requireNonNull(sceneId, "sceneId is required");
        this.title = Objects.requireNonNull(title, "title is required");
        this.gravity = Objects.requireNonNull(gravity, "gravity is required");

        // Fail-fast uniqueness check on bodies
        Map<UUID, SceneBody> bodyMap = new LinkedHashMap<>();
        if (bodies != null) {
            for (SceneBody b : bodies) {
                if (bodyMap.containsKey(b.id())) {
                    throw new IllegalArgumentException("Duplicate body UUID detected: " + b.id());
                }
                bodyMap.put(b.id(), b);
            }
        }
        this.bodies = Collections.unmodifiableMap(bodyMap);

        // Fail-fast uniqueness check on anchors
        Map<String, SceneAnchor> anchorMap = new LinkedHashMap<>();
        if (anchors != null) {
            for (SceneAnchor a : anchors) {
                if (anchorMap.containsKey(a.anchorId())) {
                    throw new IllegalArgumentException("Duplicate anchor ID detected: " + a.anchorId());
                }
                anchorMap.put(a.anchorId(), a);
            }
        }
        this.anchors = Collections.unmodifiableMap(anchorMap);

        // Fail-fast uniqueness check on surfaces
        Map<String, StaticSurface> surfaceMap = new LinkedHashMap<>();
        if (surfaces != null) {
            for (StaticSurface s : surfaces) {
                if (surfaceMap.containsKey(s.surfaceId())) {
                    throw new IllegalArgumentException("Duplicate surface ID detected: " + s.surfaceId());
                }
                surfaceMap.put(s.surfaceId(), s);
            }
        }
        this.surfaces = Collections.unmodifiableMap(surfaceMap);

        // Fail-fast uniqueness check on relationships
        Map<String, StructuralRelationship> relMap = new LinkedHashMap<>();
        if (relationships != null) {
            for (StructuralRelationship r : relationships) {
                if (relMap.containsKey(r.relationshipId())) {
                    throw new IllegalArgumentException("Duplicate relationship ID detected: " + r.relationshipId());
                }
                relMap.put(r.relationshipId(), r);
            }
        }
        this.relationships = List.copyOf(relMap.values());
    }

    public static Builder builder(String sceneId, String title) {
        return new Builder(sceneId, title);
    }

    public String sceneId() {
        return sceneId;
    }

    public String title() {
        return title;
    }

    public Vector2D gravity() {
        return gravity;
    }

    public Map<UUID, SceneBody> bodies() {
        return bodies;
    }

    public Map<String, SceneAnchor> anchors() {
        return anchors;
    }

    public Map<String, StaticSurface> surfaces() {
        return surfaces;
    }

    public List<StructuralRelationship> relationships() {
        return relationships;
    }

    public Optional<SceneBody> findBody(UUID bodyId) {
        return Optional.ofNullable(bodies.get(bodyId));
    }

    public Optional<SceneAnchor> findAnchor(String anchorId) {
        return Optional.ofNullable(anchors.get(anchorId));
    }

    public Optional<StaticSurface> findSurface(String surfaceId) {
        return Optional.ofNullable(surfaces.get(surfaceId));
    }

    public Optional<AttachmentPoint> findAttachmentPoint(UUID bodyId, String pointId) {
        SceneBody body = bodies.get(bodyId);
        return body != null ? body.findAttachmentPoint(pointId) : Optional.empty();
    }

    /**
     * Resolves an anchor reference to its initial world coordinate.
     */
    public Optional<Vector2D> resolveAnchor(AnchorRef ref) {
        Objects.requireNonNull(ref, "AnchorRef is required");
        if (ref instanceof AnchorRef.WorldAnchorRef w) {
            SceneAnchor a = anchors.get(w.anchorId());
            return a != null ? Optional.of(a.worldPosition()) : Optional.empty();
        } else if (ref instanceof AnchorRef.BodyAttachmentRef b) {
            SceneBody body = bodies.get(b.bodyId());
            if (body == null) return Optional.empty();
            AttachmentPoint pt = body.attachmentPoints().get(b.pointId());
            if (pt == null) return Optional.empty();

            Transform2D pose = new Transform2D(body.initialPose().position(), body.initialPose().angle());
            return Optional.of(pose.toWorld(pt.localPosition()));
        }
        return Optional.empty();
    }

    /**
     * Validates all components and topological relationships of this scene.
     */
    public SceneValidationResult validate() {
        List<SceneDiagnostic> diagnostics = new ArrayList<>();

        // 1. Verify bodies
        for (SceneBody b : bodies.values()) {
            if (b.shape() == null) {
                diagnostics.add(SceneDiagnostic.error(
                        SceneDiagnostic.DiagnosticCode.DEGENERATE_GEOMETRY,
                        b.id().toString(),
                        "Body " + b.label() + " has null shape"));
            }
            if (b.spec() == null) {
                diagnostics.add(SceneDiagnostic.error(
                        SceneDiagnostic.DiagnosticCode.INVALID_PARAMETER,
                        b.id().toString(),
                        "Body " + b.label() + " has null spec"));
            }
        }

        // 2. Verify relationships
        for (StructuralRelationship rel : relationships) {
            rel.collectValidationErrors(this, diagnostics);
        }

        return SceneValidationResult.of(diagnostics);
    }

    /**
     * Instantiates a ready-to-run {@link SimulationWorld} initialized strictly from this scene blueprint.
     *
     * <p>Invariant: If this scene is invalid, an {@link IllegalStateException} is thrown and NO
     * partially constructed simulation world is ever returned.
     */
    public SimulationWorld buildSimulationWorld() {
        SceneValidationResult validation = validate();
        if (!validation.isValid()) {
            throw new IllegalStateException("Cannot build SimulationWorld from invalid LaboratoryScene: "
                    + String.join("; ", validation.errorMessages()));
        }

        SimulationWorld world = new SimulationWorld(
                SimulationWorld.DEFAULT_FIXED_TIMESTEP,
                new UniformGravity(gravity));

        for (SceneBody b : bodies.values()) {
            RigidBodyState state = new RigidBodyState(
                    b.initialPose().position(),
                    b.initialPose().velocity(),
                    b.initialPose().angle(),
                    b.initialPose().angularVelocity());
            RigidBody body = new RigidBody(b.spec(), b.shape(), state);
            world.addBody(body);
        }

        return world;
    }

    /**
     * Builder for constructing {@link LaboratoryScene} blueprints with fail-fast duplicate identity detection.
     */
    public static final class Builder {
        private final String sceneId;
        private final String title;
        private Vector2D gravity = new Vector2D(0.0, -9.81);

        private final Map<UUID, SceneBody> bodies = new LinkedHashMap<>();
        private final Map<String, SceneAnchor> anchors = new LinkedHashMap<>();
        private final Map<String, StaticSurface> surfaces = new LinkedHashMap<>();
        private final Map<String, StructuralRelationship> relationships = new LinkedHashMap<>();

        public Builder(String sceneId, String title) {
            this.sceneId = Objects.requireNonNull(sceneId, "sceneId is required");
            this.title = Objects.requireNonNull(title, "title is required");
        }

        public Builder gravity(Vector2D gravity) {
            this.gravity = Objects.requireNonNull(gravity, "gravity is required");
            return this;
        }

        public Builder addBody(SceneBody body) {
            Objects.requireNonNull(body, "body is required");
            if (bodies.containsKey(body.id())) {
                throw new IllegalArgumentException("Duplicate body UUID detected: " + body.id());
            }
            bodies.put(body.id(), body);
            return this;
        }

        public Builder addAnchor(SceneAnchor anchor) {
            Objects.requireNonNull(anchor, "anchor is required");
            if (anchors.containsKey(anchor.anchorId())) {
                throw new IllegalArgumentException("Duplicate anchor ID detected: " + anchor.anchorId());
            }
            anchors.put(anchor.anchorId(), anchor);
            return this;
        }

        public Builder addSurface(StaticSurface surface) {
            Objects.requireNonNull(surface, "surface is required");
            if (surfaces.containsKey(surface.surfaceId())) {
                throw new IllegalArgumentException("Duplicate surface ID detected: " + surface.surfaceId());
            }
            surfaces.put(surface.surfaceId(), surface);
            return this;
        }

        public Builder addRelationship(StructuralRelationship relationship) {
            Objects.requireNonNull(relationship, "relationship is required");
            if (relationships.containsKey(relationship.relationshipId())) {
                throw new IllegalArgumentException("Duplicate relationship ID detected: " + relationship.relationshipId());
            }
            relationships.put(relationship.relationshipId(), relationship);
            return this;
        }

        public LaboratoryScene build() {
            return new LaboratoryScene(
                    sceneId,
                    title,
                    gravity,
                    bodies.values(),
                    anchors.values(),
                    surfaces.values(),
                    relationships.values());
        }
    }
}

