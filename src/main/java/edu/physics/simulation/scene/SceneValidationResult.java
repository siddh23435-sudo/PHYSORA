package edu.physics.simulation.scene;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of validating a {@link LaboratoryScene}, aggregating structured diagnostics.
 */
public record SceneValidationResult(
        boolean isValid,
        List<SceneDiagnostic> diagnostics) {

    public SceneValidationResult {
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        diagnostics = Collections.unmodifiableList(diagnostics);
    }

    public static SceneValidationResult valid() {
        return new SceneValidationResult(true, List.of());
    }

    public static SceneValidationResult of(List<SceneDiagnostic> diagnostics) {
        boolean valid = diagnostics.stream().noneMatch(SceneDiagnostic::isError);
        return new SceneValidationResult(valid, diagnostics);
    }

    public List<SceneDiagnostic> errors() {
        return diagnostics.stream().filter(SceneDiagnostic::isError).toList();
    }

    public List<SceneDiagnostic> warnings() {
        return diagnostics.stream().filter(d -> d.severity() == SceneDiagnostic.DiagnosticSeverity.WARNING).toList();
    }

    public List<String> errorMessages() {
        return errors().stream().map(SceneDiagnostic::message).toList();
    }
}

