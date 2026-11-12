package edu.physics.problem;

import edu.physics.projectile.LandingSurface;
import edu.physics.projectile.ProjectileMotion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Provider-independent canonical physics problem specification in SI units.
 *
 * <p>Stores the initial velocity exclusively as the Cartesian vector {@code (initialVx, initialVy)}.
 * Scalar speed and angle inputs—along with their angle reference frame—are preserved strictly
 * as non-competing metadata so there is never more than one numerical authority for velocity.
 */
public final class CanonicalPhysicsProblem {

    /** Polymorphic canonical target without duplicating existing {@link ProjectileProblem.Target} concepts. */
    public sealed interface CanonicalTarget permits StandardTarget, SurfaceTarget {
        String name();
    }

    /** Wraps an existing {@link ProjectileProblem.Target} without duplicating enum values. */
    public record StandardTarget(ProjectileProblem.Target target) implements CanonicalTarget {
        public StandardTarget {
            Objects.requireNonNull(target, "Standard projectile target is required");
        }

        @Override
        public String name() {
            return target.name();
        }
    }

    /** Generalized landing-surface and flight-bounded targets absent from {@link ProjectileProblem.Target}. */
    public enum SurfaceTarget implements CanonicalTarget {
        RANGE_ALONG_SURFACE,
        SIGNED_DISPLACEMENT_ALONG_SURFACE,
        COORDINATE_ALONG_SURFACE_FROM_ANCHOR,
        MAX_PERPENDICULAR_DISTANCE,
        MAX_SIGNED_PERPENDICULAR_DISTANCE,
        LANDING_POSITION,
        MAXIMUM_HEIGHT_IN_FLIGHT
    }

    public enum VelocityForm {
        CARTESIAN,
        POLAR,
        UNSPECIFIED
    }

    public enum AngleReference {
        HORIZONTAL,
        INCLINE_SURFACE
    }

    /** Non-competing provenance describing how the caller supplied velocity. */
    public record VelocityInputMetadata(
            VelocityForm form,
            PhysicalQuantity submittedSpeed,
            PhysicalQuantity submittedAngle,
            AngleReference angleReference,
            PhysicalQuantity submittedVx,
            PhysicalQuantity submittedVy) {
        public VelocityInputMetadata {
            Objects.requireNonNull(form, "Velocity form is required");
            Objects.requireNonNull(angleReference, "Angle reference is required");
        }

        public static VelocityInputMetadata unspecified() {
            return new VelocityInputMetadata(
                    VelocityForm.UNSPECIFIED, null, null, AngleReference.HORIZONTAL, null, null);
        }
    }

    /** Requested target plus target-specific parameters (such as query time or target height). */
    public record PartSpec(
            int index,
            CanonicalTarget target,
            Map<ProjectileProblem.Input, PhysicalQuantity> parameters) {
        public PartSpec {
            if (index < 0) {
                throw new IllegalArgumentException("Part index must be non-negative");
            }
            Objects.requireNonNull(target, "Canonical target is required");
            if (parameters == null || parameters.isEmpty()) {
                parameters = Map.of();
            } else {
                EnumMap<ProjectileProblem.Input, PhysicalQuantity> copy =
                        new EnumMap<>(ProjectileProblem.Input.class);
                copy.putAll(parameters);
                if (copy.containsValue(null)) {
                    throw new IllegalArgumentException("Part parameters cannot contain null values");
                }
                parameters = Collections.unmodifiableMap(copy);
            }
        }
    }

    private static final double EQUIVALENCE_TOLERANCE_ULPS = 64.0;
    private static final double RELATIVE_CONFLICT_TOLERANCE = 1e-9;

    private final String statement;
    private final double initialX;
    private final double initialY;
    private final Double initialVx;
    private final Double initialVy;
    private final Double gravity;
    private final LandingSurface landingSurface;
    private final VelocityInputMetadata velocityMetadata;
    private final List<PartSpec> parts;
    private final List<String> constraints;
    private final List<String> assumptions;
    private final List<String> unsupportedConstraints;
    private final List<String> validationErrors;
    private final List<ProjectileProblem.Input> missingSharedInputs;

