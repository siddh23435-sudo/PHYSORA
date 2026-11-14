package edu.physics.simulation.scene;

import java.util.Objects;
import java.util.UUID;

/**
 * Polymorphic reference to an anchor point in a laboratory scene: either a local attachment
 * point on a physical body, or a world-fixed stationary anchor.
 */
public sealed interface AnchorRef permits AnchorRef.BodyAttachmentRef, AnchorRef.WorldAnchorRef {

    record BodyAttachmentRef(UUID bodyId, String pointId) implements AnchorRef {
        public BodyAttachmentRef {
            Objects.requireNonNull(bodyId, "bodyId is required");
            Objects.requireNonNull(pointId, "pointId is required");
            if (pointId.isBlank()) {
                throw new IllegalArgumentException("pointId cannot be blank");
            }
        }
    }

    record WorldAnchorRef(String anchorId) implements AnchorRef {
        public WorldAnchorRef {
            Objects.requireNonNull(anchorId, "anchorId is required");
            if (anchorId.isBlank()) {
                throw new IllegalArgumentException("anchorId cannot be blank");
            }
        }
    }

    static AnchorRef body(UUID bodyId, String pointId) {
        return new BodyAttachmentRef(bodyId, pointId);
    }

    static AnchorRef anchor(String anchorId) {
        return new WorldAnchorRef(anchorId);
    }
}

