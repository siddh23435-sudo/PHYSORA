package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.problem.ProjectileProblemSolver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Validates untrusted interpreted structure before it is handed to the deterministic solver. */
public final class ProblemValidator {
    private final ProblemNormalizer normalizer;
    private final ProblemClassifier classifier;

    public ProblemValidator() {
        this(new ProblemNormalizer(), new ProblemClassifier());
    }

    public ProblemValidator(ProblemNormalizer normalizer) {
        this(normalizer, new ProblemClassifier());
    }

    public ProblemValidator(ProblemNormalizer normalizer, ProblemClassifier classifier) {
        if (normalizer == null) throw new IllegalArgumentException("Problem normalizer is required");
        if (classifier == null) throw new IllegalArgumentException("Problem classifier is required");
        this.normalizer = normalizer;
        this.classifier = classifier;
    }

    public ProblemValidationResult validate(String originalQuestion, InterpretationDraft draft) {
        if (originalQuestion == null || originalQuestion.isBlank() || draft == null) {
            return invalid("Original question and interpretation are required");
        }
        if (!Double.isFinite(draft.confidence()) || draft.confidence() < 0.0 || draft.confidence() > 1.0) {
            return invalid("Interpretation confidence must be between 0 and 1");
        }
        if (!draft.clarifications().isEmpty()) {
            return new ProblemValidationResult(ProblemValidationResult.Status.NEEDS_CLARIFICATION,
                    null, draft.clarifications(), List.of(), draft.assumptions());
        }
        if (draft.parts().isEmpty()) return invalid("At least one supported problem part is required");

        List<InterpretationDraft.Clarification> questions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ProjectileProblemSequence.Part> parts = new ArrayList<>();
        for (int index = 0; index < draft.parts().size(); index++) {
            InterpretationDraft.ProblemPart part = draft.parts().get(index);
            ProjectileProblem.Target classifiedTarget;
            try {
                classifiedTarget = classifier.classify(part.target()).target();
            } catch (IllegalArgumentException ex) {
                errors.add("part-" + (index + 1) + ": unsupported problem target");
                continue;
            }
            for (ProjectileProblemSequence.ResultReference reference : part.references().values()) {
                if (reference.partIndex() >= index) {
                    errors.add("part-" + (index + 1) + ": references must point to an earlier part");
                }
            }
            ProblemNormalizer.NormalizedPart normalized;
            try {
                normalized = normalizer.normalize(classifiedTarget, part.givens());
            } catch (IllegalArgumentException ex) {
                errors.add("part-" + (index + 1) + ": " + ex.getMessage());
                continue;
            }

            EnumMap<ProjectileProblem.Input, PhysicalQuantity> probeGivens = new EnumMap<>(ProjectileProblem.Input.class);
            probeGivens.putAll(normalized.normalizedGivens());
            part.references().keySet().forEach(input -> probeGivens.put(input,
                    new PhysicalQuantity(0.0, ProblemNormalizer.canonicalUnit(input))));
            ProjectileProblem probe = new ProjectileProblem(originalQuestion, classifiedTarget, probeGivens);
            List<ProjectileProblem.Input> missing = ProjectileProblemSolver.missingInputsFor(probe);
            for (ProjectileProblem.Input input : missing) {
                questions.add(new InterpretationDraft.Clarification(
                        "What is the " + humanize(input) + "?",
                        "It is required to solve part " + (index + 1) + ".",
                        List.of()));
            }
            parts.add(new ProjectileProblemSequence.Part(classifiedTarget, normalized.normalizedGivens(), part.references()));
        }

        if (!errors.isEmpty()) {
            return new ProblemValidationResult(ProblemValidationResult.Status.INVALID, null, List.of(), errors, draft.assumptions());
        }
        if (!questions.isEmpty()) {
            return new ProblemValidationResult(ProblemValidationResult.Status.NEEDS_CLARIFICATION, null,
                    questions, List.of(), draft.assumptions());
        }
        ProjectileProblemSequence sequence;
        try {
            sequence = new ProjectileProblemSequence(originalQuestion, Map.of(), parts);
        } catch (IllegalArgumentException ex) {
            return invalid(ex.getMessage());
        }
        return new ProblemValidationResult(ProblemValidationResult.Status.READY, sequence,
                List.of(), List.of(), draft.assumptions());
    }

    private static ProblemValidationResult invalid(String error) {
        return new ProblemValidationResult(ProblemValidationResult.Status.INVALID, null, List.of(), List.of(error), List.of());
    }

    private static String humanize(ProjectileProblem.Input input) {
        return input.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