    private CanonicalPhysicsProblem(
            String statement,
            double initialX,
            double initialY,
            Double initialVx,
            Double initialVy,
            Double gravity,
            LandingSurface landingSurface,
            VelocityInputMetadata velocityMetadata,
            List<PartSpec> parts,
            List<String> constraints,
            List<String> assumptions,
            List<String> unsupportedConstraints,
            List<String> validationErrors,
            List<ProjectileProblem.Input> missingSharedInputs) {
        this.statement = statement;
        this.initialX = initialX;
        this.initialY = initialY;
        this.initialVx = initialVx;
        this.initialVy = initialVy;
        this.gravity = gravity;
        this.landingSurface = landingSurface;
        this.velocityMetadata = velocityMetadata;
        this.parts = List.copyOf(parts);
        this.constraints = List.copyOf(constraints);
        this.assumptions = List.copyOf(assumptions);
        this.unsupportedConstraints = List.copyOf(unsupportedConstraints);
        this.validationErrors = List.copyOf(validationErrors);
        this.missingSharedInputs = List.copyOf(missingSharedInputs);
    }

    public static CanonicalTarget target(ProjectileProblem.Target target) {
        return new StandardTarget(target);
    }

    public static CanonicalTarget target(SurfaceTarget target) {
        return Objects.requireNonNull(target, "Surface target is required");
    }

    public static Builder builder(String statement) {
        return new Builder(statement);
    }

    /** Bridges an existing flat-surface {@link ProjectileProblem} into a canonical specification. */
    public static CanonicalPhysicsProblem fromProjectileProblem(ProjectileProblem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("Projectile problem is required");
        }
        Builder builder = new Builder(problem.statement());
        Map<ProjectileProblem.Input, PhysicalQuantity> givens = problem.givens();

        PhysicalQuantity x0 = givens.get(ProjectileProblem.Input.INITIAL_X);
        PhysicalQuantity y0 = givens.get(ProjectileProblem.Input.INITIAL_Y);
        if (x0 != null) builder.initialX(x0);
        if (y0 != null) builder.initialY(y0);

        PhysicalQuantity landingY = givens.get(ProjectileProblem.Input.LANDING_Y);
        if (landingY != null) {
            builder.horizontalLandingY(landingY);
        }

        PhysicalQuantity gravity = givens.get(ProjectileProblem.Input.GRAVITY);
        if (gravity != null) {
            builder.gravity(gravity);
        }

        PhysicalQuantity speed = givens.get(ProjectileProblem.Input.INITIAL_SPEED);
        PhysicalQuantity angle = givens.get(ProjectileProblem.Input.LAUNCH_ANGLE);
        if (speed != null || angle != null) {
            builder.polarVelocity(speed, angle, AngleReference.HORIZONTAL);
        }

