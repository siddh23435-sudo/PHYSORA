package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Untrusted, typed interpretation returned by an AI provider; it is not yet a validated canonical problem.
 *
 * <p>Preserves extracted physical constraints, surface geometry cues, and source provenance so downstream
 * deterministic validation can verify completeness against the original question and clarifications.
 */
public record InterpretationDraft(
        String normalizedQuestion,
        List<ProblemPart> parts,
        List<String> assumptions,
        List<Clarification> clarifications,
        double confidence,
        SurfaceSpec surface,
        CanonicalPhysicsProblem.AngleReference angleReference,
        List<CanonicalPhysicsProblem.CanonicalTarget> canonicalTargets,
        List<ExtractedConstraint> extractedConstraints,
        List<String> unsupportedConstraints,
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens) {

    public InterpretationDraft {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            throw new IllegalArgumentException("Normalized question is required");
        }
        parts = parts == null ? List.of() : List.copyOf(parts);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
        if (angleReference == null) {
            angleReference = CanonicalPhysicsProblem.AngleReference.HORIZONTAL;
        }
        canonicalTargets = canonicalTargets == null ? List.of() : List.copyOf(canonicalTargets);
        extractedConstraints = extractedConstraints == null ? List.of() : List.copyOf(extractedConstraints);
        unsupportedConstraints = unsupportedConstraints == null ? List.of() : List.copyOf(unsupportedConstraints);
        sharedGivens = sharedGivens == null ? Map.of() : Map.copyOf(sharedGivens);
    }

    /** 10-argument constructor preserving previous record signature with empty shared givens. */
    public InterpretationDraft(
            String normalizedQuestion,
            List<ProblemPart> parts,
            List<String> assumptions,
            List<Clarification> clarifications,
            double confidence,
            SurfaceSpec surface,
            CanonicalPhysicsProblem.AngleReference angleReference,
            List<CanonicalPhysicsProblem.CanonicalTarget> canonicalTargets,
            List<ExtractedConstraint> extractedConstraints,
            List<String> unsupportedConstraints) {
        this(
                normalizedQuestion,
                parts,
                assumptions,
                clarifications,
                confidence,
                surface,
                angleReference,
                canonicalTargets,
                extractedConstraints,
                unsupportedConstraints,
                Map.of());
    }

    /** Preserves the existing five-argument interpretation constructor with safe canonical metadata defaults. */
    public InterpretationDraft(
            String normalizedQuestion,
            List<ProblemPart> parts,
            List<String> assumptions,
            List<Clarification> clarifications,
            double confidence) {
        this(
                normalizedQuestion,
                parts,
                assumptions,
                clarifications,
                confidence,
                null,
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of(),
                Map.of());
    }

    /** Returns an immutable copy enriched with structured surface, target, constraint, and unsupported metadata. */
    public InterpretationDraft withCanonicalMetadata(
            SurfaceSpec surface,
            CanonicalPhysicsProblem.AngleReference angleReference,
            List<CanonicalPhysicsProblem.CanonicalTarget> canonicalTargets,
            List<ExtractedConstraint> extractedConstraints,
            List<String> unsupportedConstraints) {
        return new InterpretationDraft(
                normalizedQuestion,
                parts,
                assumptions,
                clarifications,
                confidence,
                surface,
                angleReference,
                canonicalTargets,
                extractedConstraints,
                unsupportedConstraints,
                sharedGivens);
    }

    /** Returns an immutable copy with explicit global shared givens. */
    public InterpretationDraft withSharedGivens(Map<ProjectileProblem.Input, PhysicalQuantity> givens) {
        return new InterpretationDraft(
                normalizedQuestion,
                parts,
                assumptions,
                clarifications,
                confidence,
                surface,
                angleReference,
                canonicalTargets,
                extractedConstraints,
                unsupportedConstraints,
                givens);
    }

    /** Untrusted surface geometry cue extracted prior to domain validation. */
    public sealed interface SurfaceSpec
            permits SurfaceSpec.Horizontal, SurfaceSpec.Inclined, SurfaceSpec.Unresolved {

        record Horizontal(Double landingY) implements SurfaceSpec {}

        record Inclined(Double anchorX, Double anchorY, Double angleDegrees) implements SurfaceSpec {}

        record Unresolved(String reason, String sourceEvidence) implements SurfaceSpec {
            public Unresolved {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("Unresolved surface reason is required");
                }
            }
        }
    }

    public enum ConstraintOrigin {
        STUDENT_EXPLICIT,
        CLARIFICATION_ANSWER,
        INTERPRETER_INFERRED,
        PHYSORA_DEFAULT
    }

    public enum ConstraintResolution {
        ASSERTED,
        UNRESOLVED_OR_AMBIGUOUS
    }

    /** Provenance-tagged physical constraint or boundary condition extracted from the prompt/clarifications. */
    public record ExtractedConstraint(
            String identifier,
            ConstraintOrigin origin,
            ConstraintResolution resolution,
            String sourceEvidence) {
        public ExtractedConstraint {
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("Constraint identifier is required");
            }
            Objects.requireNonNull(origin, "Constraint origin is required");
            Objects.requireNonNull(resolution, "Constraint resolution is required");
        }
    }

    public record ProblemPart(ProjectileProblem.Target target,
                              Map<ProjectileProblem.Input, PhysicalQuantity> givens,
                              Map<ProjectileProblem.Input, ProjectileProblemSequence.ResultReference> references) {
        public ProblemPart {
            if (target == null || givens == null || references == null) throw new IllegalArgumentException("Target, givens, and references are required");
            EnumMap<ProjectileProblem.Input, PhysicalQuantity> copy = new EnumMap<>(ProjectileProblem.Input.class);
            copy.putAll(givens);
            if (copy.containsValue(null)) throw new IllegalArgumentException("Given quantities cannot be null");
            givens = Collections.unmodifiableMap(copy);
            EnumMap<ProjectileProblem.Input, ProjectileProblemSequence.ResultReference> referenceCopy =
                    new EnumMap<>(ProjectileProblem.Input.class);
            referenceCopy.putAll(references);
            if (referenceCopy.containsValue(null)) throw new IllegalArgumentException("References cannot be null");
            if (copy.keySet().stream().anyMatch(referenceCopy::containsKey)) {
                throw new IllegalArgumentException("An input cannot be both given and referenced");
            }
            references = Collections.unmodifiableMap(referenceCopy);
        }

        public ProblemPart(ProjectileProblem.Target target, Map<ProjectileProblem.Input, PhysicalQuantity> givens) {
            this(target, givens, Map.of());
        }
    }

    public record Clarification(String prompt, String reason, List<String> options) {
        public Clarification {
            if (prompt == null || prompt.isBlank() || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Clarification prompt and reason are required");
            }
            options = List.copyOf(options);
        }
    }
}
