package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.PhysicsCapabilityRegistry;
import edu.physics.problem.ProjectileProblem;
import edu.physics.projectile.LandingSurface;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic validator verifying that an interpreted physics problem completely and faithfully
 * represents the student's question and clarifications, maps cleanly onto a single
 * {@link CanonicalPhysicsProblem}, and is covered by {@link PhysicsCapabilityRegistry}.
 *
 * <p>Includes a bounded lexical screening layer over the original prompt and clarifications to
 * catch omitted known physical conditions (such as drag, wind, spin, bounce, variable gravity,
 * friction, moving frames, curved terrain, or multi-body collisions). Passing this lexical screen
 * never proves complete semantic comprehension of arbitrary natural language; it acts as a
 * deterministic safety net before invoking {@link PhysicsCapabilityRegistry}.
 */
public final class ConstraintCompletenessValidator {

    public enum Outcome {
        INVALID_INPUT,
        UNSUPPORTED,
        INSUFFICIENT_INFORMATION,
        READY_FOR_SOLVING
    }

    public enum ConstraintCoverageStatus {
        REPRESENTED_AND_SUPPORTED,
        EXPLICITLY_UNSUPPORTED,
        UNRESOLVED_OR_AMBIGUOUS,
        MISSING_FROM_CANONICAL,
        OMITTED_FROM_INTERPRETATION,
        CONTRADICTORY
    }

    /** Ledger entry tracing a condition's origin, extraction status, canonical mapping, and capability decision. */
    public record ConstraintCoverage(
            String identifier,
            InterpretationDraft.ConstraintOrigin origin,
            ConstraintCoverageStatus status,
            boolean extracted,
            String canonicalRepresentation,
            String sourceEvidence,
            String detail) {

        public ConstraintCoverage {
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("Constraint identifier is required");
            }
            Objects.requireNonNull(status, "Constraint coverage status is required");
            if (canonicalRepresentation == null || canonicalRepresentation.isBlank()) {
                canonicalRepresentation = "None";
            }
        }

