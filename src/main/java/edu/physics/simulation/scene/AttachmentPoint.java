package edu.physics.simulation.scene;

import edu.physics.simulation.Vector2D;
import java.util.Objects;

/**
 * Immutable fiducial coordinate point defined on a physical body in local body coordinates.
 *
 * <p>Authoritatively owned by {@link SceneBody}. Used to define anchor points for cables,
 * springs, pivot pins, and load application.
 */
public record AttachmentPoint(
        String pointId,
        String label,
        Vector2D localPosition) {

    public AttachmentPoint {
        Objects.requireNonNull(pointId, "pointId is required");
        if (pointId.isBlank()) {
            throw new IllegalArgumentException("pointId cannot be blank");
        }
        Objects.requireNonNull(label, "label is required");
        Objects.requireNonNull(localPosition, "localPosition is required");
    }

    public static AttachmentPoint of(String pointId, String label, Vector2D localPosition) {
        return new AttachmentPoint(pointId, label, localPosition);
    }

    public static AttachmentPoint of(String pointId, Vector2D localPosition) {
        return new AttachmentPoint(pointId, pointId, localPosition);
    }
}

