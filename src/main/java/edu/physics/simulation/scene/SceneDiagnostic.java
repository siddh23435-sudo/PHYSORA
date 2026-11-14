package edu.physics.simulation.scene;

import java.util.Objects;

/**
 * Immutable structured diagnostic representing a physical or topological validation issue
 * in a laboratory scene.
 */
public record SceneDiagnostic(
        DiagnosticCode code,
        DiagnosticSeverity severity,
        String targetId,
        String message) {

    public enum DiagnosticSeverity {
        ERROR,
        WARNING,
        INFO
    }

    public enum DiagnosticCode {
        DUPLICATE_ID,
        MISSING_BODY,
        MISSING_ANCHOR,
        MISSING_ATTACHMENT,
        MISSING_SURFACE,
        INVALID_CABLE_LENGTH,
        INVALID_PARAMETER,
        DEGENERATE_GEOMETRY,
        INCONSISTENT_TOPOLOGY
    }

    public SceneDiagnostic {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(targetId, "targetId is required");
        Objects.requireNonNull(message, "message is required");
    }

    public static SceneDiagnostic error(DiagnosticCode code, String targetId, String message) {
        return new SceneDiagnostic(code, DiagnosticSeverity.ERROR, targetId, message);
    }

    public static SceneDiagnostic warning(DiagnosticCode code, String targetId, String message) {
        return new SceneDiagnostic(code, DiagnosticSeverity.WARNING, targetId, message);
    }

    public boolean isError() {
        return severity == DiagnosticSeverity.ERROR;
    }
}

