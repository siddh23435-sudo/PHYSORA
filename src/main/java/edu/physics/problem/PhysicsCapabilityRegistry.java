package edu.physics.problem;

import edu.physics.projectile.LandingSurface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit, deterministic registry of implemented physics-engine capabilities.
 *
 * <p>Capability support is defined strictly by the verified physics solver and
 * {@link edu.physics.projectile.ProjectileMotion} contracts—never by AI assertions
 * or target enum existence alone. Evaluates combinations of landing geometry,
 * requested targets, physical constraints, and available inputs while preserving
 * every finding across categories.
 */
public final class PhysicsCapabilityRegistry {

    public enum SupportStatus {
        SUPPORTED,
        INSUFFICIENT_INFORMATION,
        UNSUPPORTED,
        INVALID_INPUT
    }

    public enum SurfaceKind {
        HORIZONTAL_PLANE,
        INCLINED_PLANE
    }

    /** Known canonical physical constraints—both supported model assumptions and recognized unsupported effects. */
    public enum ConstraintKind {
        POINT_PARTICLE(true, "point-particle"),
        TWO_DIMENSIONAL_MOTION(true, "two-dimensional-motion"),
        CONSTANT_DOWNWARD_GRAVITY(true, "constant-downward-gravity"),
        NO_AIR_RESISTANCE(true, "air-resistance-ignored"),
        NO_WIND(true, "no-wind"),
        NO_SPIN(true, "no-spin"),
        RIGID_NON_BOUNCING_CONTACT(true, "rigid-non-bouncing-contact"),
        STATIONARY_FRAME(true, "stationary-inertial-frame"),
        HORIZONTAL_LANDING_SURFACE(true, "horizontal-landing-surface"),
        INCLINED_LANDING_SURFACE(true, "inclined-landing-surface"),

        AIR_RESISTANCE(false, "air-resistance"),
        WIND(false, "wind"),
        MAGNUS_OR_SPIN(false, "spin"),
        BOUNCE_OR_RESTITUTION(false, "bounce-restitution"),
        NON_UNIFORM_GRAVITY(false, "variable-gravity"),
        MOVING_REFERENCE_OR_TARGET(false, "moving-target"),
        CURVED_OR_PIECEWISE_SURFACE(false, "curved-surface"),
        SURFACE_FRICTION_OR_SLIDING(false, "sliding-friction"),
        MULTIPLE_INTERACTING_BODIES(false, "multiple-interacting-bodies");

        private final boolean supportedByEngine;
        private final String canonicalId;

        ConstraintKind(boolean supportedByEngine, String canonicalId) {
            this.supportedByEngine = supportedByEngine;
            this.canonicalId = canonicalId;
        }

        public boolean isSupportedByEngine() {
            return supportedByEngine;
        }

        public String canonicalId() {
            return canonicalId;
        }
    }

    /** Declarative metadata for one implemented engine capability. */
    public record CapabilityDescriptor(
            String id,
            String physicalModel,
            Set<SurfaceKind> supportedSurfaces,
            Set<CanonicalPhysicsProblem.CanonicalTarget> supportedTargets,
            Set<ProjectileProblem.Input> sharedRequiredInputs,
            Set<ConstraintKind> requiredConstraints,
            List<String> restrictions) {

        public CapabilityDescriptor {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Capability id is required");
            if (physicalModel == null || physicalModel.isBlank()) throw new IllegalArgumentException("Physical model description is required");
            supportedSurfaces = Set.copyOf(supportedSurfaces);
            supportedTargets = Set.copyOf(supportedTargets);
            sharedRequiredInputs = Set.copyOf(sharedRequiredInputs);
            requiredConstraints = Set.copyOf(requiredConstraints);
            restrictions = List.copyOf(restrictions);
        }
    }

    /** Per-target deterministic capability evaluation outcome. */
    public record TargetAssessment(
            int partIndex,
            CanonicalPhysicsProblem.CanonicalTarget target,
            SupportStatus status,
            List<String> matchedCapabilityIds,
            List<String> missingInputs,
            List<String> unsupportedReasons,
            List<String> validationErrors) {

        public TargetAssessment {
            if (partIndex < 0) throw new IllegalArgumentException("Part index must be non-negative");
            Objects.requireNonNull(target, "Canonical target is required");
            Objects.requireNonNull(status, "Target support status is required");
            matchedCapabilityIds = List.copyOf(matchedCapabilityIds);
            missingInputs = List.copyOf(missingInputs);
            unsupportedReasons = List.copyOf(unsupportedReasons);
            validationErrors = List.copyOf(validationErrors);
        }
    }

