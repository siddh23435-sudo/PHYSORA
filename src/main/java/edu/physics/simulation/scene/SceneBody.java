package edu.physics.simulation.scene;

import edu.physics.simulation.PhysicalBodySpec;
import edu.physics.simulation.geometry.GeometricShape;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable participant entity in a laboratory scene blueprint.
 *
 * <p>Authoritatively owns its physical specification, geometric shape, initial pose, and
 * local attachment points. Contains no mutable runtime variables.
 */
public record SceneBody(
        UUID id,
        String label,
        PhysicalBodySpec spec,
        GeometricShape shape,
        InitialPose initialPose,
        Map<String, AttachmentPoint> attachmentPoints) {

    public SceneBody {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(label, "label is required");
        if (label.isBlank()) throw new IllegalArgumentException("label cannot be blank");
        Objects.requireNonNull(spec, "spec is required");
        Objects.requireNonNull(shape, "shape is required");
        Objects.requireNonNull(initialPose, "initialPose is required");
        attachmentPoints = attachmentPoints != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attachmentPoints)) : Map.of();
    }

    public static SceneBody of(
            UUID id,
            String label,
            PhysicalBodySpec spec,
            GeometricShape shape,
            InitialPose initialPose,
            List<AttachmentPoint> attachments) {

        Map<String, AttachmentPoint> map = new LinkedHashMap<>();
        if (attachments != null) {
            for (AttachmentPoint p : attachments) {
                if (map.containsKey(p.pointId())) {
                    throw new IllegalArgumentException("Duplicate attachment point ID: " + p.pointId());
                }
                map.put(p.pointId(), p);
            }
        }
        return new SceneBody(id, label, spec, shape, initialPose, map);
    }

    public static SceneBody of(
            UUID id,
            String label,
            PhysicalBodySpec spec,
            GeometricShape shape,
            InitialPose initialPose) {
        return of(id, label, spec, shape, initialPose, List.of());
    }

    public Optional<AttachmentPoint> findAttachmentPoint(String pointId) {
        return Optional.ofNullable(attachmentPoints.get(pointId));
    }
}

