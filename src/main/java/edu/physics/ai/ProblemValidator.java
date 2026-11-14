package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.projectile.LandingSurface;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates interpreted physical structure by translating into a canonical physics problem
 * and delegating authoritative validation to the {@link ConstraintCompletenessValidator}.
 */
public final class ProblemValidator {
    private static final double TOLERANCE = 1e-9;
    private static final Set<ProjectileProblem.Input> CANDIDATE_LAUNCH_INPUTS = Set.of(
            ProjectileProblem.Input.INITIAL_X,
            ProjectileProblem.Input.INITIAL_Y,
            ProjectileProblem.Input.LANDING_Y,
            ProjectileProblem.Input.GRAVITY,
            ProjectileProblem.Input.INITIAL_SPEED,
            ProjectileProblem.Input.LAUNCH_ANGLE
    );

    private final ConstraintCompletenessValidator completenessValidator;
    private final ProblemClassifier classifier;

    public ProblemValidator() {
        this(new ConstraintCompletenessValidator(), new ProblemClassifier());
    }

    public ProblemValidator(ConstraintCompletenessValidator completenessValidator) {
        this(completenessValidator, new ProblemClassifier());
    }

    public ProblemValidator(ProblemNormalizer normalizer) {
        this(new ConstraintCompletenessValidator(), new ProblemClassifier());
    }

    public ProblemValidator(ProblemNormalizer normalizer, ProblemClassifier classifier) {
        this(new ConstraintCompletenessValidator(), classifier != null ? classifier : new ProblemClassifier());
    }

    public ProblemValidator(ConstraintCompletenessValidator completenessValidator, ProblemClassifier classifier) {
        if (completenessValidator == null) throw new IllegalArgumentException("Completeness validator is required");
        this.completenessValidator = completenessValidator;
        this.classifier = classifier != null ? classifier : new ProblemClassifier();
    }

    public ProblemValidationResult validate(String originalQuestion, InterpretationDraft draft) {
        if (originalQuestion == null || originalQuestion.isBlank()) {
            return invalid("Original question and interpretation are required");
        }
        return validate(new InterpretationRequest(originalQuestion), draft);
    }

    public ProblemValidationResult validate(InterpretationRequest request, InterpretationDraft draft) {
        if (request == null || request.question() == null || request.question().isBlank() || draft == null) {
            return invalid("Original question and interpretation are required");
        }
        if (!Double.isFinite(draft.confidence()) || draft.confidence() < 0.0 || draft.confidence() > 1.0) {
            return new ProblemValidationResult(
                    ProblemValidationResult.Status.INVALID,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of("Interpretation confidence must be between 0 and 1"),
                    draft.assumptions());
        }
        if (!draft.clarifications().isEmpty()) {
            return new ProblemValidationResult(
                    ProblemValidationResult.Status.NEEDS_CLARIFICATION,
                    null,
                    null,
                    null,
                    draft.clarifications(),
                    List.of(),
                    draft.assumptions());
        }
        if (draft.parts().isEmpty() && draft.canonicalTargets().isEmpty()) {
            return invalid("At least one supported problem part is required");
        }

        CanonicalPhysicsProblem canonical;
        try {
            canonical = buildCanonicalProblem(request, draft);
        } catch (IllegalArgumentException ex) {
            return new ProblemValidationResult(
                    ProblemValidationResult.Status.INVALID,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(ex.getMessage()),
                    draft.assumptions());
        }

        ConstraintCompletenessValidator.CompletenessReport report =
                completenessValidator.validate(request, draft, canonical);

        ProblemValidationResult.Status status = switch (report.outcome()) {
            case READY_FOR_SOLVING -> ProblemValidationResult.Status.READY;
            case INSUFFICIENT_INFORMATION -> ProblemValidationResult.Status.NEEDS_CLARIFICATION;
            case UNSUPPORTED -> ProblemValidationResult.Status.UNSUPPORTED;
            case INVALID_INPUT -> ProblemValidationResult.Status.INVALID;
        };

        List<InterpretationDraft.Clarification> clarifications = new ArrayList<>(draft.clarifications());
        if (status == ProblemValidationResult.Status.NEEDS_CLARIFICATION && clarifications.isEmpty()) {
            for (String missing : report.missingInputs()) {
                clarifications.add(new InterpretationDraft.Clarification(
                        "What is the " + humanize(missing) + "?",
                        "It is required to evaluate the projectile trajectory.",
                        List.of()));
            }
            for (String unresolved : report.unresolvedConditions()) {
                clarifications.add(new InterpretationDraft.Clarification(
                        "Could you clarify: " + unresolved + "?",
                        "This physical condition is unresolved or ambiguous in the problem statement.",
                        List.of()));
            }
        }

        List<String> errors = new ArrayList<>();
        errors.addAll(report.invalidDiagnostics());
        errors.addAll(report.unsupportedDiagnostics());

        List<String> assumptions = new ArrayList<>();
        assumptions.addAll(report.modelAssumptions());
        assumptions.addAll(report.introducedDefaults());
        for (String a : draft.assumptions()) {
            if (!assumptions.contains(a)) {
                assumptions.add(a);
            }
        }

        ProjectileProblemSequence normalizedProblem = null;
        if (status == ProblemValidationResult.Status.READY) {
            normalizedProblem = synthesizeLegacySequence(canonical);
        }

        return new ProblemValidationResult(
                status,
                canonical,
                report,
                normalizedProblem,
                clarifications,
                errors,
                assumptions);
    }

