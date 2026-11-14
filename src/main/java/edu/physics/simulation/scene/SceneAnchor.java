package edu.physics.simulation.scene;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable world-fixed fiducial anchor in a laboratory scene (e.g. wall bracket,
 * ceiling mount, or fixed pulley axle pin).
 *
 * <p>Represents a stationary point in laboratory world coordinates without requiring
 * an artificial zero-mass dynamic rigid body.
 */
public record SceneAnchor(
        String anchorId,
        String label,
        Vector2D worldPosition) {

    public SceneAnchor {
        Objects.requireNonNull(anchorId, "anchorId is required");
        if (anchorId.isBlank()) {
            throw new IllegalArgumentException("anchorId cannot be blank");
        }
        Objects.requireNonNull(label, "label is required");
        Objects.requireNonNull(worldPosition, "worldPosition is required");
    }

    public static SceneAnchor of(String anchorId, String label, Vector2D worldPosition) {
        return new SceneAnchor(anchorId, label, worldPosition);
    }

    public static SceneAnchor of(String anchorId, Vector2D worldPosition) {
        return new SceneAnchor(anchorId, anchorId, worldPosition);
    }
}