    /** Full diagnostic report preserving all matched, missing, unsupported, unknown, and invalid findings. */
    public record CapabilityEvaluation(
            SupportStatus status,
            List<String> matchedCapabilityIds,
            List<TargetAssessment> targetAssessments,
            List<String> missingInputs,
            List<ConstraintKind> supportedConstraints,
            List<ConstraintKind> unsupportedConstraints,
            List<String> unknownConstraints,
            List<String> unsupportedReasons,
            List<String> invalidDiagnostics) {

        public CapabilityEvaluation {
            Objects.requireNonNull(status, "Support status is required");
            matchedCapabilityIds = List.copyOf(matchedCapabilityIds);
            targetAssessments = List.copyOf(targetAssessments);
            missingInputs = List.copyOf(missingInputs);
            supportedConstraints = List.copyOf(supportedConstraints);
            unsupportedConstraints = List.copyOf(unsupportedConstraints);
            unknownConstraints = List.copyOf(unknownConstraints);
            unsupportedReasons = List.copyOf(unsupportedReasons);
            invalidDiagnostics = List.copyOf(invalidDiagnostics);
        }

        public boolean isSupported() {
            return status == SupportStatus.SUPPORTED;
        }
    }

    /** Deterministic classification of one constraint string against registered capabilities. */
    public record ConstraintClassification(
            String rawToken,
            String canonicalId,
            ConstraintKind kind,
            boolean known,
            boolean supported) {

        public boolean isBlank() {
            return rawToken == null || rawToken.isBlank();
        }
    }

    public static final String CAPABILITY_PROJECTILE_2D_KINEMATICS = "PROJECTILE_2D_KINEMATICS";
    public static final String CAPABILITY_HORIZONTAL_LANDING_SURFACE = "HORIZONTAL_LANDING_SURFACE";
    public static final String CAPABILITY_INCLINED_LANDING_SURFACE = "INCLINED_LANDING_SURFACE";
    public static final String CAPABILITY_INVERSE_PROJECTILE_PARAMETERS = "INVERSE_PROJECTILE_PARAMETERS";

    private static final Map<String, ConstraintKind> KNOWN_CONSTRAINT_ALIASES = buildConstraintAliases();

    private final List<CapabilityDescriptor> descriptors;
    private final Map<String, CapabilityDescriptor> descriptorsById;

    public PhysicsCapabilityRegistry() {
        List<CapabilityDescriptor> built = buildDefaultCapabilities();
        this.descriptors = List.copyOf(built);
        Map<String, CapabilityDescriptor> index = new LinkedHashMap<>();
        for (CapabilityDescriptor descriptor : built) {
            if (index.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalStateException("Duplicate capability id: " + descriptor.id());
            }
        }
        this.descriptorsById = CollectionsUnmodifiableLinkedMap(index);
    }

    public List<CapabilityDescriptor> capabilities() {
        return descriptors;
    }

    public CapabilityDescriptor capability(String id) {
        return descriptorsById.get(id);
    }