    private CanonicalPhysicsProblem buildCanonicalProblem(InterpretationRequest request, InterpretationDraft draft) {
        CanonicalPhysicsProblem.Builder builder = CanonicalPhysicsProblem.builder(request.question());

        Set<ProjectileProblem.Input> referencedInputs = new java.util.HashSet<>();
        for (InterpretationDraft.ProblemPart part : draft.parts()) {
            referencedInputs.addAll(part.references().keySet());
        }

        Map<ProjectileProblem.Input, PhysicalQuantity> promotableInputs = new EnumMap<>(ProjectileProblem.Input.class);

        // 1. Explicit shared givens establish problem-level scope (if not referenced)
        draft.sharedGivens().forEach((input, qty) -> {
            if (CANDIDATE_LAUNCH_INPUTS.contains(input) && !referencedInputs.contains(input)) {
                promotableInputs.put(input, qty);
            }
        });

        // 2. Universal specification across every relevant part
        int numParts = draft.parts().size();
        for (ProjectileProblem.Input candidate : CANDIDATE_LAUNCH_INPUTS) {
            if (promotableInputs.containsKey(candidate) || referencedInputs.contains(candidate)) {
                continue;
            }
            if (numParts > 0) {
                boolean allPartsHave = true;
                PhysicalQuantity first = null;
                boolean allAgree = true;
                for (InterpretationDraft.ProblemPart part : draft.parts()) {
                    PhysicalQuantity q = part.givens().get(candidate);
                    if (q == null) {
                        allPartsHave = false;
                        break;
                    }
                    if (first == null) {
                        first = q;
                    } else if (first.unit().dimension() != q.unit().dimension()
                            || !nearlyEqual(first.valueInSi(), q.valueInSi())) {
                        allAgree = false;
                        break;
                    }
                }
                if (allPartsHave && allAgree) {
                    promotableInputs.put(candidate, first);
                }
            }
        }

        // 3. Problem-level velocity: either or both speed and angle can be set at problem level
        boolean hasPromotableSpeed = promotableInputs.containsKey(ProjectileProblem.Input.INITIAL_SPEED);
        boolean hasPromotableAngle = promotableInputs.containsKey(ProjectileProblem.Input.LAUNCH_ANGLE);
        boolean promotePolarVelocity = hasPromotableSpeed || hasPromotableAngle;

        // 4. Apply promoted inputs to problem-level builder
        if (promotableInputs.containsKey(ProjectileProblem.Input.INITIAL_X)) {
            builder.initialX(promotableInputs.get(ProjectileProblem.Input.INITIAL_X));
        }
        if (promotableInputs.containsKey(ProjectileProblem.Input.INITIAL_Y)) {
            builder.initialY(promotableInputs.get(ProjectileProblem.Input.INITIAL_Y));
        }

        // Surface handling
        if (draft.surface() instanceof InterpretationDraft.SurfaceSpec.Inclined inc) {
            double ax = inc.anchorX() != null ? inc.anchorX() : 0.0;
            double ay = inc.anchorY() != null ? inc.anchorY() : 0.0;
            double angle = inc.angleDegrees() != null ? inc.angleDegrees() : 0.0;
            builder.landingSurface(LandingSurface.incline(ax, ay, angle));
        } else if (draft.surface() instanceof InterpretationDraft.SurfaceSpec.Horizontal horiz) {
            if (horiz.landingY() != null) {
                builder.horizontalLandingY(new PhysicalQuantity(horiz.landingY(), PhysicalUnit.METRE));
            } else {
                builder.horizontalLandingY(new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            }
        } else if (promotableInputs.containsKey(ProjectileProblem.Input.LANDING_Y)) {
            builder.horizontalLandingY(promotableInputs.get(ProjectileProblem.Input.LANDING_Y));
        } else if (draft.surface() == null) {
            builder.horizontalLandingY(new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            builder.addAssumption("landing-on-horizontal-ground: y=0 m (PHYSORA_DEFAULT)");
        }

        // Gravity handling
        if (promotableInputs.containsKey(ProjectileProblem.Input.GRAVITY)) {
            builder.gravity(promotableInputs.get(ProjectileProblem.Input.GRAVITY));
        } else {
            boolean partHasGravity = draft.parts().stream().anyMatch(p ->
                    p.givens().containsKey(ProjectileProblem.Input.GRAVITY)
                    || p.references().containsKey(ProjectileProblem.Input.GRAVITY));
            if (!partHasGravity && !mentionsAlternativeGravity(request.question())) {
                builder.gravity(new PhysicalQuantity(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED));
                builder.addAssumption("constant-downward-gravity: 9.8 m/s^2 (PHYSORA_DEFAULT)");
            }
        }

        // Velocity handling
        if (promotePolarVelocity) {
            builder.polarVelocity(
                    promotableInputs.get(ProjectileProblem.Input.INITIAL_SPEED),
                    promotableInputs.get(ProjectileProblem.Input.LAUNCH_ANGLE),
                    draft.angleReference());
        }

        // 5. Add parts
        for (int i = 0; i < numParts; i++) {
            InterpretationDraft.ProblemPart part = draft.parts().get(i);
            CanonicalPhysicsProblem.CanonicalTarget target = CanonicalPhysicsProblem.target(part.target());
            EnumMap<ProjectileProblem.Input, PhysicalQuantity> partParams =
                    new EnumMap<>(ProjectileProblem.Input.class);
            part.givens().forEach((input, qty) -> {
                if (!promotableInputs.containsKey(input)) {
                    partParams.put(input, qty);
                }
            });
            builder.addTarget(target, partParams, part.references());
        }

        int partsCovered = 0;
        for (CanonicalPhysicsProblem.CanonicalTarget target : draft.canonicalTargets()) {
            boolean coveredByPart = false;
            for (int i = partsCovered; i < numParts; i++) {
                if (CanonicalPhysicsProblem.target(draft.parts().get(i).target()).equals(target)) {
                    coveredByPart = true;
                    partsCovered = i + 1;
                    break;
                }
            }
            if (!coveredByPart) {
                builder.addTarget(target);
            }
        }

        // Forward assumptions and unsupported constraints
        for (String assumption : draft.assumptions()) {
            builder.addAssumption(assumption);
        }
        for (String unsupported : draft.unsupportedConstraints()) {
            builder.addUnsupportedConstraint(unsupported);
        }

        return builder.build();
    }

    private static ProjectileProblemSequence synthesizeLegacySequence(CanonicalPhysicsProblem canonical) {
        if (canonical.landingSurface() != null && !canonical.landingSurface().isHorizontal()) {
            return null;
        }
        for (CanonicalPhysicsProblem.PartSpec part : canonical.parts()) {
            if (part.target() instanceof CanonicalPhysicsProblem.SurfaceTarget) {
                return null;
            }
        }
        EnumMap<ProjectileProblem.Input, PhysicalQuantity> sharedGivens =
                new EnumMap<>(ProjectileProblem.Input.class);
        if (canonical.derivedInitialSpeed().isPresent()) {
            sharedGivens.put(ProjectileProblem.Input.INITIAL_SPEED,
                    new PhysicalQuantity(canonical.derivedInitialSpeed().getAsDouble(), PhysicalUnit.METRE_PER_SECOND));
        }
        if (canonical.derivedLaunchAngleDegrees().isPresent()) {
            sharedGivens.put(ProjectileProblem.Input.LAUNCH_ANGLE,
                    new PhysicalQuantity(canonical.derivedLaunchAngleDegrees().getAsDouble(), PhysicalUnit.DEGREE));
        }
        if (canonical.gravity().isPresent()) {
            sharedGivens.put(ProjectileProblem.Input.GRAVITY,
                    new PhysicalQuantity(canonical.gravity().getAsDouble(), PhysicalUnit.METRE_PER_SECOND_SQUARED));
        }
        sharedGivens.put(ProjectileProblem.Input.INITIAL_X,
                new PhysicalQuantity(canonical.initialX(), PhysicalUnit.METRE));
        sharedGivens.put(ProjectileProblem.Input.INITIAL_Y,
                new PhysicalQuantity(canonical.initialY(), PhysicalUnit.METRE));
        if (canonical.landingSurface().isHorizontal()) {
            sharedGivens.put(ProjectileProblem.Input.LANDING_Y,
                    new PhysicalQuantity(canonical.landingSurface().anchorY(), PhysicalUnit.METRE));
        }

        List<ProjectileProblemSequence.Part> parts = new ArrayList<>();
        for (CanonicalPhysicsProblem.PartSpec part : canonical.parts()) {
            ProjectileProblem.Target target = part.target() instanceof CanonicalPhysicsProblem.StandardTarget st
                    ? st.target() : ProjectileProblem.Target.HORIZONTAL_RANGE;
            EnumMap<ProjectileProblem.Input, PhysicalQuantity> partGivens =
                    new EnumMap<>(ProjectileProblem.Input.class);
            partGivens.putAll(sharedGivens);
            partGivens.putAll(part.parameters());
            parts.add(new ProjectileProblemSequence.Part(target, partGivens, part.references()));
        }
        return new ProjectileProblemSequence(canonical.statement(), sharedGivens, parts);
    }

    private static boolean mentionsAlternativeGravity(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("moon") || lower.contains("mars") || lower.contains("planet")
                || lower.contains("gravity") || lower.contains("gravitational acceleration")
                || lower.contains("g =") || lower.contains("g=");
    }

    private static boolean nearlyEqual(double a, double b) {
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= TOLERANCE * scale;
    }

    private static ProblemValidationResult invalid(String error) {
        return new ProblemValidationResult(
                ProblemValidationResult.Status.INVALID,
                null,
                null,
                null,
                List.of(),
                List.of(error),
                List.of());
    }

    private static String humanize(String inputName) {
        if (inputName == null) return "unknown parameter";
        return inputName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