        EnumMap<ProjectileProblem.Input, PhysicalQuantity> partParams =
                new EnumMap<>(ProjectileProblem.Input.class);
        givens.forEach((input, quantity) -> {
            switch (input) {
                case INITIAL_X, INITIAL_Y, LANDING_Y, GRAVITY, INITIAL_SPEED, LAUNCH_ANGLE -> {
                    // Represented at canonical problem level
                }
                default -> partParams.put(input, quantity);
            }
        });
        builder.addTarget(target(problem.target()), partParams);
        return builder.build();
    }

    public String statement() { return statement; }
    public double initialX() { return initialX; }
    public double initialY() { return initialY; }
    public LandingSurface landingSurface() { return landingSurface; }
    public VelocityInputMetadata velocityMetadata() { return velocityMetadata; }
    public List<PartSpec> parts() { return parts; }
    public List<String> constraints() { return constraints; }
    public List<String> assumptions() { return assumptions; }
    public List<String> unsupportedConstraints() { return unsupportedConstraints; }
    public List<String> validationErrors() { return validationErrors; }
    public List<ProjectileProblem.Input> missingSharedInputs() { return missingSharedInputs; }

    public boolean hasVelocity() {
        return initialVx != null && initialVy != null;
    }

    public boolean hasGravity() {
        return gravity != null;
    }

    public boolean isValidForEvaluation() {
        return validationErrors.isEmpty() && unsupportedConstraints.isEmpty();
    }

    public OptionalDouble initialVx() {
        return initialVx == null ? OptionalDouble.empty() : OptionalDouble.of(initialVx);
    }

    public OptionalDouble initialVy() {
        return initialVy == null ? OptionalDouble.empty() : OptionalDouble.of(initialVy);
    }

    public OptionalDouble gravity() {
        return gravity == null ? OptionalDouble.empty() : OptionalDouble.of(gravity);
    }

    /** Derived initial speed magnitude from the canonical velocity vector {@code (vx, vy)}. */
    public OptionalDouble derivedInitialSpeed() {
        if (!hasVelocity()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.hypot(initialVx, initialVy));
    }

    /** Derived launch angle in degrees counterclockwise from {@code +x}. */
    public OptionalDouble derivedLaunchAngleDegrees() {
        if (!hasVelocity()) {
            return OptionalDouble.empty();
        }
        if (initialVx == 0.0 && initialVy == 0.0) {
            return OptionalDouble.of(0.0);
        }
        return OptionalDouble.of(Math.toDegrees(Math.atan2(initialVy, initialVx)));
    }

    /**
     * Instantiates the generalized {@link ProjectileMotion} model from this canonical specification.
     *
     * @throws IllegalStateException if validation errors exist or velocity/gravity are incomplete
     */
    public ProjectileMotion toMotion() {
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("Cannot build motion from invalid canonical problem: "
                    + String.join(", ", validationErrors));
        }
        if (!unsupportedConstraints.isEmpty()) {
            throw new IllegalStateException("Cannot build motion when unsupported constraints exist: "
                    + String.join(", ", unsupportedConstraints));
        }
        if (!hasVelocity() || !hasGravity()) {
            throw new IllegalStateException("Velocity vector and gravity are required to construct ProjectileMotion");
        }
        return ProjectileMotion.fromVelocityComponents(
                initialVx, initialVy, gravity, initialX, initialY, landingSurface);
    }

    public static final class Builder {
        private final String statement;
        private PhysicalQuantity x0Quantity;
        private PhysicalQuantity y0Quantity;
        private PhysicalQuantity landingYQuantity;
        private LandingSurface explicitLandingSurface;
        private PhysicalQuantity gravityQuantity;
        private PhysicalQuantity polarSpeed;
        private PhysicalQuantity polarAngle;
        private AngleReference polarReference = AngleReference.HORIZONTAL;
        private boolean hasPolarAttempt;
        private PhysicalQuantity cartesianVx;
        private PhysicalQuantity cartesianVy;
        private boolean hasCartesianAttempt;
        private final List<PartSpec> requestedParts = new ArrayList<>();
        private final List<String> constraints = new ArrayList<>();
        private final List<String> assumptions = new ArrayList<>();
        private final List<String> unsupportedConstraints = new ArrayList<>();

        private Builder(String statement) {
            if (statement == null || statement.isBlank()) {
                throw new IllegalArgumentException("Problem statement is required");
            }
            this.statement = statement;
        }

        public Builder initialPosition(PhysicalQuantity x0, PhysicalQuantity y0) {
            this.x0Quantity = x0;
            this.y0Quantity = y0;
            return this;
        }

        public Builder initialX(PhysicalQuantity x0) {
            this.x0Quantity = x0;
            return this;
        }

        public Builder initialY(PhysicalQuantity y0) {
            this.y0Quantity = y0;
            return this;
        }

        public Builder gravity(PhysicalQuantity gravity) {
            this.gravityQuantity = gravity;
            return this;
        }

        public Builder landingSurface(LandingSurface landingSurface) {
            this.explicitLandingSurface = Objects.requireNonNull(landingSurface, "Landing surface is required");
            return this;
        }

        public Builder horizontalLandingY(PhysicalQuantity landingY) {
            this.landingYQuantity = landingY;
            return this;
        }

        public Builder velocityComponents(PhysicalQuantity vx, PhysicalQuantity vy) {
            this.cartesianVx = vx;
            this.cartesianVy = vy;
            this.hasCartesianAttempt = true;
            return this;
        }

        public Builder polarVelocity(PhysicalQuantity speed, PhysicalQuantity angle) {
            return polarVelocity(speed, angle, AngleReference.HORIZONTAL);
        }

        public Builder polarVelocity(PhysicalQuantity speed, PhysicalQuantity angle, AngleReference angleReference) {
            this.polarSpeed = speed;
            this.polarAngle = angle;
            this.polarReference = Objects.requireNonNull(angleReference, "Angle reference is required");
            this.hasPolarAttempt = true;
            return this;
        }

        public Builder addTarget(CanonicalTarget target) {
            return addTarget(target, Map.of());
        }

        public Builder addTarget(ProjectileProblem.Target target) {
            return addTarget(CanonicalPhysicsProblem.target(target), Map.of());
        }

        public Builder addTarget(SurfaceTarget target) {
            return addTarget((CanonicalTarget) target, Map.of());
        }

        public Builder addTarget(CanonicalTarget target, Map<ProjectileProblem.Input, PhysicalQuantity> parameters) {
            int nextIndex = requestedParts.size();
            requestedParts.add(new PartSpec(nextIndex, target, parameters));
            return this;
        }

        public Builder addConstraint(String constraint) {
            if (constraint != null && !constraint.isBlank()) {
                constraints.add(constraint.trim());
            }
            return this;
        }

        public Builder addAssumption(String assumption) {
            if (assumption != null && !assumption.isBlank()) {
                assumptions.add(assumption.trim());
            }
            return this;
        }

        public Builder addUnsupportedConstraint(String unsupportedConstraint) {
            if (unsupportedConstraint != null && !unsupportedConstraint.isBlank()) {
                unsupportedConstraints.add(unsupportedConstraint.trim());
            }
            return this;
        }

        /** Builds a deeply immutable snapshot; safe to invoke repeatedly without duplicating builder state. */
        public CanonicalPhysicsProblem build() {
            if (requestedParts.isEmpty()) {
                throw new IllegalArgumentException("At least one target is required");
            }
            List<PartSpec> builtParts = new ArrayList<>(requestedParts.size());
            for (int i = 0; i < requestedParts.size(); i++) {
                PartSpec raw = requestedParts.get(i);
                builtParts.add(new PartSpec(i, raw.target(), raw.parameters()));
            }
            List<String> localAssumptions = new ArrayList<>(assumptions);
            List<String> localErrors = new ArrayList<>();
            List<ProjectileProblem.Input> localMissing = new ArrayList<>();

            Double x0Value = validateLengthQuantity(x0Quantity, ProjectileProblem.Input.INITIAL_X, localErrors);
            double resolvedX0 = 0.0;
            if (x0Quantity == null) {
                localAssumptions.add("initial-x-defaulted-to-zero-metres");
            } else if (x0Value != null) {
                resolvedX0 = x0Value;
            }

            Double y0Value = validateLengthQuantity(y0Quantity, ProjectileProblem.Input.INITIAL_Y, localErrors);
            double resolvedY0 = 0.0;
            if (y0Quantity == null) {
                localAssumptions.add("initial-y-defaulted-to-zero-metres");
            } else if (y0Value != null) {
                resolvedY0 = y0Value;
            }

            LandingSurface resolvedSurface = resolveLandingSurface(
                    resolvedY0, localAssumptions, localErrors, x0Value != null || x0Quantity == null, y0Value != null || y0Quantity == null);
            Double resolvedGravity = resolveGravity(localErrors, localMissing);

            VelocityResolution velocity = resolveVelocity(resolvedSurface, localAssumptions, localErrors, localMissing);
            validateLaunchClearanceOnIncline(resolvedX0, resolvedY0, resolvedSurface, x0Value != null || x0Quantity == null, y0Value != null || y0Quantity == null, localErrors);

            return new CanonicalPhysicsProblem(
                    statement,
                    resolvedX0,
                    resolvedY0,
                    velocity.vx,
                    velocity.vy,
                    resolvedGravity,
                    resolvedSurface,
                    velocity.metadata,
                    builtParts,
                    constraints,
                    localAssumptions,
                    unsupportedConstraints,
                    localErrors,
                    localMissing);
        }

        private record VelocityResolution(Double vx, Double vy, VelocityInputMetadata metadata) {}

        private static Double validateLengthQuantity(PhysicalQuantity quantity,
                                                     ProjectileProblem.Input input,
                                                     List<String> errors) {
            if (quantity == null) {
                return null;
            }
            if (quantity.unit().dimension() != PhysicalUnit.Dimension.LENGTH) {
                errors.add("incompatible-unit:" + input.name());
                return null;
            }
            if (!Double.isFinite(quantity.value())) {
                errors.add("non-finite-input:" + input.name());
                return null;
            }
            double si = quantity.valueInSi();
            if (!Double.isFinite(si)) {
                errors.add("unit-conversion-overflow:" + input.name());
                return null;
            }
            return si;
        }

        private LandingSurface resolveLandingSurface(double resolvedY0,
                                                     List<String> localAssumptions,
                                                     List<String> localErrors,
                                                     boolean x0Valid,
                                                     boolean y0Valid) {
            Double landingYSi = validateLengthQuantity(landingYQuantity, ProjectileProblem.Input.LANDING_Y, localErrors);
            if (explicitLandingSurface != null && landingYQuantity != null) {
                if (landingYSi != null && (!explicitLandingSurface.isHorizontal()
                        || Double.compare(explicitLandingSurface.anchorY(), landingYSi) != 0)) {
                    localErrors.add("conflicting-landing-surface-specification");
                }
                return explicitLandingSurface;
            }
            if (explicitLandingSurface != null) {
                return explicitLandingSurface;
            }
            if (landingYQuantity != null) {
                return landingYSi != null
                        ? LandingSurface.horizontal(landingYSi)
                        : LandingSurface.horizontal(resolvedY0);
            }
            localAssumptions.add("landing-surface-defaulted-to-initial-height");
            return LandingSurface.horizontal(y0Valid ? resolvedY0 : 0.0);
        }

        private Double resolveGravity(List<String> localErrors, List<ProjectileProblem.Input> localMissing) {
            if (gravityQuantity == null) {
                localMissing.add(ProjectileProblem.Input.GRAVITY);
                return null;
            }
            if (gravityQuantity.unit().dimension() != PhysicalUnit.Dimension.ACCELERATION) {
                localErrors.add("incompatible-unit:GRAVITY");
                return null;
            }
            if (!Double.isFinite(gravityQuantity.value())) {
                localErrors.add("non-finite-input:GRAVITY");
                return null;
            }
            double si = gravityQuantity.valueInSi();
            if (!Double.isFinite(si)) {
                localErrors.add("unit-conversion-overflow:GRAVITY");
                return null;
            }
            if (si < 0.0) {
                localErrors.add("negative-nonnegative-quantity:GRAVITY");
                return null;
            }
            return si;
        }

        private VelocityResolution resolveVelocity(LandingSurface resolvedSurface,
                                                     List<String> localAssumptions,
                                                     List<String> localErrors,
                                                     List<ProjectileProblem.Input> localMissing) {
            Double cartVx = parseVelocityQuantity(cartesianVx, "INITIAL_VX", localErrors);
            Double cartVy = parseVelocityQuantity(cartesianVy, "INITIAL_VY", localErrors);
            boolean cartesianComplete = hasCartesianAttempt && cartVx != null && cartVy != null;

            Double polSpeed = parseSpeedQuantity(polarSpeed, localErrors);
            Double polAngleDeg = parseAngleDegrees(polarAngle, localErrors);
            Double polHorizontalAngle = null;
            if (polAngleDeg != null) {
                double horizontalAngle = switch (polarReference) {
                    case HORIZONTAL -> polAngleDeg;
                    case INCLINE_SURFACE -> polAngleDeg + resolvedSurface.angleDegrees();
                };
                if (!Double.isFinite(horizontalAngle)) {
                    localErrors.add("non-finite-normalized-angle");
                } else {
                    polHorizontalAngle = horizontalAngle;
                }
            }
            boolean polarComplete = hasPolarAttempt && polSpeed != null && polHorizontalAngle != null;

            double[] polarVector = polarComplete ? resolvePolarComponents(polSpeed, polHorizontalAngle) : null;

            if (cartesianComplete && polarVector != null) {
                if (!vectorsAgree(cartVx, cartVy, polarVector[0], polarVector[1])) {
                    localErrors.add("conflicting-velocity-specification");
                }
                VelocityInputMetadata meta = new VelocityInputMetadata(
                        VelocityForm.CARTESIAN, polarSpeed, polarAngle, polarReference, cartesianVx, cartesianVy);
                return new VelocityResolution(normalizeZero(cartVx), normalizeZero(cartVy), meta);
            }

            if (cartesianComplete) {
                // Cross-check any partially supplied polar scalar against the complete Cartesian vector.
                if (hasPolarAttempt) {
                    double derivedSpeed = Math.hypot(cartVx, cartVy);
                    if (polSpeed != null && !scalarsAgree(polSpeed, derivedSpeed, Math.max(polSpeed, derivedSpeed))) {
                        localErrors.add("conflicting-velocity-specification");
                    }
                    if (polHorizontalAngle != null && derivedSpeed > 0.0) {
                        double[] unitPolar = resolvePolarComponents(1.0, polHorizontalAngle);
                        if (!vectorsAgree(cartVx / derivedSpeed, cartVy / derivedSpeed, unitPolar[0], unitPolar[1])) {
                            localErrors.add("conflicting-velocity-specification");
                        }
                    }
                }
                VelocityInputMetadata meta = new VelocityInputMetadata(
                        VelocityForm.CARTESIAN, polarSpeed, polarAngle, polarReference, cartesianVx, cartesianVy);
                return new VelocityResolution(normalizeZero(cartVx), normalizeZero(cartVy), meta);
            }

            if (polarVector != null) {
                // Cross-check any partially supplied Cartesian component against the complete polar velocity.
                if (hasCartesianAttempt) {
                    if (cartVx != null && !scalarsAgree(cartVx, polarVector[0], polSpeed)) {
                        localErrors.add("conflicting-velocity-specification");
                    }
                    if (cartVy != null && !scalarsAgree(cartVy, polarVector[1], polSpeed)) {
                        localErrors.add("conflicting-velocity-specification");
                    }
                }
                VelocityInputMetadata meta = new VelocityInputMetadata(
                        VelocityForm.POLAR, polarSpeed, polarAngle, polarReference, cartesianVx, cartesianVy);
                if (polarReference == AngleReference.INCLINE_SURFACE && !resolvedSurface.isHorizontal()) {
                    localAssumptions.add("launch-angle-normalized-from-incline-surface-to-horizontal");
                }
                return new VelocityResolution(normalizeZero(polarVector[0]), normalizeZero(polarVector[1]), meta);
            }

            if (polSpeed != null) {
                double magTol = Math.max(RELATIVE_CONFLICT_TOLERANCE * polSpeed,
                        EQUIVALENCE_TOLERANCE_ULPS * Math.ulp(polSpeed));
                if ((cartVx != null && Math.abs(cartVx) > polSpeed + magTol)
                        || (cartVy != null && Math.abs(cartVy) > polSpeed + magTol)) {
                    localErrors.add("conflicting-velocity-specification");
                }
            }

            VelocityInputMetadata unresolvedMeta = new VelocityInputMetadata(
                    hasCartesianAttempt ? VelocityForm.CARTESIAN
                            : (hasPolarAttempt ? VelocityForm.POLAR : VelocityForm.UNSPECIFIED),
                    polarSpeed, polarAngle, polarReference, cartesianVx, cartesianVy);

            if (!hasPolarAttempt && !hasCartesianAttempt) {
                localMissing.add(ProjectileProblem.Input.INITIAL_SPEED);
                localMissing.add(ProjectileProblem.Input.LAUNCH_ANGLE);
            } else if (hasPolarAttempt) {
                if (polarSpeed == null) localMissing.add(ProjectileProblem.Input.INITIAL_SPEED);
                if (polarAngle == null) localMissing.add(ProjectileProblem.Input.LAUNCH_ANGLE);
            } else {
                localMissing.add(ProjectileProblem.Input.INITIAL_SPEED);
                localMissing.add(ProjectileProblem.Input.LAUNCH_ANGLE);
            }
            return new VelocityResolution(null, null, unresolvedMeta);
        }

        private static Double parseVelocityQuantity(PhysicalQuantity quantity, String label, List<String> localErrors) {
            if (quantity == null) {
                return null;
            }
            if (quantity.unit().dimension() != PhysicalUnit.Dimension.SPEED) {
                localErrors.add("incompatible-unit:" + label);
                return null;
            }
            if (!Double.isFinite(quantity.value())) {
                localErrors.add("non-finite-input:" + label);
                return null;
            }
            double si = quantity.valueInSi();
            if (!Double.isFinite(si)) {
                localErrors.add("unit-conversion-overflow:" + label);
                return null;
            }
            return si;
        }

        private static Double parseSpeedQuantity(PhysicalQuantity speed, List<String> localErrors) {
            if (speed == null) {
                return null;
            }
            if (speed.unit().dimension() != PhysicalUnit.Dimension.SPEED) {
                localErrors.add("incompatible-unit:INITIAL_SPEED");
                return null;
            }
            if (!Double.isFinite(speed.value())) {
                localErrors.add("non-finite-input:INITIAL_SPEED");
                return null;
            }
            double si = speed.valueInSi();
            if (!Double.isFinite(si)) {
                localErrors.add("unit-conversion-overflow:INITIAL_SPEED");
                return null;
            }
            if (si < 0.0) {
                localErrors.add("negative-nonnegative-quantity:INITIAL_SPEED");
                return null;
            }
            return si;
        }

        private static Double parseAngleDegrees(PhysicalQuantity angle, List<String> localErrors) {
            if (angle == null) {
                return null;
            }
            if (angle.unit().dimension() != PhysicalUnit.Dimension.ANGLE) {
                localErrors.add("incompatible-unit:LAUNCH_ANGLE");
                return null;
            }
            if (!Double.isFinite(angle.value())) {
                localErrors.add("non-finite-input:LAUNCH_ANGLE");
                return null;
            }
            double degrees = angle.valueInSi();
            if (!Double.isFinite(degrees)) {
                localErrors.add("unit-conversion-overflow:LAUNCH_ANGLE");
                return null;
            }
            return degrees;
        }

        private static void validateLaunchClearanceOnIncline(double resolvedX0,
                                                             double resolvedY0,
                                                             LandingSurface resolvedSurface,
                                                             boolean x0Valid,
                                                             boolean y0Valid,
                                                             List<String> localErrors) {
            if (resolvedSurface == null || resolvedSurface.isHorizontal() || !x0Valid || !y0Valid) {
                return;
            }
            double clearance = resolvedY0 - resolvedSurface.heightAt(resolvedX0);
            double scale = Math.abs(resolvedY0) + Math.abs(resolvedSurface.anchorY())
                    + Math.abs((resolvedX0 - resolvedSurface.anchorX()) * resolvedSurface.slopeTangent());
            double tolerance = scale == 0.0 ? 0.0 : 16.0 * Math.ulp(scale);
            if (clearance < -tolerance) {
                localErrors.add("launch-below-inclined-surface");
            }
        }

        private static boolean scalarsAgree(double a, double b, double referenceScale) {
            double scale = Math.max(Math.abs(a), Math.max(Math.abs(b), Math.abs(referenceScale)));
            if (scale == 0.0) {
                return true;
            }
            double tol = Math.max(RELATIVE_CONFLICT_TOLERANCE * scale, EQUIVALENCE_TOLERANCE_ULPS * Math.ulp(scale));
            return Math.abs(a - b) <= tol;
        }

        private static double[] resolvePolarComponents(double speed, double angleDegrees) {
            double cos = cosDegrees(angleDegrees);
            double sin = sinDegrees(angleDegrees);
            return new double[] { speed * cos, speed * sin };
        }

        private static boolean vectorsAgree(double vx1, double vy1, double vx2, double vy2) {
            double scale = Math.max(Math.hypot(vx1, vy1), Math.hypot(vx2, vy2));
            if (scale == 0.0) {
                return true;
            }
            double diff = Math.hypot(vx1 - vx2, vy1 - vy2);
            double tol = Math.max(RELATIVE_CONFLICT_TOLERANCE * scale, EQUIVALENCE_TOLERANCE_ULPS * Math.ulp(scale));
            return diff <= tol;
        }

        private static double cosDegrees(double degrees) {
            if (degrees % 90.0 == 0.0) {
                int quadrant = Math.floorMod((int) Math.round((degrees % 360.0) / 90.0), 4);
                return switch (quadrant) { case 0 -> 1.0; case 2 -> -1.0; default -> 0.0; };
            }
            return Math.cos(Math.toRadians(degrees % 360.0));
        }

        private static double sinDegrees(double degrees) {
            if (degrees % 90.0 == 0.0) {
                int quadrant = Math.floorMod((int) Math.round((degrees % 360.0) / 90.0), 4);
                return switch (quadrant) { case 1 -> 1.0; case 3 -> -1.0; default -> 0.0; };
            }
            return Math.sin(Math.toRadians(degrees % 360.0));
        }

        private static double normalizeZero(double value) {
            return value == 0.0 ? 0.0 : value;
        }
    }
}