    /** Classifies one constraint or synonym token deterministically without synthetic probe problems. */
    public ConstraintClassification classifyConstraint(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ConstraintClassification("", "", null, false, false);
        }
        String trimmed = raw.trim();
        String normalized = normalizeToken(trimmed);
        ConstraintKind mapped = KNOWN_CONSTRAINT_ALIASES.get(normalized);
        if (mapped == null) {
            return new ConstraintClassification(trimmed, normalized, null, false, false);
        }
        return new ConstraintClassification(trimmed, mapped.canonicalId(), mapped, true, mapped.isSupportedByEngine());
    }

    /** Evaluates a canonical problem using only its declared constraints and solver-truth capability rules. */
    public CapabilityEvaluation evaluate(CanonicalPhysicsProblem problem) {
        return evaluate(problem, Set.of());
    }

    /** Evaluates a canonical problem along with explicit typed constraints extracted upstream. */
    public CapabilityEvaluation evaluate(CanonicalPhysicsProblem problem,
                                         Collection<ConstraintKind> explicitConstraints) {
        if (problem == null) {
            return new CapabilityEvaluation(
                    SupportStatus.INVALID_INPUT,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("canonical-problem-required"));
        }

        Set<String> invalidDiagnostics = new LinkedHashSet<>(problem.validationErrors());
        Set<String> matchedCapabilities = new LinkedHashSet<>();
        Set<String> allMissingInputs = new LinkedHashSet<>();
        Set<ConstraintKind> supportedConstraints = EnumSet.noneOf(ConstraintKind.class);
        Set<ConstraintKind> unsupportedConstraints = EnumSet.noneOf(ConstraintKind.class);
        Set<String> unknownConstraints = new LinkedHashSet<>();
        Set<String> unsupportedReasons = new LinkedHashSet<>();

        // 1. Classify explicit typed constraints
        if (explicitConstraints != null) {
            for (ConstraintKind kind : explicitConstraints) {
                if (kind == null) continue;
                if (kind.isSupportedByEngine()) {
                    supportedConstraints.add(kind);
                } else {
                    unsupportedConstraints.add(kind);
                    unsupportedReasons.add("unsupported-constraint:" + kind.canonicalId());
                }
            }
        }

        // 2. Classify structured string constraints attached to canonical problem without keyword heuristics
        for (String raw : problem.constraints()) {
            classifyConstraintToken(raw, supportedConstraints, unsupportedConstraints, unknownConstraints, unsupportedReasons);
        }
        for (String rawUnsupported : problem.unsupportedConstraints()) {
            ConstraintClassification classification = classifyConstraint(rawUnsupported);
            if (classification.isBlank()) {
                continue;
            }
            if (!classification.known()) {
                unknownConstraints.add(classification.rawToken());
            } else if (!classification.supported()) {
                unsupportedConstraints.add(classification.kind());
                unsupportedReasons.add("unsupported-constraint:" + classification.canonicalId());
            } else {
                invalidDiagnostics.add("supported-constraint-marked-unsupported:" + classification.canonicalId());
            }
        }

        SurfaceKind surfaceKind = problem.landingSurface().isHorizontal()
                ? SurfaceKind.HORIZONTAL_PLANE
                : SurfaceKind.INCLINED_PLANE;

        // 3. Detect explicit contradiction between surface constraint and landingSurface geometry
        if (supportedConstraints.contains(ConstraintKind.HORIZONTAL_LANDING_SURFACE)
                && surfaceKind == SurfaceKind.INCLINED_PLANE) {
            invalidDiagnostics.add("constraint-contradicts-landing-surface:expected-horizontal-got-inclined");
        }
        if (supportedConstraints.contains(ConstraintKind.INCLINED_LANDING_SURFACE)
                && surfaceKind == SurfaceKind.HORIZONTAL_PLANE) {
            invalidDiagnostics.add("constraint-contradicts-landing-surface:expected-inclined-got-horizontal");
        }

        // Unknown constraints prevent asserting capability support
        for (String unknown : unknownConstraints) {
            unsupportedReasons.add("unknown-unmapped-constraint:" + unknown);
        }

        // 4. Evaluate each requested target part independently against real solver capabilities
        List<TargetAssessment> assessments = new ArrayList<>(problem.parts().size());
        boolean anyTargetUnsupported = false;
        boolean anyTargetMissingInfo = false;
        boolean anyTargetInvalid = false;

        for (CanonicalPhysicsProblem.PartSpec part : problem.parts()) {
            TargetAssessment assessment = assessPart(problem, part, surfaceKind);
            assessments.add(assessment);
            matchedCapabilities.addAll(assessment.matchedCapabilityIds());
            allMissingInputs.addAll(assessment.missingInputs());
            unsupportedReasons.addAll(assessment.unsupportedReasons());
            invalidDiagnostics.addAll(assessment.validationErrors());

            if (assessment.status() == SupportStatus.INVALID_INPUT) anyTargetInvalid = true;
            if (assessment.status() == SupportStatus.UNSUPPORTED) anyTargetUnsupported = true;
            if (assessment.status() == SupportStatus.INSUFFICIENT_INFORMATION) anyTargetMissingInfo = true;
        }

        // Preserve all findings; determine overall SupportStatus strictly by precedence:
        // INVALID_INPUT > UNSUPPORTED > INSUFFICIENT_INFORMATION > SUPPORTED
        SupportStatus overall;
        if (!invalidDiagnostics.isEmpty() || anyTargetInvalid) {
            overall = SupportStatus.INVALID_INPUT;
        } else if (!unsupportedConstraints.isEmpty()
                || !unknownConstraints.isEmpty()
                || !unsupportedReasons.isEmpty()
                || anyTargetUnsupported) {
            overall = SupportStatus.UNSUPPORTED;
        } else if (!allMissingInputs.isEmpty() || anyTargetMissingInfo) {
            overall = SupportStatus.INSUFFICIENT_INFORMATION;
        } else {
            overall = SupportStatus.SUPPORTED;
        }

        return new CapabilityEvaluation(
                overall,
                List.copyOf(matchedCapabilities),
                assessments,
                List.copyOf(allMissingInputs),
                List.copyOf(supportedConstraints),
                List.copyOf(unsupportedConstraints),
                List.copyOf(unknownConstraints),
                List.copyOf(unsupportedReasons),
                List.copyOf(invalidDiagnostics));
    }

    private TargetAssessment assessPart(CanonicalPhysicsProblem problem,
                                        CanonicalPhysicsProblem.PartSpec part,
                                        SurfaceKind surfaceKind) {
        CanonicalPhysicsProblem.CanonicalTarget target = part.target();
        List<String> partMatched = new ArrayList<>();
        List<String> partMissing = new ArrayList<>();
        List<String> partUnsupported = new ArrayList<>();
        List<String> partInvalid = new ArrayList<>();

        validatePartParameters(part.parameters(), partInvalid);
        validatePartReferences(part, partInvalid);

        if (target instanceof CanonicalPhysicsProblem.StandardTarget standardTarget) {
            ProjectileProblem.Target std = standardTarget.target();
            if (std == ProjectileProblem.Target.FIND_INITIAL_SPEED
                    || std == ProjectileProblem.Target.FIND_LAUNCH_ANGLE) {
                assessInverseTarget(problem, part, std, surfaceKind,
                        partMatched, partMissing, partUnsupported);
            } else {
                assessStandardForwardTarget(problem, part, std, surfaceKind,
                        partMatched, partMissing, partUnsupported);
            }
        } else if (target instanceof CanonicalPhysicsProblem.SurfaceTarget surfaceTarget) {
            assessSurfaceTarget(problem, part, surfaceTarget, surfaceKind, partMatched, partMissing, partUnsupported);
        }

        SupportStatus status;
        if (!partInvalid.isEmpty() || !problem.validationErrors().isEmpty()) {
            status = SupportStatus.INVALID_INPUT;
        } else if (!partUnsupported.isEmpty()) {
            status = SupportStatus.UNSUPPORTED;
        } else if (!partMissing.isEmpty()) {
            status = SupportStatus.INSUFFICIENT_INFORMATION;
        } else {
            status = SupportStatus.SUPPORTED;
        }

        return new TargetAssessment(
                part.index(),
                target,
                status,
                partMatched,
                partMissing.stream().distinct().toList(),
                partUnsupported,
                partInvalid);
    }

    private void assessStandardForwardTarget(CanonicalPhysicsProblem problem,
                                             CanonicalPhysicsProblem.PartSpec part,
                                             ProjectileProblem.Target target,
                                             SurfaceKind surfaceKind,
                                             List<String> matched,
                                             List<String> missing,
                                             List<String> unsupported) {
        if (target == ProjectileProblem.Target.FLIGHT_TIME
                || target == ProjectileProblem.Target.HORIZONTAL_RANGE) {
            String surfaceCapId = surfaceCapabilityIdFor(surfaceKind);
            CapabilityDescriptor surfaceCap = descriptorsById.get(surfaceCapId);
            if (surfaceCap != null && surfaceCap.supportedTargets().contains(part.target())) {
                matched.add(surfaceCapId);
            } else {
                unsupported.add("target-not-supported-on-surface:" + target.name() + ":" + surfaceKind.name());
            }
        } else {
            CapabilityDescriptor kinematics = descriptorsById.get(CAPABILITY_PROJECTILE_2D_KINEMATICS);
            if (kinematics != null && kinematics.supportedTargets().contains(part.target())) {
                matched.add(CAPABILITY_PROJECTILE_2D_KINEMATICS);
            } else {
                unsupported.add("target-not-supported:" + target.name());
            }
        }

        collectMissingSharedForwardInputs(problem, part, missing);
        for (ProjectileProblem.Input req : requiredPartInputsFor(target)) {
            if (!part.parameters().containsKey(req) && !part.references().containsKey(req)) {
                missing.add(req.name());
            }
        }
    }

    private void assessSurfaceTarget(CanonicalPhysicsProblem problem,
                                     CanonicalPhysicsProblem.PartSpec part,
                                     CanonicalPhysicsProblem.SurfaceTarget target,
                                     SurfaceKind surfaceKind,
                                     List<String> matched,
                                     List<String> missing,
                                     List<String> unsupported) {
        String capId = surfaceCapabilityIdFor(surfaceKind);
        CapabilityDescriptor descriptor = descriptorsById.get(capId);
        if (descriptor != null && descriptor.supportedTargets().contains(target)) {
            matched.add(capId);
        } else {
            unsupported.add("surface-target-not-supported:" + target.name() + ":" + surfaceKind.name());
        }
        collectMissingSharedForwardInputs(problem, part, missing);
    }

    private void assessInverseTarget(CanonicalPhysicsProblem problem,
                                     CanonicalPhysicsProblem.PartSpec part,
                                     ProjectileProblem.Target inverseTarget,
                                     SurfaceKind surfaceKind,
                                     List<String> matched,
                                     List<String> missing,
                                     List<String> unsupported) {
        Map<ProjectileProblem.Input, PhysicalQuantity> params = part.parameters();
        Map<ProjectileProblem.Input, ProjectileProblemSequence.ResultReference> refs = part.references();

        // Exact solver truth from ProjectileProblemSolver.solveCanonicalInversePart:
        // An inverse query on an inclined surface that supplies HORIZONTAL_DISPLACEMENT
        // without VERTICAL_DISPLACEMENT requires inclined-surface intersection, which is unsupported.
        boolean hasDx = params.containsKey(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT)
                || refs.containsKey(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT);
        boolean hasDy = params.containsKey(ProjectileProblem.Input.VERTICAL_DISPLACEMENT)
                || refs.containsKey(ProjectileProblem.Input.VERTICAL_DISPLACEMENT);
        if (surfaceKind == SurfaceKind.INCLINED_PLANE && hasDx && !hasDy) {
            unsupported.add("inverse-target-requires-inclined-surface-intersection:" + inverseTarget.name());
        } else {
            matched.add(CAPABILITY_INVERSE_PROJECTILE_PARAMETERS);
        }

        if (!problem.hasGravity()
                && !params.containsKey(ProjectileProblem.Input.GRAVITY)
                && !refs.containsKey(ProjectileProblem.Input.GRAVITY)) {
            missing.add(ProjectileProblem.Input.GRAVITY.name());
        }

        CanonicalPhysicsProblem.VelocityInputMetadata meta = problem.velocityMetadata();
        if (inverseTarget == ProjectileProblem.Target.FIND_INITIAL_SPEED) {
            boolean hasAngle = problem.derivedLaunchAngleDegrees().isPresent()
                    || (meta.submittedAngle() != null
                    && meta.submittedAngle().unit().dimension() == PhysicalUnit.Dimension.ANGLE
                    && Double.isFinite(meta.submittedAngle().valueInSi()))
                    || params.containsKey(ProjectileProblem.Input.LAUNCH_ANGLE)
                    || refs.containsKey(ProjectileProblem.Input.LAUNCH_ANGLE);
            if (!hasAngle) {
                missing.add(ProjectileProblem.Input.LAUNCH_ANGLE.name());
            }
            boolean hasCondition = params.containsKey(ProjectileProblem.Input.TARGET_VX)
                    || refs.containsKey(ProjectileProblem.Input.TARGET_VX)
                    || ((params.containsKey(ProjectileProblem.Input.TIME) || refs.containsKey(ProjectileProblem.Input.TIME))
                    && (params.containsKey(ProjectileProblem.Input.TARGET_VY) || refs.containsKey(ProjectileProblem.Input.TARGET_VY)
                    || params.containsKey(ProjectileProblem.Input.TARGET_SPEED) || refs.containsKey(ProjectileProblem.Input.TARGET_SPEED)))
                    || (hasDx && hasDy);
            if (!hasCondition && unsupported.isEmpty()) {
                missing.add("INVERSE_SPEED_CONDITION");
            }
        } else {
            boolean hasSpeed = problem.derivedInitialSpeed().isPresent()
                    || (meta.submittedSpeed() != null
                    && meta.submittedSpeed().unit().dimension() == PhysicalUnit.Dimension.SPEED
                    && Double.isFinite(meta.submittedSpeed().valueInSi()))
                    || params.containsKey(ProjectileProblem.Input.INITIAL_SPEED)
                    || refs.containsKey(ProjectileProblem.Input.INITIAL_SPEED);
            if (!hasSpeed) {
                missing.add(ProjectileProblem.Input.INITIAL_SPEED.name());
            }
            if (!hasDx) {
                missing.add(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT.name());
            }
            if (!hasDy && unsupported.isEmpty()) {
                missing.add(ProjectileProblem.Input.VERTICAL_DISPLACEMENT.name());
            }
        }
    }

    private static void collectMissingSharedForwardInputs(CanonicalPhysicsProblem problem,
                                                          CanonicalPhysicsProblem.PartSpec part,
                                                          List<String> missing) {
        if (!problem.hasVelocity()) {
            CanonicalPhysicsProblem.VelocityInputMetadata meta = problem.velocityMetadata();
            boolean hasSpeed = problem.derivedInitialSpeed().isPresent()
                    || (meta.submittedSpeed() != null
                    && meta.submittedSpeed().unit().dimension() == PhysicalUnit.Dimension.SPEED
                    && Double.isFinite(meta.submittedSpeed().valueInSi()))
                    || part.parameters().containsKey(ProjectileProblem.Input.INITIAL_SPEED)
                    || part.references().containsKey(ProjectileProblem.Input.INITIAL_SPEED);
            boolean hasAngle = problem.derivedLaunchAngleDegrees().isPresent()
                    || (meta.submittedAngle() != null
                    && meta.submittedAngle().unit().dimension() == PhysicalUnit.Dimension.ANGLE
                    && Double.isFinite(meta.submittedAngle().valueInSi()))
                    || part.parameters().containsKey(ProjectileProblem.Input.LAUNCH_ANGLE)
                    || part.references().containsKey(ProjectileProblem.Input.LAUNCH_ANGLE);
            if (!hasSpeed) {
                missing.add(ProjectileProblem.Input.INITIAL_SPEED.name());
            }
            if (!hasAngle) {
                missing.add(ProjectileProblem.Input.LAUNCH_ANGLE.name());
            }
        }
        if (!problem.hasGravity()
                && !part.parameters().containsKey(ProjectileProblem.Input.GRAVITY)
                && !part.references().containsKey(ProjectileProblem.Input.GRAVITY)) {
            missing.add(ProjectileProblem.Input.GRAVITY.name());
        }
    }

    private static void validatePartReferences(CanonicalPhysicsProblem.PartSpec part, List<String> partErrors) {
        part.references().forEach((input, ref) -> {
            if (input == null || ref == null) {
                partErrors.add("null-input-quantity");
            } else if (ref.quantityName() == null || ref.quantityName().isBlank()) {
                partErrors.add("invalid-dependency-reference:" + input.name());
            } else if (ref.partIndex() < 0 || ref.partIndex() >= part.index()) {
                partErrors.add("invalid-dependency-reference:" + input.name());
            } else if (part.parameters().containsKey(input)) {
                partErrors.add("conflicting-input-definition:" + input.name());
            }
        });
    }

    private static void validatePartParameters(Map<ProjectileProblem.Input, PhysicalQuantity> parameters,
                                               List<String> partErrors) {
        parameters.forEach((input, quantity) -> {
            if (input == null || quantity == null) {
                partErrors.add("null-input-quantity");
            } else if (input.dimension() != quantity.unit().dimension()) {
                partErrors.add("incompatible-unit:" + input.name());
            } else if (!Double.isFinite(quantity.value())) {
                partErrors.add("non-finite-input:" + input.name());
            } else if ((input == ProjectileProblem.Input.TIME || input == ProjectileProblem.Input.TARGET_SPEED)
                    && quantity.value() < 0.0) {
                partErrors.add("negative-nonnegative-quantity:" + input.name());
            } else if (!Double.isFinite(quantity.valueInSi())) {
                partErrors.add("unit-conversion-overflow:" + input.name());
            }
        });
    }

    private static List<ProjectileProblem.Input> requiredPartInputsFor(ProjectileProblem.Target target) {
        return switch (target) {
            case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME ->
                    List.of(ProjectileProblem.Input.TIME);
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                    SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT ->
                    List.of(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT);
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT ->
                    List.of(ProjectileProblem.Input.TARGET_Y);
            case TIMES_AT_POSITION ->
                    List.of(ProjectileProblem.Input.TARGET_X, ProjectileProblem.Input.TARGET_Y);
            default -> List.of();
        };
    }

    private static String surfaceCapabilityIdFor(SurfaceKind kind) {
        return kind == SurfaceKind.HORIZONTAL_PLANE
                ? CAPABILITY_HORIZONTAL_LANDING_SURFACE
                : CAPABILITY_INCLINED_LANDING_SURFACE;
    }

    private void classifyConstraintToken(String raw,
                                         Set<ConstraintKind> supported,
                                         Set<ConstraintKind> unsupported,
                                         Set<String> unknown,
                                         Set<String> unsupportedReasons) {
        ConstraintClassification classification = classifyConstraint(raw);
        if (classification.isBlank()) {
            return;
        }
        if (!classification.known()) {
            unknown.add(classification.rawToken());
            return;
        }
        if (classification.supported()) {
            supported.add(classification.kind());
        } else {
            unsupported.add(classification.kind());
            unsupportedReasons.add("unsupported-constraint:" + classification.canonicalId());
        }
    }

    private static String normalizeToken(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceAll("\\s+", "-");
    }

    private static Map<String, ConstraintKind> buildConstraintAliases() {
        Map<String, ConstraintKind> map = new LinkedHashMap<>();
        for (ConstraintKind kind : ConstraintKind.values()) {
            map.put(kind.canonicalId(), kind);
            map.put(kind.name().toLowerCase(Locale.ROOT).replace('_', '-'), kind);
        }
        map.put("no-air-resistance", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("vacuum", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("neglect-air-resistance", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("ignore-air-resistance", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("no-drag", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("neglect-drag", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("zero-drag", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("without-air-resistance", ConstraintKind.NO_AIR_RESISTANCE);
        map.put("without-drag", ConstraintKind.NO_AIR_RESISTANCE);

        map.put("wind-ignored", ConstraintKind.NO_WIND);
        map.put("neglect-wind", ConstraintKind.NO_WIND);
        map.put("without-wind", ConstraintKind.NO_WIND);
        map.put("zero-wind", ConstraintKind.NO_WIND);

        map.put("spin-ignored", ConstraintKind.NO_SPIN);
        map.put("zero-spin", ConstraintKind.NO_SPIN);
        map.put("without-spin", ConstraintKind.NO_SPIN);
        map.put("no-magnus", ConstraintKind.NO_SPIN);

        map.put("constant-gravity", ConstraintKind.CONSTANT_DOWNWARD_GRAVITY);
        map.put("uniform-gravity", ConstraintKind.CONSTANT_DOWNWARD_GRAVITY);
        map.put("flat-ground", ConstraintKind.HORIZONTAL_LANDING_SURFACE);
        map.put("horizontal-ground", ConstraintKind.HORIZONTAL_LANDING_SURFACE);
        map.put("level-ground", ConstraintKind.HORIZONTAL_LANDING_SURFACE);
        map.put("inclined-plane", ConstraintKind.INCLINED_LANDING_SURFACE);
        map.put("incline", ConstraintKind.INCLINED_LANDING_SURFACE);
        map.put("no-bounce", ConstraintKind.RIGID_NON_BOUNCING_CONTACT);
        map.put("non-bouncing", ConstraintKind.RIGID_NON_BOUNCING_CONTACT);
        map.put("no-rebound", ConstraintKind.RIGID_NON_BOUNCING_CONTACT);
        map.put("stationary-frame", ConstraintKind.STATIONARY_FRAME);
        map.put("inertial-frame", ConstraintKind.STATIONARY_FRAME);

        map.put("drag", ConstraintKind.AIR_RESISTANCE);
        map.put("air-drag", ConstraintKind.AIR_RESISTANCE);
        map.put("aerodynamic-drag", ConstraintKind.AIR_RESISTANCE);
        map.put("linear-drag", ConstraintKind.AIR_RESISTANCE);
        map.put("quadratic-drag", ConstraintKind.AIR_RESISTANCE);
        map.put("terminal-velocity", ConstraintKind.AIR_RESISTANCE);
        map.put("viscous-damping", ConstraintKind.AIR_RESISTANCE);
        map.put("viscous-drag", ConstraintKind.AIR_RESISTANCE);
        map.put("fluid-resistance", ConstraintKind.AIR_RESISTANCE);

        map.put("crosswind", ConstraintKind.WIND);
        map.put("headwind", ConstraintKind.WIND);
        map.put("tailwind", ConstraintKind.WIND);

        map.put("magnus-effect", ConstraintKind.MAGNUS_OR_SPIN);
        map.put("magnus", ConstraintKind.MAGNUS_OR_SPIN);
        map.put("magnus-force", ConstraintKind.MAGNUS_OR_SPIN);
        map.put("topspin", ConstraintKind.MAGNUS_OR_SPIN);
        map.put("backspin", ConstraintKind.MAGNUS_OR_SPIN);

        map.put("bounce", ConstraintKind.BOUNCE_OR_RESTITUTION);
        map.put("restitution", ConstraintKind.BOUNCE_OR_RESTITUTION);
        map.put("rebound", ConstraintKind.BOUNCE_OR_RESTITUTION);
        map.put("coefficient-of-restitution", ConstraintKind.BOUNCE_OR_RESTITUTION);
        map.put("elastic-collision", ConstraintKind.BOUNCE_OR_RESTITUTION);
        map.put("inelastic-collision", ConstraintKind.BOUNCE_OR_RESTITUTION);

        map.put("orbital-gravity", ConstraintKind.NON_UNIFORM_GRAVITY);
        map.put("altitude-gravity", ConstraintKind.NON_UNIFORM_GRAVITY);
        map.put("non-uniform-gravity", ConstraintKind.NON_UNIFORM_GRAVITY);
        map.put("varying-gravity", ConstraintKind.NON_UNIFORM_GRAVITY);

        map.put("moving-frame", ConstraintKind.MOVING_REFERENCE_OR_TARGET);
        map.put("moving-platform", ConstraintKind.MOVING_REFERENCE_OR_TARGET);
        map.put("elevator", ConstraintKind.MOVING_REFERENCE_OR_TARGET);
        map.put("accelerating-frame", ConstraintKind.MOVING_REFERENCE_OR_TARGET);

        map.put("curved-terrain", ConstraintKind.CURVED_OR_PIECEWISE_SURFACE);
        map.put("piecewise-surface", ConstraintKind.CURVED_OR_PIECEWISE_SURFACE);
        map.put("stepped-surface", ConstraintKind.CURVED_OR_PIECEWISE_SURFACE);

        map.put("incline-sliding", ConstraintKind.SURFACE_FRICTION_OR_SLIDING);
        map.put("friction", ConstraintKind.SURFACE_FRICTION_OR_SLIDING);
        map.put("rolling", ConstraintKind.SURFACE_FRICTION_OR_SLIDING);
        map.put("sliding", ConstraintKind.SURFACE_FRICTION_OR_SLIDING);

        map.put("multiple-bodies", ConstraintKind.MULTIPLE_INTERACTING_BODIES);
        map.put("two-bodies", ConstraintKind.MULTIPLE_INTERACTING_BODIES);
        map.put("two-projectiles", ConstraintKind.MULTIPLE_INTERACTING_BODIES);
        map.put("mid-air-collision", ConstraintKind.MULTIPLE_INTERACTING_BODIES);
        return java.util.Collections.unmodifiableMap(map);
    }

    private static List<CapabilityDescriptor> buildDefaultCapabilities() {
        Set<ConstraintKind> baseIdealConstraints = EnumSet.of(
                ConstraintKind.POINT_PARTICLE,
                ConstraintKind.TWO_DIMENSIONAL_MOTION,
                ConstraintKind.CONSTANT_DOWNWARD_GRAVITY,
                ConstraintKind.NO_AIR_RESISTANCE,
                ConstraintKind.NO_WIND,
                ConstraintKind.NO_SPIN,
                ConstraintKind.RIGID_NON_BOUNCING_CONTACT,
                ConstraintKind.STATIONARY_FRAME);

        Set<CanonicalPhysicsProblem.CanonicalTarget> forwardPointTargets = new LinkedHashSet<>();
        for (ProjectileProblem.Target t : List.of(
                ProjectileProblem.Target.MAXIMUM_HEIGHT,
                ProjectileProblem.Target.TIME_TO_APEX,
                ProjectileProblem.Target.SPEED_AT_APEX,
                ProjectileProblem.Target.VELOCITY_AT_TIME,
                ProjectileProblem.Target.SPEED_AT_TIME,
                ProjectileProblem.Target.POSITION_AT_TIME,
                ProjectileProblem.Target.DISTANCE_TRAVELLED_AT_TIME,
                ProjectileProblem.Target.POSITION_AT_HORIZONTAL_DISPLACEMENT,
                ProjectileProblem.Target.VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                ProjectileProblem.Target.SPEED_AT_HORIZONTAL_DISPLACEMENT,
                ProjectileProblem.Target.TIME_AT_HORIZONTAL_DISPLACEMENT,
                ProjectileProblem.Target.POSITION_AT_HEIGHT,
                ProjectileProblem.Target.VELOCITY_AT_HEIGHT,
                ProjectileProblem.Target.SPEED_AT_HEIGHT,
                ProjectileProblem.Target.TIMES_AT_HEIGHT,
                ProjectileProblem.Target.TIMES_AT_POSITION)) {
            forwardPointTargets.add(CanonicalPhysicsProblem.target(t));
        }

        Set<CanonicalPhysicsProblem.CanonicalTarget> surfaceLandingTargets = new LinkedHashSet<>();
        surfaceLandingTargets.add(CanonicalPhysicsProblem.target(ProjectileProblem.Target.FLIGHT_TIME));
        surfaceLandingTargets.add(CanonicalPhysicsProblem.target(ProjectileProblem.Target.HORIZONTAL_RANGE));
        for (CanonicalPhysicsProblem.SurfaceTarget st : CanonicalPhysicsProblem.SurfaceTarget.values()) {
            surfaceLandingTargets.add(st);
        }

        Set<ProjectileProblem.Input> sharedForwardInputs = EnumSet.of(
                ProjectileProblem.Input.INITIAL_SPEED,
                ProjectileProblem.Input.LAUNCH_ANGLE,
                ProjectileProblem.Input.GRAVITY);

        CapabilityDescriptor projectile2d = new CapabilityDescriptor(
                CAPABILITY_PROJECTILE_2D_KINEMATICS,
                "Ideal 2D point-mass trajectory under uniform downward gravity",
                EnumSet.of(SurfaceKind.HORIZONTAL_PLANE, SurfaceKind.INCLINED_PLANE),
                forwardPointTargets,
                sharedForwardInputs,
                baseIdealConstraints,
                List.of(
                        "Time and coordinate point queries describe the continuing parabolic trajectory.",
                        "Zero air resistance, zero wind, zero spin, non-negative gravity."));

        Set<ConstraintKind> horizontalConstraints = EnumSet.copyOf(baseIdealConstraints);
        horizontalConstraints.add(ConstraintKind.HORIZONTAL_LANDING_SURFACE);
        CapabilityDescriptor horizontalSurface = new CapabilityDescriptor(
                CAPABILITY_HORIZONTAL_LANDING_SURFACE,
                "Constant-height horizontal landing plane y = landingY",
                EnumSet.of(SurfaceKind.HORIZONTAL_PLANE),
                surfaceLandingTargets,
                sharedForwardInputs,
                horizontalConstraints,
                List.of(
                        "First future descending/tangent crossing selected when launching below landingY.",
                        "No bouncing after landing."));

        Set<ConstraintKind> inclinedConstraints = EnumSet.copyOf(baseIdealConstraints);
        inclinedConstraints.add(ConstraintKind.INCLINED_LANDING_SURFACE);
        CapabilityDescriptor inclinedSurface = new CapabilityDescriptor(
                CAPABILITY_INCLINED_LANDING_SURFACE,
                "Planar inclined landing surface y(x) = anchorY + (x - anchorX) * tan(beta)",
                EnumSet.of(SurfaceKind.INCLINED_PLANE),
                surfaceLandingTargets,
                sharedForwardInputs,
                inclinedConstraints,
                List.of(
                        "Incline angle strictly inside (-90, 90) degrees.",
                        "Launch height must satisfy y0 >= surface height at x0.",
                        "Does not support surface-intersection inverse queries omitting vertical displacement."));

        Set<CanonicalPhysicsProblem.CanonicalTarget> inverseTargets = Set.of(
                CanonicalPhysicsProblem.target(ProjectileProblem.Target.FIND_INITIAL_SPEED),
                CanonicalPhysicsProblem.target(ProjectileProblem.Target.FIND_LAUNCH_ANGLE));
        CapabilityDescriptor inverseParams = new CapabilityDescriptor(
                CAPABILITY_INVERSE_PROJECTILE_PARAMETERS,
                "Analytical inverse determination of launch speed or launch angle from displacement/time conditions",
                EnumSet.of(SurfaceKind.HORIZONTAL_PLANE, SurfaceKind.INCLINED_PLANE),
                inverseTargets,
                EnumSet.of(ProjectileProblem.Input.GRAVITY),
                baseIdealConstraints,
                List.of(
                        "Supported for explicit (dx, dy) displacement and (t, vx/vy/speed) conditions.",
                        "Unsupported on inclined surfaces when HORIZONTAL_DISPLACEMENT lacks VERTICAL_DISPLACEMENT."));

        return List.of(projectile2d, horizontalSurface, inclinedSurface, inverseParams);
    }

    private static Map<String, CapabilityDescriptor> CollectionsUnmodifiableLinkedMap(
            Map<String, CapabilityDescriptor> source) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}