        public ConstraintCoverage(
                String identifier,
                InterpretationDraft.ConstraintOrigin origin,
                ConstraintCoverageStatus status,
                String sourceEvidence,
                String detail) {
            this(identifier, origin, status, origin != null, "None", sourceEvidence, detail);
        }
    }

    /** Full validation outcome keeping explicit, clarification, inferred, model-assumption, default, and unresolved buckets separate. */
    public record CompletenessReport(
            Outcome outcome,
            InterpretationRequest originalRequest,
            PhysicsCapabilityRegistry.CapabilityEvaluation capabilityEvaluation,
            List<ConstraintCoverage> constraintLedger,
            List<String> explicitStudentConditions,
            List<String> clarificationDerivedConditions,
            List<String> interpreterInferredConditions,
            List<String> modelAssumptions,
            List<String> introducedDefaults,
            List<String> unresolvedConditions,
            List<String> missingInputs,
            List<String> unsupportedDiagnostics,
            List<String> invalidDiagnostics,
            List<String> insufficientDiagnostics) {

        public CompletenessReport {
            Objects.requireNonNull(outcome, "Validation outcome is required");
            constraintLedger = List.copyOf(constraintLedger);
            explicitStudentConditions = List.copyOf(explicitStudentConditions);
            clarificationDerivedConditions = List.copyOf(clarificationDerivedConditions);
            interpreterInferredConditions = List.copyOf(interpreterInferredConditions);
            modelAssumptions = List.copyOf(modelAssumptions);
            introducedDefaults = List.copyOf(introducedDefaults);
            unresolvedConditions = List.copyOf(unresolvedConditions);
            missingInputs = List.copyOf(missingInputs);
            unsupportedDiagnostics = List.copyOf(unsupportedDiagnostics);
            invalidDiagnostics = List.copyOf(invalidDiagnostics);
            insufficientDiagnostics = List.copyOf(insufficientDiagnostics);
        }

        public CompletenessReport(
                Outcome outcome,
                InterpretationRequest originalRequest,
                PhysicsCapabilityRegistry.CapabilityEvaluation capabilityEvaluation,
                List<ConstraintCoverage> constraintLedger,
                List<String> explicitStudentConditions,
                List<String> modelAssumptions,
                List<String> introducedDefaults,
                List<String> unresolvedConditions,
                List<String> missingInputs,
                List<String> unsupportedDiagnostics,
                List<String> invalidDiagnostics,
                List<String> insufficientDiagnostics) {
            this(
                    outcome,
                    originalRequest,
                    capabilityEvaluation,
                    constraintLedger,
                    explicitStudentConditions,
                    List.of(),
                    List.of(),
                    modelAssumptions,
                    introducedDefaults,
                    unresolvedConditions,
                    missingInputs,
                    unsupportedDiagnostics,
                    invalidDiagnostics,
                    insufficientDiagnostics);
        }

        public boolean isReadyForSolving() {
            return outcome == Outcome.READY_FOR_SOLVING;
        }
    }

    private record OppositeConstraintPair(
            PhysicsCapabilityRegistry.ConstraintKind activeUnsupported,
            PhysicsCapabilityRegistry.ConstraintKind negatedSupported,
            String diagnosticSuffix) {}

    private record CuePatternRule(
            PhysicsCapabilityRegistry.ConstraintKind activeKind,
            PhysicsCapabilityRegistry.ConstraintKind negatedKind,
            Pattern pattern) {}

    private record DetectedCue(
            PhysicsCapabilityRegistry.ConstraintKind activeKind,
            PhysicsCapabilityRegistry.ConstraintKind negatedKind,
            boolean negated,
            InterpretationDraft.ConstraintOrigin origin,
            String evidenceSnippet) {}

    private record ScreeningResult(
            Set<PhysicsCapabilityRegistry.ConstraintKind> activeUnsupported,
            Set<PhysicsCapabilityRegistry.ConstraintKind> negatedSupported,
            Map<PhysicsCapabilityRegistry.ConstraintKind, InterpretationDraft.ConstraintOrigin> activeOrigins,
            Map<PhysicsCapabilityRegistry.ConstraintKind, String> activeEvidence,
            Map<PhysicsCapabilityRegistry.ConstraintKind, InterpretationDraft.ConstraintOrigin> negatedOrigins,
            Map<PhysicsCapabilityRegistry.ConstraintKind, String> negatedEvidence,
            boolean inclinedSurfaceMentioned,
            String inclinedSurfaceEvidence) {}

    private static final double EQUIVALENCE_REL_TOL = 1e-6;

    private static final List<OppositeConstraintPair> OPPOSITE_CONSTRAINT_PAIRS = List.of(
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.AIR_RESISTANCE,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_AIR_RESISTANCE,
                    "air-resistance-both-present-and-ignored"),
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.WIND,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_WIND,
                    "wind-both-present-and-ignored"),
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.MAGNUS_OR_SPIN,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_SPIN,
                    "spin-both-present-and-ignored"),
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.BOUNCE_OR_RESTITUTION,
                    PhysicsCapabilityRegistry.ConstraintKind.RIGID_NON_BOUNCING_CONTACT,
                    "bounce-restitution-both-present-and-ignored"),
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.NON_UNIFORM_GRAVITY,
                    PhysicsCapabilityRegistry.ConstraintKind.CONSTANT_DOWNWARD_GRAVITY,
                    "variable-gravity-both-present-and-ignored"),
            new OppositeConstraintPair(
                    PhysicsCapabilityRegistry.ConstraintKind.MOVING_REFERENCE_OR_TARGET,
                    PhysicsCapabilityRegistry.ConstraintKind.STATIONARY_FRAME,
                    "moving-frame-both-present-and-ignored"));

    private static final List<CuePatternRule> UNSUPPORTED_CUE_RULES = List.of(
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.AIR_RESISTANCE,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_AIR_RESISTANCE,
                    Pattern.compile("\\b(?:air\\s+resistance|air\\s+drag|aerodynamic\\s+drag|quadratic\\s+drag|linear\\s+drag|viscous\\s+damping|viscous\\s+drag|fluid\\s+resistance|terminal\\s+velocity|drag\\s+force|drag\\s+coefficient|drag)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.WIND,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_WIND,
                    Pattern.compile("\\b(?:crosswind|headwind|tailwind|winds?|gusts?|breeze)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.MAGNUS_OR_SPIN,
                    PhysicsCapabilityRegistry.ConstraintKind.NO_SPIN,
                    Pattern.compile("\\b(?:magnus(?:\\s+effect|\\s+force)?|topspin|backspin|sidespin|spinning|spins?|rotational\\s+spin|angular\\s+velocity)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.BOUNCE_OR_RESTITUTION,
                    PhysicsCapabilityRegistry.ConstraintKind.RIGID_NON_BOUNCING_CONTACT,
                    Pattern.compile("\\b(?:bounces?|bouncing|rebounds?|coefficient\\s+of\\s+restitution|restitution|elastic\\s+collision|inelastic\\s+collision)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.NON_UNIFORM_GRAVITY,
                    PhysicsCapabilityRegistry.ConstraintKind.CONSTANT_DOWNWARD_GRAVITY,
                    Pattern.compile("\\b(?:variable\\s+gravity|varying\\s+gravity|non[-\\s]?uniform\\s+gravity|altitude[-\\s]dependent\\s+gravity|inverse[-\\s]?square\\s+gravity|orbital\\s+gravity|gravity\\s+varies\\s+with)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.SURFACE_FRICTION_OR_SLIDING,
                    null,
                    Pattern.compile("\\b(?:frictional|friction|coefficient\\s+of\\s+friction|rough\\s+(?:incline|plane|surface|ramp)|rolls?\\s+(?:down|up|along|without\\s+slipping)|rolling|slides?\\s+(?:down|up|along)|sliding)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.MOVING_REFERENCE_OR_TARGET,
                    PhysicsCapabilityRegistry.ConstraintKind.STATIONARY_FRAME,
                    Pattern.compile("\\b(?:moving\\s+(?:platform|target|frame|elevator|lift|cart|car|train|truck|trolley|boat|ship|conveyor)|accelerating\\s+(?:platform|elevator|lift|frame|cart|train|car)|non[-\\s]?inertial\\s+frame|elevator)\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.CURVED_OR_PIECEWISE_SURFACE,
                    null,
                    Pattern.compile("\\b(?:curved\\s+(?:surface|ground|terrain|ramp|incline)|piecewise\\s+surface|stepped\\s+(?:surface|ground|stairs|terrain)|parabolic\\s+(?:surface|hill|terrain|ground)|hemispherical\\s+(?:surface|hill|dome)|spherical\\s+(?:surface|hill|dome))\\b", Pattern.CASE_INSENSITIVE)),
            new CuePatternRule(
                    PhysicsCapabilityRegistry.ConstraintKind.MULTIPLE_INTERACTING_BODIES,
                    null,
                    Pattern.compile("\\b(?:two\\s+(?:balls|projectiles|particles|stones|bodies|masses)|multiple\\s+(?:bodies|projectiles|particles)|second\\s+(?:ball|projectile|particle|stone|body|mass)|collide\\s+in\\s+mid[-\\s]?air|mid[-\\s]?air\\s+collision|collide\\s+with\\s+each\\s+other)\\b", Pattern.CASE_INSENSITIVE)));

    private static final Pattern VACUUM_PATTERN = Pattern.compile(
            "\\b(?:in\\s+(?:a\\s+)?vacuum|air[-\\s]?resistance[-\\s]?free|drag[-\\s]?free)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FRICTIONLESS_PATTERN = Pattern.compile(
            "\\b(?:frictionless|smooth\\s+(?:incline|inclined\\s+plane|plane|ramp|surface))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_NEGATED_GRAVITY_PATTERN = Pattern.compile(
            "\\b(?:constant|uniform)\\s+(?:downward\\s+)?gravity\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLINED_SURFACE_CUE_PATTERN = Pattern.compile(
            "\\b(?:inclined\\s+plane|incline\\s+plane|sloping\\s+(?:plane|ground|surface)|inclined\\s+surface|up\\s+(?:a|an|the)\\s+incline|down\\s+(?:a|an|the)\\s+incline|on\\s+(?:a|an|the)\\s+(?:\\d+(?:\\.\\d+)?\\s*(?:deg|degree|degrees|°)\\s+)?incline)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CLAUSE_SPLIT_PATTERN = Pattern.compile(
            "[.!?\\n;]+|,(?!\\d)|\\b(?:but|however|although|though|whereas|while|except)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATION_PREFIX_PATTERN = Pattern.compile(
            "\\b(?:neglect(?:ed|ing)?|ignore(?:d|ing)?|without|no|zero|omit(?:ted|ting)?|absence\\s+of|free\\s+of|absent|assume\\s+no)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATION_SUFFIX_PATTERN = Pattern.compile(
            "^\\s*(?:is|are|was|were|can\\s+be|may\\s+be|should\\s+be|be)?\\s*(?:neglected|ignored|omitted|negligible|absent|zero)\\b|^\\s*=\\s*0(?:\\.0+)?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_NEGATION_PATTERN = Pattern.compile(
            "\\b(?:not|cannot|can't|do\\s+not|don't|does\\s+not|doesn't|must\\s+not|should\\s+not|never)\\s+(?:neglect|ignore|omit|ignored|neglected|negligible|absent|zero|frictionless)\\b|\\bnon[-\\s]?negligible\\b|\\bnon[-\\s]?zero\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE_RESET_PATTERN = Pattern.compile(
            "\\b(?:experiences?|experiencing|subject\\s+to|acted\\s+(?:on|upon)\\s+by|consider(?:ing)?|include(?:s|d|ing)?|there\\s+(?:is|are)|with)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SHORT_YES_ANSWER = Pattern.compile(
            "^\\s*(?:yes|yeah|yep|true|present|correct|affirmative|include\\s+it|consider\\s+it)(?:\\W+.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_NO_ANSWER = Pattern.compile(
            "^\\s*(?:no|nope|false|none|neglect(?:\\s+it)?|ignore(?:\\s+it)?|neglected|ignored|negligible|not\\s+present)(?:\\W+.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GRAVITY_MENTION_PATTERN = Pattern.compile(
            "\\b(?:gravity|gravitational\\s+acceleration)\\b|\\bg\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRAVITY_VALUE_PATTERN = Pattern.compile(
            "(?:^|\\b)(?:g\\s*=|take\\s+g\\s*(?:=|as|to\\s+be)|use\\s+(?:g\\s*=)?|gravity\\s*(?:=|is|of))?\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(m\\s*/\\s*s\\^?2|m\\s*s\\^-2|ft\\s*/\\s*s\\^?2|cm\\s*/\\s*s\\^?2|N\\s*/\\s*kg)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLINE_ANGLE_QUESTION_PATTERN = Pattern.compile(
            "\\b(?:incline|inclined\\s+plane|slope|ramp)\\b.*\\bangle\\b|\\bangle\\b.*\\b(?:incline|inclined\\s+plane|slope|ramp)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANGLE_VALUE_PATTERN = Pattern.compile(
            "([+-]?\\d+(?:\\.\\d+)?)\\s*(?:deg|degrees?|°)\\b",
            Pattern.CASE_INSENSITIVE);

    private final PhysicsCapabilityRegistry registry;

    public ConstraintCompletenessValidator() {
        this(new PhysicsCapabilityRegistry());
    }

    public ConstraintCompletenessValidator(PhysicsCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Physics capability registry is required");
    }

    public CompletenessReport validate(InterpretationRequest originalRequest,
                                       InterpretationDraft draft,
                                       CanonicalPhysicsProblem canonical) {
        Set<String> invalidDiagnostics = new LinkedHashSet<>();
        Set<String> unsupportedDiagnostics = new LinkedHashSet<>();
        Set<String> insufficientDiagnostics = new LinkedHashSet<>();
        Set<String> missingInputs = new LinkedHashSet<>();
        Set<String> unresolvedConditions = new LinkedHashSet<>();
        Set<String> explicitStudentConditions = new LinkedHashSet<>();
        Set<String> clarificationDerivedConditions = new LinkedHashSet<>();
        Set<String> interpreterInferredConditions = new LinkedHashSet<>();
        Set<String> modelAssumptions = new LinkedHashSet<>();
        Set<String> introducedDefaults = new LinkedHashSet<>();
        List<ConstraintCoverage> ledger = new ArrayList<>();

        if (originalRequest == null) {
            invalidDiagnostics.add("original-request-required");
        }
        if (draft == null) {
            invalidDiagnostics.add("interpretation-draft-required");
        } else {
            if (!Double.isFinite(draft.confidence()) || draft.confidence() < 0.0 || draft.confidence() > 1.0) {
                invalidDiagnostics.add("invalid-interpretation-confidence");
            }
            if (!draft.clarifications().isEmpty()) {
                for (InterpretationDraft.Clarification c : draft.clarifications()) {
                    unresolvedConditions.add(c.prompt());
                }
                insufficientDiagnostics.add("clarifications-requested:" + draft.clarifications().size());
            }
            if (draft.parts().isEmpty() && draft.canonicalTargets().isEmpty() && draft.clarifications().isEmpty()) {
                insufficientDiagnostics.add("incomplete-interpretation:no-targets-extracted");
            }
        }

        PhysicsCapabilityRegistry.CapabilityEvaluation baseEvaluation = registry.evaluate(canonical);
        invalidDiagnostics.addAll(baseEvaluation.invalidDiagnostics());
        unsupportedDiagnostics.addAll(baseEvaluation.unsupportedReasons());
        missingInputs.addAll(baseEvaluation.missingInputs());

        ScreeningResult screening = screenOriginalRequest(originalRequest);

        Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds =
                toKindEnumSet(baseEvaluation.supportedConstraints());
        Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds =
                toKindEnumSet(baseEvaluation.unsupportedConstraints());
        Set<PhysicsCapabilityRegistry.ConstraintKind> assertedSupportedKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> assertedUnsupportedKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> unresolvedExtractedKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> draftUnsupportedKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> draftAssumptionNegatedKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> draftAssumptionActiveKinds =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);

        if (canonical == null) {
            invalidDiagnostics.add("canonical-problem-required");
        } else {
            if (draft != null) {
                inspectDraftAssumptions(draft, draftAssumptionNegatedKinds, draftAssumptionActiveKinds);
                checkDraftSurfaceConsistency(draft, canonical, invalidDiagnostics,
                        insufficientDiagnostics, unresolvedConditions, ledger);
                checkAngleReferenceConsistency(draft, canonical, invalidDiagnostics);
                checkTargetCoverageAndParameters(draft, canonical, invalidDiagnostics);
                checkExtractedConstraints(draft, canonical, baseEvaluation,
                        canonicalSupportedKinds, canonicalUnsupportedKinds,
                        assertedSupportedKinds, assertedUnsupportedKinds,
                        unresolvedExtractedKinds, draftUnsupportedKinds,
                        explicitStudentConditions, clarificationDerivedConditions,
                        interpreterInferredConditions, unresolvedConditions,
                        invalidDiagnostics, unsupportedDiagnostics, insufficientDiagnostics, ledger);
            }
            checkClarificationQuantityUpdates(originalRequest, canonical,
                    invalidDiagnostics, missingInputs, ledger);
            reconcileOriginalContextCues(
                    screening, draft, canonical,
                    canonicalSupportedKinds, canonicalUnsupportedKinds,
                    assertedSupportedKinds, assertedUnsupportedKinds,
                    unresolvedExtractedKinds, draftUnsupportedKinds,
                    explicitStudentConditions, clarificationDerivedConditions,
                    invalidDiagnostics, unsupportedDiagnostics, ledger);
            detectAllContradictions(
                    screening,
                    canonicalSupportedKinds, canonicalUnsupportedKinds,
                    assertedSupportedKinds, assertedUnsupportedKinds,
                    draftUnsupportedKinds,
                    draftAssumptionNegatedKinds, draftAssumptionActiveKinds,
                    invalidDiagnostics);

            Set<PhysicsCapabilityRegistry.ConstraintKind> activeUnsupportedForAssumptions =
                    EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
            activeUnsupportedForAssumptions.addAll(canonicalUnsupportedKinds);
            activeUnsupportedForAssumptions.addAll(assertedUnsupportedKinds);
            activeUnsupportedForAssumptions.addAll(draftUnsupportedKinds);
            activeUnsupportedForAssumptions.addAll(screening.activeUnsupported());

            partitionAssumptions(
                    canonical, draft, activeUnsupportedForAssumptions,
                    modelAssumptions, introducedDefaults);
            recordRemainingCanonicalConstraints(canonical, baseEvaluation, ledger);
        }

        Outcome outcome;
        if (!invalidDiagnostics.isEmpty()) {
            outcome = Outcome.INVALID_INPUT;
        } else if (!unsupportedDiagnostics.isEmpty()) {
            outcome = Outcome.UNSUPPORTED;
        } else if (!insufficientDiagnostics.isEmpty() || !missingInputs.isEmpty() || !unresolvedConditions.isEmpty()) {
            outcome = Outcome.INSUFFICIENT_INFORMATION;
        } else {
            outcome = Outcome.READY_FOR_SOLVING;
        }

        return new CompletenessReport(
                outcome,
                originalRequest,
                baseEvaluation,
                ledger,
                List.copyOf(explicitStudentConditions),
                List.copyOf(clarificationDerivedConditions),
                List.copyOf(interpreterInferredConditions),
                List.copyOf(modelAssumptions),
                List.copyOf(introducedDefaults),
                List.copyOf(unresolvedConditions),
                List.copyOf(missingInputs),
                List.copyOf(unsupportedDiagnostics),
                List.copyOf(invalidDiagnostics),
                List.copyOf(insufficientDiagnostics));
    }

    private ScreeningResult screenOriginalRequest(InterpretationRequest request) {
        Set<PhysicsCapabilityRegistry.ConstraintKind> activeUnsupported =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Set<PhysicsCapabilityRegistry.ConstraintKind> negatedSupported =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        Map<PhysicsCapabilityRegistry.ConstraintKind, InterpretationDraft.ConstraintOrigin> activeOrigins =
                new EnumMap<>(PhysicsCapabilityRegistry.ConstraintKind.class);
        Map<PhysicsCapabilityRegistry.ConstraintKind, String> activeEvidence =
                new EnumMap<>(PhysicsCapabilityRegistry.ConstraintKind.class);
        Map<PhysicsCapabilityRegistry.ConstraintKind, InterpretationDraft.ConstraintOrigin> negatedOrigins =
                new EnumMap<>(PhysicsCapabilityRegistry.ConstraintKind.class);
        Map<PhysicsCapabilityRegistry.ConstraintKind, String> negatedEvidence =
                new EnumMap<>(PhysicsCapabilityRegistry.ConstraintKind.class);

        boolean inclinedSurfaceMentioned = false;
        String inclinedSurfaceEvidence = null;

        if (request == null) {
            return new ScreeningResult(
                    activeUnsupported, negatedSupported,
                    activeOrigins, activeEvidence,
                    negatedOrigins, negatedEvidence,
                    false, null);
        }

        List<DetectedCue> cues = new ArrayList<>(
                scanTextForCues(request.question(), InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT));
        Matcher inclineQuestionMatcher = INCLINED_SURFACE_CUE_PATTERN.matcher(request.question());
        if (inclineQuestionMatcher.find()) {
            inclinedSurfaceMentioned = true;
            inclinedSurfaceEvidence = inclineQuestionMatcher.group().trim();
        }

        for (InterpretationRequest.ClarificationTurn turn : request.clarifications()) {
            List<DetectedCue> answerCues = scanTextForCues(
                    turn.answer(), InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER);
            if (!answerCues.isEmpty()) {
                cues.addAll(answerCues);
            } else {
                boolean yes = SHORT_YES_ANSWER.matcher(turn.answer()).matches();
                boolean no = !yes && SHORT_NO_ANSWER.matcher(turn.answer()).matches();
                boolean numericParamAnswer = extractFirstNumber(turn.answer()) != null;
                List<DetectedCue> questionCues = scanTextForCues(
                        turn.question(), InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER);
                for (DetectedCue qCue : questionCues) {
                    if (yes || (numericParamAnswer && !qCue.negated())) {
                        cues.add(new DetectedCue(
                                qCue.activeKind(), qCue.negatedKind(), qCue.negated(),
                                InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER,
                                turn.question() + " -> " + turn.answer()));
                    } else if (no && qCue.negatedKind() != null) {
                        cues.add(new DetectedCue(
                                qCue.activeKind(), qCue.negatedKind(), !qCue.negated(),
                                InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER,
                                turn.question() + " -> " + turn.answer()));
                    }
                }
            }
            if (!inclinedSurfaceMentioned) {
                Matcher turnIncline = INCLINED_SURFACE_CUE_PATTERN.matcher(turn.answer());
                if (turnIncline.find()) {
                    inclinedSurfaceMentioned = true;
                    inclinedSurfaceEvidence = turnIncline.group().trim();
                }
            }
        }

        for (DetectedCue cue : cues) {
            if (!cue.negated()) {
                if (cue.activeKind() != null) {
                    activeUnsupported.add(cue.activeKind());
                    activeOrigins.putIfAbsent(cue.activeKind(), cue.origin());
                    activeEvidence.putIfAbsent(cue.activeKind(), cue.evidenceSnippet());
                }
            } else if (cue.negatedKind() != null) {
                negatedSupported.add(cue.negatedKind());
                negatedOrigins.putIfAbsent(cue.negatedKind(), cue.origin());
                negatedEvidence.putIfAbsent(cue.negatedKind(), cue.evidenceSnippet());
            }
        }

        return new ScreeningResult(
                activeUnsupported, negatedSupported,
                activeOrigins, activeEvidence,
                negatedOrigins, negatedEvidence,
                inclinedSurfaceMentioned, inclinedSurfaceEvidence);
    }

    private List<DetectedCue> scanTextForCues(String text, InterpretationDraft.ConstraintOrigin origin) {
        List<DetectedCue> detected = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return detected;
        }
        String[] clauses = CLAUSE_SPLIT_PATTERN.split(text);
        for (String rawClause : clauses) {
            String clause = rawClause.trim();
            if (clause.isEmpty()) {
                continue;
            }
            boolean hasFrictionless = FRICTIONLESS_PATTERN.matcher(clause).find();
            Matcher vacuumMatcher = VACUUM_PATTERN.matcher(clause);
            if (vacuumMatcher.find()) {
                boolean negatedVacuum = DOUBLE_NEGATION_PATTERN.matcher(clause).find();
                detected.add(new DetectedCue(
                        PhysicsCapabilityRegistry.ConstraintKind.AIR_RESISTANCE,
                        PhysicsCapabilityRegistry.ConstraintKind.NO_AIR_RESISTANCE,
                        !negatedVacuum,
                        origin,
                        clause));
            }
            Matcher constGravityMatcher = EXPLICIT_NEGATED_GRAVITY_PATTERN.matcher(clause);
            if (constGravityMatcher.find()) {
                detected.add(new DetectedCue(
                        PhysicsCapabilityRegistry.ConstraintKind.NON_UNIFORM_GRAVITY,
                        PhysicsCapabilityRegistry.ConstraintKind.CONSTANT_DOWNWARD_GRAVITY,
                        true,
                        origin,
                        clause));
            }

            for (CuePatternRule rule : UNSUPPORTED_CUE_RULES) {
                Matcher matcher = rule.pattern().matcher(clause);
                while (matcher.find()) {
                    if (rule.activeKind() == PhysicsCapabilityRegistry.ConstraintKind.SURFACE_FRICTION_OR_SLIDING
                            && hasFrictionless
                            && matcher.group().toLowerCase(Locale.ROOT).contains("friction")) {
                        continue;
                    }
                    boolean negated = isNegatedMatchInClause(clause, matcher.start(), matcher.end());
                    detected.add(new DetectedCue(
                            rule.activeKind(),
                            rule.negatedKind(),
                            negated,
                            origin,
                            clause));
                }
            }
        }
        return detected;
    }

    private static boolean isNegatedMatchInClause(String clause, int matchStart, int matchEnd) {
        String prefix = clause.substring(0, matchStart);
        String suffix = clause.substring(matchEnd);
        if (DOUBLE_NEGATION_PATTERN.matcher(prefix).find()
                || DOUBLE_NEGATION_PATTERN.matcher(suffix).find()
                || DOUBLE_NEGATION_PATTERN.matcher(clause).find()) {
            return false;
        }
        if (NEGATION_SUFFIX_PATTERN.matcher(suffix).find()) {
            return true;
        }
        Matcher negPrefix = NEGATION_PREFIX_PATTERN.matcher(prefix);
        int lastNegEnd = -1;
        while (negPrefix.find()) {
            lastNegEnd = negPrefix.end();
        }
        if (lastNegEnd < 0) {
            return false;
        }
        String between = prefix.substring(lastNegEnd);
        return !POSITIVE_RESET_PATTERN.matcher(between).find();
    }

    private void inspectDraftAssumptions(InterpretationDraft draft,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> assumptionNegatedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> assumptionActiveKinds) {
        for (String rawAssumption : draft.assumptions()) {
            if (rawAssumption == null || rawAssumption.isBlank()) {
                continue;
            }
            PhysicsCapabilityRegistry.ConstraintClassification direct = registry.classifyConstraint(rawAssumption);
            if (direct.known()) {
                if (direct.supported()) {
                    assumptionNegatedKinds.add(direct.kind());
                } else {
                    assumptionActiveKinds.add(direct.kind());
                }
            }
            for (DetectedCue cue : scanTextForCues(rawAssumption, InterpretationDraft.ConstraintOrigin.INTERPRETER_INFERRED)) {
                if (cue.negated() && cue.negatedKind() != null) {
                    assumptionNegatedKinds.add(cue.negatedKind());
                } else if (!cue.negated() && cue.activeKind() != null) {
                    assumptionActiveKinds.add(cue.activeKind());
                }
            }
        }
    }

    private void checkDraftSurfaceConsistency(InterpretationDraft draft,
                                              CanonicalPhysicsProblem canonical,
                                              Set<String> invalidDiagnostics,
                                              Set<String> insufficientDiagnostics,
                                              Set<String> unresolvedConditions,
                                              List<ConstraintCoverage> ledger) {
        InterpretationDraft.SurfaceSpec spec = draft.surface();
        if (spec == null) {
            return;
        }
        LandingSurface canonicalSurface = canonical.landingSurface();

        if (spec instanceof InterpretationDraft.SurfaceSpec.Unresolved unresolved) {
            unresolvedConditions.add(unresolved.reason());
            insufficientDiagnostics.add("unresolved-landing-surface:" + unresolved.reason());
            ledger.add(new ConstraintCoverage(
                    "landing-surface",
                    InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                    ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                    true,
                    "None",
                    unresolved.sourceEvidence(),
                    unresolved.reason()));
            return;
        }

        if (spec instanceof InterpretationDraft.SurfaceSpec.Horizontal horizontal) {
            Double landingY = horizontal.landingY();
            if (landingY != null && !Double.isFinite(landingY)) {
                invalidDiagnostics.add("non-finite-draft-surface-landing-y");
                return;
            }
            if (!canonicalSurface.isHorizontal()) {
                invalidDiagnostics.add("canonical-surface-mismatch:draft-horizontal-canonical-inclined");
                return;
            }
            if (landingY != null && !nearlyEqual(landingY, canonicalSurface.anchorY())) {
                invalidDiagnostics.add("canonical-surface-mismatch:horizontal-height-differs");
            }
            return;
        }

        if (spec instanceof InterpretationDraft.SurfaceSpec.Inclined inclined) {
            Double beta = inclined.angleDegrees();
            if (beta == null) {
                unresolvedConditions.add("missing-incline-angle");
                insufficientDiagnostics.add("unresolved-incline-angle");
                ledger.add(new ConstraintCoverage(
                        "inclined-landing-surface",
                        InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                        true,
                        "None",
                        null,
                        "Incline surface extracted without numeric slope angle"));
                return;
            }
            if (!Double.isFinite(beta) || beta <= -90.0 || beta >= 90.0
                    || (inclined.anchorX() != null && !Double.isFinite(inclined.anchorX()))
                    || (inclined.anchorY() != null && !Double.isFinite(inclined.anchorY()))) {
                invalidDiagnostics.add("invalid-draft-incline-surface-geometry");
                return;
            }
            if (beta != 0.0 && canonicalSurface.isHorizontal()) {
                invalidDiagnostics.add("canonical-surface-mismatch:inclined-overridden-by-horizontal");
                ledger.add(new ConstraintCoverage(
                        PhysicsCapabilityRegistry.ConstraintKind.INCLINED_LANDING_SURFACE.canonicalId(),
                        InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintCoverageStatus.MISSING_FROM_CANONICAL,
                        true,
                        "HorizontalPlane(y=" + canonicalSurface.anchorY() + " m)",
                        "incline angle " + beta + " deg",
                        "Draft specified an inclined surface, but canonical specification defaulted to horizontal"));
                return;
            }
            if (!nearlyEqual(beta, canonicalSurface.angleDegrees())
                    || (inclined.anchorX() != null && !nearlyEqual(inclined.anchorX(), canonicalSurface.anchorX()))
                    || (inclined.anchorY() != null && !nearlyEqual(inclined.anchorY(), canonicalSurface.anchorY()))) {
                invalidDiagnostics.add("canonical-surface-mismatch:inclined-parameters-differ");
            }
        }
    }

    private static void checkAngleReferenceConsistency(InterpretationDraft draft,
                                                       CanonicalPhysicsProblem canonical,
                                                       Set<String> invalidDiagnostics) {
        if (canonical.hasVelocity()
                && draft.angleReference() != canonical.velocityMetadata().angleReference()) {
            invalidDiagnostics.add("canonical-angle-reference-mismatch");
        }
    }

    private static void checkTargetCoverageAndParameters(InterpretationDraft draft,
                                                         CanonicalPhysicsProblem canonical,
                                                         Set<String> invalidDiagnostics) {
        List<CanonicalPhysicsProblem.PartSpec> canonicalParts = canonical.parts();
        boolean[] matchedIndices = new boolean[canonicalParts.size()];

        for (InterpretationDraft.ProblemPart draftPart : draft.parts()) {
            CanonicalPhysicsProblem.CanonicalTarget expectedTarget = CanonicalPhysicsProblem.target(draftPart.target());
            int exactIndex = -1;
            int targetOnlyIndex = -1;
            for (int i = 0; i < canonicalParts.size(); i++) {
                if (matchedIndices[i]) {
                    continue;
                }
                if (canonicalParts.get(i).target().equals(expectedTarget)) {
                    if (targetOnlyIndex < 0) {
                        targetOnlyIndex = i;
                    }
                    if (allPartParametersMatch(draftPart.givens(), canonical, canonicalParts.get(i))) {
                        exactIndex = i;
                        break;
                    }
                }
            }
            int chosenIndex = exactIndex >= 0 ? exactIndex : targetOnlyIndex;
            if (chosenIndex < 0) {
                // Allow duplicate-target fallback check before reporting missing
                boolean targetExistsAtAll = canonicalParts.stream().anyMatch(p -> p.target().equals(expectedTarget));
                if (!targetExistsAtAll) {
                    invalidDiagnostics.add("interpretation-target-missing-from-canonical:" + draftPart.target().name());
                } else {
                    invalidDiagnostics.add("interpretation-target-count-mismatch:" + draftPart.target().name());
                }
                continue;
            }
            matchedIndices[chosenIndex] = true;
            CanonicalPhysicsProblem.PartSpec canonicalPart = canonicalParts.get(chosenIndex);
            comparePartParameters(draftPart, canonical, canonicalPart, invalidDiagnostics);
        }

        Set<CanonicalPhysicsProblem.CanonicalTarget> canonicalTargetSet = new LinkedHashSet<>();
        for (CanonicalPhysicsProblem.PartSpec part : canonicalParts) {
            canonicalTargetSet.add(part.target());
        }
        for (CanonicalPhysicsProblem.CanonicalTarget extraTarget : draft.canonicalTargets()) {
            if (!canonicalTargetSet.contains(extraTarget)) {
                invalidDiagnostics.add("interpretation-target-missing-from-canonical:" + extraTarget.name());
            }
        }
    }

    private static boolean allPartParametersMatch(Map<ProjectileProblem.Input, PhysicalQuantity> draftGivens,
                                                  CanonicalPhysicsProblem canonical,
                                                  CanonicalPhysicsProblem.PartSpec canonicalPart) {
        for (Map.Entry<ProjectileProblem.Input, PhysicalQuantity> entry : draftGivens.entrySet()) {
            ProjectileProblem.Input input = entry.getKey();
            PhysicalQuantity draftQty = entry.getValue();
            if (isSharedCanonicalInput(input)) {
                if (!sharedInputMatchesCanonical(input, draftQty, canonical)) {
                    return false;
                }
            } else {
                PhysicalQuantity canonQty = canonicalPart.parameters().get(input);
                if (canonQty == null
                        || canonQty.unit().dimension() != draftQty.unit().dimension()
                        || !nearlyEqual(draftQty.valueInSi(), canonQty.valueInSi())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void comparePartParameters(InterpretationDraft.ProblemPart draftPart,
                                              CanonicalPhysicsProblem canonical,
                                              CanonicalPhysicsProblem.PartSpec canonicalPart,
                                              Set<String> invalidDiagnostics) {
        draftPart.givens().forEach((input, draftQty) -> {
            if (isSharedCanonicalInput(input)) {
                if (!sharedInputMatchesCanonical(input, draftQty, canonical)) {
                    invalidDiagnostics.add("canonical-given-parameter-mismatch:" + input.name());
                }
                return;
            }
            PhysicalQuantity canonicalQty = canonicalPart.parameters().get(input);
            if (canonicalQty == null) {
                invalidDiagnostics.add("canonical-target-parameter-missing:"
                        + draftPart.target().name() + ":" + input.name());
            } else if (canonicalQty.unit().dimension() != draftQty.unit().dimension()
                    || !nearlyEqual(draftQty.valueInSi(), canonicalQty.valueInSi())) {
                invalidDiagnostics.add("canonical-target-parameter-mismatch:"
                        + draftPart.target().name() + ":" + input.name());
            }
        });
    }

    private static boolean isSharedCanonicalInput(ProjectileProblem.Input input) {
        return switch (input) {
            case INITIAL_X, INITIAL_Y, LANDING_Y, GRAVITY, INITIAL_SPEED, LAUNCH_ANGLE -> true;
            default -> false;
        };
    }

    private static boolean sharedInputMatchesCanonical(ProjectileProblem.Input input,
                                                     PhysicalQuantity draftQty,
                                                     CanonicalPhysicsProblem canonical) {
        if (input.dimension() != draftQty.unit().dimension() || !Double.isFinite(draftQty.valueInSi())) {
            return false;
        }
        return switch (input) {
            case INITIAL_X -> nearlyEqual(draftQty.valueInSi(), canonical.initialX());
            case INITIAL_Y -> nearlyEqual(draftQty.valueInSi(), canonical.initialY());
            case LANDING_Y -> canonical.landingSurface().isHorizontal()
                    && nearlyEqual(draftQty.valueInSi(), canonical.landingSurface().anchorY());
            case GRAVITY -> canonical.hasGravity()
                    && nearlyEqual(draftQty.valueInSi(), canonical.gravity().getAsDouble());
            case INITIAL_SPEED -> {
                if (canonical.derivedInitialSpeed().isPresent()) {
                    yield nearlyEqual(draftQty.valueInSi(), canonical.derivedInitialSpeed().getAsDouble());
                }
                PhysicalQuantity submitted = canonical.velocityMetadata().submittedSpeed();
                yield submitted != null && nearlyEqual(draftQty.valueInSi(), submitted.valueInSi());
            }
            case LAUNCH_ANGLE -> {
                PhysicalQuantity submitted = canonical.velocityMetadata().submittedAngle();
                if (submitted != null) {
                    yield nearlyEqual(draftQty.valueInSi(), submitted.valueInSi());
                }
                yield canonical.derivedLaunchAngleDegrees().isPresent()
                        && nearlyEqual(draftQty.valueInSi(),
                        canonical.derivedLaunchAngleDegrees().getAsDouble());
            }
            default -> false;
        };
    }

    private void checkExtractedConstraints(InterpretationDraft draft,
                                           CanonicalPhysicsProblem canonical,
                                           PhysicsCapabilityRegistry.CapabilityEvaluation baseEvaluation,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> assertedSupportedKinds,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> assertedUnsupportedKinds,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> unresolvedExtractedKinds,
                                           Set<PhysicsCapabilityRegistry.ConstraintKind> draftUnsupportedKinds,
                                           Set<String> explicitStudentConditions,
                                           Set<String> clarificationDerivedConditions,
                                           Set<String> interpreterInferredConditions,
                                           Set<String> unresolvedConditions,
                                           Set<String> invalidDiagnostics,
                                           Set<String> unsupportedDiagnostics,
                                           Set<String> insufficientDiagnostics,
                                           List<ConstraintCoverage> ledger) {
        for (InterpretationDraft.ExtractedConstraint constraint : draft.extractedConstraints()) {
            recordOriginBucket(
                    constraint,
                    explicitStudentConditions,
                    clarificationDerivedConditions,
                    interpreterInferredConditions);

            if (constraint.resolution() == InterpretationDraft.ConstraintResolution.UNRESOLVED_OR_AMBIGUOUS) {
                PhysicsCapabilityRegistry.ConstraintClassification cls = registry.classifyConstraint(constraint.identifier());
                if (cls.known()) {
                    unresolvedExtractedKinds.add(cls.kind());
                }
                String label = cls.known() ? cls.canonicalId() : constraint.identifier();
                unresolvedConditions.add(label);
                insufficientDiagnostics.add("unresolved-constraint:" + label);
                ledger.add(new ConstraintCoverage(
                        label,
                        constraint.origin(),
                        ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                        true,
                        "None",
                        constraint.sourceEvidence(),
                        "Constraint requires clarification before solving"));
                continue;
            }

            PhysicsCapabilityRegistry.ConstraintClassification classification =
                    registry.classifyConstraint(constraint.identifier());

            if (!classification.known()) {
                String token = classification.rawToken();
                unsupportedDiagnostics.add("unknown-unmapped-constraint:" + token);
                ledger.add(new ConstraintCoverage(
                        token,
                        constraint.origin(),
                        ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                        true,
                        "None",
                        constraint.sourceEvidence(),
                        "Constraint has no registered physics capability"));
                continue;
            }

            PhysicsCapabilityRegistry.ConstraintKind kind = classification.kind();

            if (!classification.supported()) {
                assertedUnsupportedKinds.add(kind);
                unsupportedDiagnostics.add("unsupported-constraint:" + kind.canonicalId());
                if (requiresSourceEvidence(constraint.origin()) && !hasMeaningfulEvidence(constraint.sourceEvidence())) {
                    invalidDiagnostics.add("missing-constraint-source-evidence:" + kind.canonicalId());
                }
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        constraint.origin(),
                        ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                        true,
                        "None",
                        constraint.sourceEvidence(),
                        "Physics engine does not implement " + kind.canonicalId()));
                continue;
            }

            assertedSupportedKinds.add(kind);

            if (requiresSourceEvidence(constraint.origin()) && !hasMeaningfulEvidence(constraint.sourceEvidence())) {
                invalidDiagnostics.add("missing-constraint-source-evidence:" + kind.canonicalId());
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        constraint.origin(),
                        ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                        true,
                        describeCanonicalRepresentation(kind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds),
                        constraint.sourceEvidence(),
                        "Extracted student/clarification constraint lacks non-blank source evidence"));
                continue;
            }

            if (isRepresentedInCanonical(kind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds)) {
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        constraint.origin(),
                        ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                        true,
                        describeCanonicalRepresentation(kind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds),
                        constraint.sourceEvidence(),
                        "Represented in canonical specification and supported by registry"));
            } else {
                invalidDiagnostics.add("constraint-missing-from-canonical:" + kind.canonicalId());
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        constraint.origin(),
                        ConstraintCoverageStatus.MISSING_FROM_CANONICAL,
                        true,
                        "None",
                        constraint.sourceEvidence(),
                        "Extracted constraint is not genuinely reflected in canonical specification"));
            }
        }

        for (String draftUnsupported : draft.unsupportedConstraints()) {
            PhysicsCapabilityRegistry.ConstraintClassification cls = registry.classifyConstraint(draftUnsupported);
            if (cls.isBlank()) {
                continue;
            }
            if (!cls.known()) {
                unsupportedDiagnostics.add("unknown-unmapped-constraint:" + cls.rawToken());
                if (ledger.stream().noneMatch(e -> e.identifier().equals(cls.rawToken()))) {
                    ledger.add(new ConstraintCoverage(
                            cls.rawToken(),
                            InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                            ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                            true,
                            "None",
                            null,
                            "Declared unknown unsupported constraint by interpretation"));
                }
                continue;
            }
            PhysicsCapabilityRegistry.ConstraintKind kind = cls.kind();
            if (!cls.supported()) {
                draftUnsupportedKinds.add(kind);
                unsupportedDiagnostics.add("unsupported-constraint:" + kind.canonicalId());
                if (ledger.stream().noneMatch(e -> e.identifier().equals(kind.canonicalId()))) {
                    ledger.add(new ConstraintCoverage(
                            kind.canonicalId(),
                            InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                            ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                            true,
                            "None",
                            null,
                            "Declared unsupported by interpretation"));
                }
            } else {
                invalidDiagnostics.add("supported-constraint-marked-unsupported:" + kind.canonicalId());
            }
        }
    }

    private void checkClarificationQuantityUpdates(InterpretationRequest request,
                                                   CanonicalPhysicsProblem canonical,
                                                   Set<String> invalidDiagnostics,
                                                   Set<String> missingInputs,
                                                   List<ConstraintCoverage> ledger) {
        if (request == null) {
            return;
        }
        for (InterpretationRequest.ClarificationTurn turn : request.clarifications()) {
            String combined = turn.question() + " " + turn.answer();
            if (GRAVITY_MENTION_PATTERN.matcher(combined).find()) {
                Double clarifiedG = parseGravityFromAnswer(turn.answer());
                if (clarifiedG != null && Double.isFinite(clarifiedG)) {
                    if (!canonical.hasGravity()) {
                        missingInputs.add(ProjectileProblem.Input.GRAVITY.name());
                        invalidDiagnostics.add("clarification-unapplied:gravity-missing-from-canonical");
                        ledger.add(new ConstraintCoverage(
                                "clarified-gravity",
                                InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER,
                                ConstraintCoverageStatus.MISSING_FROM_CANONICAL,
                                false,
                                "None",
                                turn.answer(),
                                "Clarification supplied gravity = " + clarifiedG + " m/s^2 but canonical lacks gravity"));
                    } else if (!nearlyEqual(clarifiedG, canonical.gravity().getAsDouble())) {
                        invalidDiagnostics.add("clarification-mismatch:gravity-not-updated-in-canonical");
                        ledger.add(new ConstraintCoverage(
                                "clarified-gravity",
                                InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER,
                                ConstraintCoverageStatus.CONTRADICTORY,
                                true,
                                "Gravity=" + canonical.gravity().getAsDouble() + " m/s^2",
                                turn.answer(),
                                "Clarification specified gravity = " + clarifiedG
                                        + " m/s^2, but canonical retained " + canonical.gravity().getAsDouble() + " m/s^2"));
                    }
                }
            }

            if (INCLINE_ANGLE_QUESTION_PATTERN.matcher(turn.question()).find()) {
                Matcher angleMatcher = ANGLE_VALUE_PATTERN.matcher(turn.answer());
                if (angleMatcher.find()) {
                    double clarifiedAngle = Double.parseDouble(angleMatcher.group(1));
                    if (canonical.landingSurface().isHorizontal()
                            || !nearlyEqual(clarifiedAngle, canonical.landingSurface().angleDegrees())) {
                        invalidDiagnostics.add("clarification-mismatch:incline-angle-not-updated-in-canonical");
                        ledger.add(new ConstraintCoverage(
                                "clarified-incline-angle",
                                InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER,
                                ConstraintCoverageStatus.CONTRADICTORY,
                                true,
                                canonical.landingSurface().isHorizontal()
                                        ? "HorizontalPlane"
                                        : "InclinedPlane(angle=" + canonical.landingSurface().angleDegrees() + "°)",
                                turn.answer(),
                                "Clarification specified incline angle = " + clarifiedAngle + "°"));
                    }
                }
            }
        }
    }

    private void reconcileOriginalContextCues(ScreeningResult screening,
                                              InterpretationDraft draft,
                                              CanonicalPhysicsProblem canonical,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> assertedSupportedKinds,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> assertedUnsupportedKinds,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> unresolvedExtractedKinds,
                                              Set<PhysicsCapabilityRegistry.ConstraintKind> draftUnsupportedKinds,
                                              Set<String> explicitStudentConditions,
                                              Set<String> clarificationDerivedConditions,
                                              Set<String> invalidDiagnostics,
                                              Set<String> unsupportedDiagnostics,
                                              List<ConstraintCoverage> ledger) {
        Set<PhysicsCapabilityRegistry.ConstraintKind> accountedUnsupported =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        accountedUnsupported.addAll(assertedUnsupportedKinds);
        accountedUnsupported.addAll(draftUnsupportedKinds);
        accountedUnsupported.addAll(canonicalUnsupportedKinds);
        accountedUnsupported.addAll(unresolvedExtractedKinds);

        for (PhysicsCapabilityRegistry.ConstraintKind activeKind : screening.activeUnsupported()) {
            InterpretationDraft.ConstraintOrigin origin = screening.activeOrigins().get(activeKind);
            String evidence = screening.activeEvidence().get(activeKind);
            addConditionByOrigin(origin, activeKind.canonicalId(), evidence,
                    explicitStudentConditions, clarificationDerivedConditions);

            if (!accountedUnsupported.contains(activeKind)) {
                unsupportedDiagnostics.add("incomplete-constraint-extraction:" + activeKind.canonicalId());
                unsupportedDiagnostics.add("unsupported-constraint:" + activeKind.canonicalId());
                if (ledger.stream().noneMatch(e -> e.identifier().equals(activeKind.canonicalId()))) {
                    ledger.add(new ConstraintCoverage(
                            activeKind.canonicalId(),
                            origin,
                            ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION,
                            false,
                            "None",
                            evidence,
                            "Explicit physical condition in original request/clarification omitted by interpreter"));
                }
            }
        }

        for (PhysicsCapabilityRegistry.ConstraintKind negatedKind : screening.negatedSupported()) {
            InterpretationDraft.ConstraintOrigin origin = screening.negatedOrigins().get(negatedKind);
            String evidence = screening.negatedEvidence().get(negatedKind);
            addConditionByOrigin(origin, negatedKind.canonicalId(), evidence,
                    explicitStudentConditions, clarificationDerivedConditions);

            PhysicsCapabilityRegistry.ConstraintKind opposingActive = opposingActiveFor(negatedKind);
            if (opposingActive != null
                    && !screening.activeUnsupported().contains(opposingActive)
                    && (assertedUnsupportedKinds.contains(opposingActive)
                    || draftUnsupportedKinds.contains(opposingActive)
                    || canonicalUnsupportedKinds.contains(opposingActive))) {
                invalidDiagnostics.add("contradictory-interpretation:"
                        + opposingActive.canonicalId() + "-flagged-when-explicitly-negated");
                continue;
            }

            if (ledger.stream().noneMatch(e -> e.identifier().equals(negatedKind.canonicalId()))
                    && isRepresentedInCanonical(negatedKind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds)) {
                ledger.add(new ConstraintCoverage(
                        negatedKind.canonicalId(),
                        origin,
                        ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                        assertedSupportedKinds.contains(negatedKind) || canonicalSupportedKinds.contains(negatedKind),
                        describeCanonicalRepresentation(negatedKind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds),
                        evidence,
                        "Explicitly neglected/idealized in request and satisfied by PHYSORA model"));
            }
        }

        // Catch silent omission of inclined plane when draft did not declare unresolved/inclined and canonical is horizontal
        boolean draftMentionsInclineOrUnresolved = draft != null
                && (draft.surface() instanceof InterpretationDraft.SurfaceSpec.Inclined
                || draft.surface() instanceof InterpretationDraft.SurfaceSpec.Unresolved
                || unresolvedExtractedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.INCLINED_LANDING_SURFACE)
                || !draft.clarifications().isEmpty());
        if (screening.inclinedSurfaceMentioned()
                && canonical.landingSurface().isHorizontal()
                && !draftMentionsInclineOrUnresolved) {
            invalidDiagnostics.add("incomplete-constraint-extraction:inclined-landing-surface");
            if (ledger.stream().noneMatch(e -> e.identifier().equals(
                    PhysicsCapabilityRegistry.ConstraintKind.INCLINED_LANDING_SURFACE.canonicalId()))) {
                ledger.add(new ConstraintCoverage(
                        PhysicsCapabilityRegistry.ConstraintKind.INCLINED_LANDING_SURFACE.canonicalId(),
                        InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION,
                        false,
                        "HorizontalPlane(y=" + canonical.landingSurface().anchorY() + " m)",
                        screening.inclinedSurfaceEvidence(),
                        "Inclined surface stated in request was omitted and defaulted to horizontal"));
            }
        }
    }

    private void detectAllContradictions(ScreeningResult screening,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> assertedSupportedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> assertedUnsupportedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> draftUnsupportedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> draftAssumptionNegatedKinds,
                                         Set<PhysicsCapabilityRegistry.ConstraintKind> draftAssumptionActiveKinds,
                                         Set<String> invalidDiagnostics) {
        Set<PhysicsCapabilityRegistry.ConstraintKind> allActiveUnsupported =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        allActiveUnsupported.addAll(assertedUnsupportedKinds);
        allActiveUnsupported.addAll(draftUnsupportedKinds);
        allActiveUnsupported.addAll(canonicalUnsupportedKinds);
        allActiveUnsupported.addAll(screening.activeUnsupported());
        allActiveUnsupported.addAll(draftAssumptionActiveKinds);

        Set<PhysicsCapabilityRegistry.ConstraintKind> allNegatedSupported =
                EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        allNegatedSupported.addAll(assertedSupportedKinds);
        allNegatedSupported.addAll(canonicalSupportedKinds);
        allNegatedSupported.addAll(screening.negatedSupported());
        allNegatedSupported.addAll(draftAssumptionNegatedKinds);

        for (OppositeConstraintPair pair : OPPOSITE_CONSTRAINT_PAIRS) {
            boolean studentBoth = screening.activeUnsupported().contains(pair.activeUnsupported())
                    && screening.negatedSupported().contains(pair.negatedSupported());
            if (studentBoth) {
                invalidDiagnostics.add("contradictory-student-conditions:" + pair.diagnosticSuffix());
            }

            boolean assumptionConflict = allActiveUnsupported.contains(pair.activeUnsupported())
                    && draftAssumptionNegatedKinds.contains(pair.negatedSupported());
            if (assumptionConflict) {
                invalidDiagnostics.add("contradictory-assumption:" + pair.activeUnsupported().canonicalId() + "-ignored");
            }

            if (allActiveUnsupported.contains(pair.activeUnsupported())
                    && allNegatedSupported.contains(pair.negatedSupported())) {
                invalidDiagnostics.add("contradictory-constraints:" + pair.diagnosticSuffix());
            }
        }
    }

    private static PhysicsCapabilityRegistry.ConstraintKind opposingActiveFor(
            PhysicsCapabilityRegistry.ConstraintKind negatedKind) {
        for (OppositeConstraintPair pair : OPPOSITE_CONSTRAINT_PAIRS) {
            if (pair.negatedSupported() == negatedKind) {
                return pair.activeUnsupported();
            }
        }
        return null;
    }

    private void recordRemainingCanonicalConstraints(CanonicalPhysicsProblem canonical,
                                                     PhysicsCapabilityRegistry.CapabilityEvaluation eval,
                                                     List<ConstraintCoverage> ledger) {
        Set<String> alreadyRecorded = new LinkedHashSet<>();
        for (ConstraintCoverage entry : ledger) {
            alreadyRecorded.add(entry.identifier());
        }
        Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupported = toKindEnumSet(eval.supportedConstraints());
        Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupported = toKindEnumSet(eval.unsupportedConstraints());

        for (PhysicsCapabilityRegistry.ConstraintKind kind : eval.supportedConstraints()) {
            if (alreadyRecorded.add(kind.canonicalId())) {
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        null,
                        ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                        false,
                        describeCanonicalRepresentation(kind, canonical, canonicalSupported, canonicalUnsupported),
                        null,
                        "Attached directly on canonical specification"));
            }
        }
        for (PhysicsCapabilityRegistry.ConstraintKind kind : eval.unsupportedConstraints()) {
            if (alreadyRecorded.add(kind.canonicalId())) {
                ledger.add(new ConstraintCoverage(
                        kind.canonicalId(),
                        null,
                        ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                        false,
                        "None",
                        null,
                        "Attached directly on canonical specification"));
            }
        }
        for (String unknown : eval.unknownConstraints()) {
            if (alreadyRecorded.add(unknown)) {
                ledger.add(new ConstraintCoverage(
                        unknown,
                        null,
                        ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                        false,
                        "None",
                        null,
                        "Unmapped constraint attached directly on canonical specification"));
            }
        }
        for (String unsupported : canonical.unsupportedConstraints()) {
            PhysicsCapabilityRegistry.ConstraintClassification cls = registry.classifyConstraint(unsupported);
            String id = cls.known() ? cls.canonicalId() : cls.rawToken();
            if (!id.isEmpty() && alreadyRecorded.add(id)) {
                ledger.add(new ConstraintCoverage(
                        id,
                        null,
                        ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                        false,
                        "None",
                        null,
                        "Canonical problem declared unsupported constraint"));
            }
        }
    }

    private static boolean isRepresentedInCanonical(
            PhysicsCapabilityRegistry.ConstraintKind kind,
            CanonicalPhysicsProblem canonical,
            Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds,
            Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds) {
        PhysicsCapabilityRegistry.ConstraintKind opposingActive = opposingActiveFor(kind);
        if (opposingActive != null && canonicalUnsupportedKinds.contains(opposingActive)) {
            return false;
        }
        return switch (kind) {
            case HORIZONTAL_LANDING_SURFACE -> canonical.landingSurface().isHorizontal()
                    && !canonicalUnsupportedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.CURVED_OR_PIECEWISE_SURFACE);
            case INCLINED_LANDING_SURFACE -> !canonical.landingSurface().isHorizontal()
                    && !canonicalUnsupportedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.CURVED_OR_PIECEWISE_SURFACE);
            case CONSTANT_DOWNWARD_GRAVITY -> !canonicalUnsupportedKinds.contains(
                    PhysicsCapabilityRegistry.ConstraintKind.NON_UNIFORM_GRAVITY)
                    && canonical.hasGravity()
                    && canonical.gravity().getAsDouble() > 0.0;
            case POINT_PARTICLE -> !canonicalUnsupportedKinds.contains(
                    PhysicsCapabilityRegistry.ConstraintKind.MULTIPLE_INTERACTING_BODIES)
                    && canonical.validationErrors().isEmpty();
            case TWO_DIMENSIONAL_MOTION -> canonical.validationErrors().isEmpty();
            case NO_AIR_RESISTANCE, NO_WIND, NO_SPIN, RIGID_NON_BOUNCING_CONTACT, STATIONARY_FRAME ->
                    canonicalSupportedKinds.contains(kind) || canonical.validationErrors().isEmpty();
            default -> canonicalSupportedKinds.contains(kind);
        };
    }

    private static String describeCanonicalRepresentation(
            PhysicsCapabilityRegistry.ConstraintKind kind,
            CanonicalPhysicsProblem canonical,
            Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalSupportedKinds,
            Set<PhysicsCapabilityRegistry.ConstraintKind> canonicalUnsupportedKinds) {
        if (!isRepresentedInCanonical(kind, canonical, canonicalSupportedKinds, canonicalUnsupportedKinds)) {
            return "None";
        }
        return switch (kind) {
            case HORIZONTAL_LANDING_SURFACE ->
                    "HorizontalPlane(y=" + canonical.landingSurface().anchorY() + " m)";
            case INCLINED_LANDING_SURFACE ->
                    "InclinedPlane(anchor=(" + canonical.landingSurface().anchorX() + ", "
                            + canonical.landingSurface().anchorY() + "), angle="
                            + canonical.landingSurface().angleDegrees() + "°)";
            case CONSTANT_DOWNWARD_GRAVITY ->
                    "Gravity=" + canonical.gravity().getAsDouble() + " m/s^2";
            default -> canonicalSupportedKinds.contains(kind)
                    ? "ConstraintMetadata(" + kind.canonicalId() + ")"
                    : "IdealizedModelAssumption(" + kind.canonicalId() + ")";
        };
    }

    private void partitionAssumptions(CanonicalPhysicsProblem canonical,
                                      InterpretationDraft draft,
                                      Set<PhysicsCapabilityRegistry.ConstraintKind> activeUnsupportedKinds,
                                      Set<String> modelAssumptions,
                                      Set<String> introducedDefaults) {
        if (!activeUnsupportedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.MULTIPLE_INTERACTING_BODIES)) {
            modelAssumptions.add("point-particle");
        }
        modelAssumptions.add("two-dimensional-motion");
        if (!activeUnsupportedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.NON_UNIFORM_GRAVITY)
                && canonical.hasGravity()) {
            modelAssumptions.add("constant-downward-gravity");
        }
        if (!activeUnsupportedKinds.contains(PhysicsCapabilityRegistry.ConstraintKind.AIR_RESISTANCE)) {
            modelAssumptions.add("air-resistance-ignored");
        }

        for (String assumption : canonical.assumptions()) {
            if (isIntroducedDefault(assumption)) {
                introducedDefaults.add(assumption);
            } else if (!assumptionContradictsActiveEffects(assumption, activeUnsupportedKinds)) {
                modelAssumptions.add(assumption);
            }
        }
        if (draft != null) {
            for (String assumption : draft.assumptions()) {
                if (assumption != null && !assumption.isBlank()) {
                    String trimmed = assumption.trim();
                    if (isIntroducedDefault(trimmed)) {
                        introducedDefaults.add(trimmed);
                    } else if (!assumptionContradictsActiveEffects(trimmed, activeUnsupportedKinds)) {
                        modelAssumptions.add(trimmed);
                    }
                }
            }
        }
    }

    private boolean assumptionContradictsActiveEffects(
            String rawAssumption,
            Set<PhysicsCapabilityRegistry.ConstraintKind> activeUnsupportedKinds) {
        if (rawAssumption == null || rawAssumption.isBlank()) {
            return false;
        }
        PhysicsCapabilityRegistry.ConstraintClassification direct = registry.classifyConstraint(rawAssumption);
        if (direct.known() && direct.supported()) {
            PhysicsCapabilityRegistry.ConstraintKind active = opposingActiveFor(direct.kind());
            if (active != null && activeUnsupportedKinds.contains(active)) {
                return true;
            }
        }
        for (DetectedCue cue : scanTextForCues(rawAssumption, InterpretationDraft.ConstraintOrigin.INTERPRETER_INFERRED)) {
            if (cue.negated() && cue.activeKind() != null && activeUnsupportedKinds.contains(cue.activeKind())) {
                return true;
            }
        }
        return false;
    }

    private static Double parseGravityFromAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        Matcher matcher = GRAVITY_VALUE_PATTERN.matcher(answer);
        if (!matcher.find()) {
            return null;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.isBlank()) {
            return value;
        }
        String normalizedUnit = unit.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalizedUnit.equals("ft/s^2") || normalizedUnit.equals("ft/s2")) {
            return value * 0.3048;
        }
        if (normalizedUnit.equals("cm/s^2") || normalizedUnit.equals("cm/s2")) {
            return value * 0.01;
        }
        return value;
    }

    private static Double extractFirstNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)").matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    private static boolean requiresSourceEvidence(InterpretationDraft.ConstraintOrigin origin) {
        return origin == InterpretationDraft.ConstraintOrigin.STUDENT_EXPLICIT
                || origin == InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER;
    }

    private static boolean hasMeaningfulEvidence(String sourceEvidence) {
        if (sourceEvidence == null || sourceEvidence.isBlank()) {
            return false;
        }
        for (int i = 0; i < sourceEvidence.length(); i++) {
            if (Character.isLetterOrDigit(sourceEvidence.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void recordOriginBucket(InterpretationDraft.ExtractedConstraint constraint,
                                           Set<String> explicitStudentConditions,
                                           Set<String> clarificationDerivedConditions,
                                           Set<String> interpreterInferredConditions) {
        String formatted = formatStudentCondition(constraint);
        switch (constraint.origin()) {
            case STUDENT_EXPLICIT -> explicitStudentConditions.add(formatted);
            case CLARIFICATION_ANSWER -> clarificationDerivedConditions.add(formatted);
            case INTERPRETER_INFERRED -> interpreterInferredConditions.add(formatted);
        }
    }

    private static void addConditionByOrigin(InterpretationDraft.ConstraintOrigin origin,
                                             String identifier,
                                             String evidence,
                                             Set<String> explicitStudentConditions,
                                             Set<String> clarificationDerivedConditions) {
        String formatted = (evidence == null || evidence.isBlank())
                ? identifier
                : identifier + " (" + evidence + ")";
        if (origin == InterpretationDraft.ConstraintOrigin.CLARIFICATION_ANSWER) {
            clarificationDerivedConditions.add(formatted);
        } else {
            explicitStudentConditions.add(formatted);
        }
    }

    private static Set<PhysicsCapabilityRegistry.ConstraintKind> toKindEnumSet(
            List<PhysicsCapabilityRegistry.ConstraintKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return EnumSet.noneOf(PhysicsCapabilityRegistry.ConstraintKind.class);
        }
        return EnumSet.copyOf(kinds);
    }

    private static boolean isIntroducedDefault(String assumption) {
        if (assumption == null) return false;
        return assumption.contains("-defaulted-")
                || assumption.equals("launch-angle-normalized-from-incline-surface-to-horizontal");
    }

    private static String formatStudentCondition(InterpretationDraft.ExtractedConstraint constraint) {
        if (constraint.sourceEvidence() == null || constraint.sourceEvidence().isBlank()) {
            return constraint.identifier();
        }
        return constraint.identifier() + " (" + constraint.sourceEvidence() + ")";
    }

    private static boolean nearlyEqual(double left, double right) {
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= EQUIVALENCE_REL_TOL * scale;
    }
}
