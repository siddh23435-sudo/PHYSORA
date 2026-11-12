package edu.physics.problem;

import edu.physics.projectile.LandingSurface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.physics.problem.CanonicalPhysicsProblem.SurfaceTarget;
import static edu.physics.problem.PhysicsCapabilityRegistry.ConstraintKind;
import static edu.physics.problem.PhysicsCapabilityRegistry.SupportStatus;
import static edu.physics.problem.ProjectileProblem.Input;
import static edu.physics.problem.ProjectileProblem.Target;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsCapabilityRegistryTest {
    private final PhysicsCapabilityRegistry registry = new PhysicsCapabilityRegistry();

    @Test
    void supportsHorizontalSurfaceAndForwardTargetsWithExactCapabilitySelection() {
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Horizontal projectile multi-target")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addConstraint("constant-downward-gravity")
                .addConstraint("air-resistance-ignored")
                .addTarget(Target.HORIZONTAL_RANGE)
                .addTarget(Target.MAXIMUM_HEIGHT)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME),
                        Map.of(Input.TIME, q(1.0, PhysicalUnit.SECOND)))
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval = registry.evaluate(problem);

        assertEquals(SupportStatus.SUPPORTED, eval.status());
        assertTrue(eval.isSupported());
        assertTrue(eval.matchedCapabilityIds().contains(PhysicsCapabilityRegistry.CAPABILITY_HORIZONTAL_LANDING_SURFACE));
        assertTrue(eval.matchedCapabilityIds().contains(PhysicsCapabilityRegistry.CAPABILITY_PROJECTILE_2D_KINEMATICS));
        assertFalse(eval.matchedCapabilityIds().contains(PhysicsCapabilityRegistry.CAPABILITY_INCLINED_LANDING_SURFACE));
        assertTrue(eval.missingInputs().isEmpty());
        assertTrue(eval.unsupportedConstraints().isEmpty());
        assertTrue(eval.unknownConstraints().isEmpty());
        assertEquals(3, eval.targetAssessments().size());
        assertTrue(eval.targetAssessments().stream().allMatch(a -> a.status() == SupportStatus.SUPPORTED));
    }

    @Test
    void supportsInclinedSurfaceForwardAndSurfaceTargetsWithoutHardcoding() {
        CanonicalPhysicsProblem inclined = CanonicalPhysicsProblem.builder("Inclined plane projectile")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addConstraint("inclined-landing-surface")
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .addTarget(SurfaceTarget.MAXIMUM_HEIGHT_IN_FLIGHT)
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval = registry.evaluate(inclined);

        assertEquals(SupportStatus.SUPPORTED, eval.status());
        assertEquals(List.of(PhysicsCapabilityRegistry.CAPABILITY_INCLINED_LANDING_SURFACE),
                eval.matchedCapabilityIds());
        assertTrue(eval.supportedConstraints().contains(ConstraintKind.INCLINED_LANDING_SURFACE));
        assertEquals(4, eval.targetAssessments().size());
    }

    @Test
    void distinguishesSupportedAndRestrictedInverseTargetsOnInclinedSurface() {
        CanonicalPhysicsProblem supportedInverse = CanonicalPhysicsProblem.builder("Supported inverse over incline")
                .polarVelocity(null, q(15.0, PhysicalUnit.DEGREE), CanonicalPhysicsProblem.AngleReference.INCLINE_SURFACE)
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(2.0, PhysicalUnit.METRE)))
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation supportedEval = registry.evaluate(supportedInverse);
        assertEquals(SupportStatus.SUPPORTED, supportedEval.status());
        assertEquals(List.of(PhysicsCapabilityRegistry.CAPABILITY_INVERSE_PROJECTILE_PARAMETERS),
                supportedEval.matchedCapabilityIds());

        CanonicalPhysicsProblem restrictedInverse = CanonicalPhysicsProblem.builder("Inverse requiring incline intersection")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_LAUNCH_ANGLE),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(15.0, PhysicalUnit.METRE)))
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation restrictedEval = registry.evaluate(restrictedInverse);
        assertEquals(SupportStatus.UNSUPPORTED, restrictedEval.status());
        assertTrue(restrictedEval.unsupportedReasons().contains(
                "inverse-target-requires-inclined-surface-intersection:FIND_LAUNCH_ANGLE"));
    }

    @Test
    void reportsInsufficientInformationAndPreservesMultipleTargetsRequiringDifferentCapabilities() {
        CanonicalPhysicsProblem missingInputs = CanonicalPhysicsProblem.builder("Missing gravity and query time")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 20.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(Target.POSITION_AT_TIME)
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval = registry.evaluate(missingInputs);

        assertEquals(SupportStatus.INSUFFICIENT_INFORMATION, eval.status());
        assertTrue(eval.matchedCapabilityIds().contains(PhysicsCapabilityRegistry.CAPABILITY_INCLINED_LANDING_SURFACE));
        assertTrue(eval.matchedCapabilityIds().contains(PhysicsCapabilityRegistry.CAPABILITY_PROJECTILE_2D_KINEMATICS));
        assertTrue(eval.missingInputs().contains("GRAVITY"));
        assertTrue(eval.missingInputs().contains("TIME"));
    }

    @Test
    void distinguishesUnsupportedConstraintsAndUnknownConstraintsAndPreservesMissingFindings() {
        // Simultaneous missing gravity + known unsupported air-resistance + unknown unmapped constraint
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Complex incomplete unsupported problem")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .addConstraint("constant-downward-gravity")
                .addConstraint("quadratic-drag")
                .addConstraint("coriolis-deflection")
                .addTarget(Target.HORIZONTAL_RANGE)
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval =
                registry.evaluate(problem, Set.of(ConstraintKind.WIND));

        // Precedence: UNSUPPORTED overrides INSUFFICIENT_INFORMATION, while preserving ALL findings
        assertEquals(SupportStatus.UNSUPPORTED, eval.status());
        assertFalse(eval.isSupported());
        assertTrue(eval.supportedConstraints().contains(ConstraintKind.CONSTANT_DOWNWARD_GRAVITY));
        assertTrue(eval.unsupportedConstraints().contains(ConstraintKind.AIR_RESISTANCE));
        assertTrue(eval.unsupportedConstraints().contains(ConstraintKind.WIND));
        assertEquals(List.of("coriolis-deflection"), eval.unknownConstraints());
        assertTrue(eval.missingInputs().contains("GRAVITY"));
        assertTrue(eval.unsupportedReasons().contains("unknown-unmapped-constraint:coriolis-deflection"));
    }

    @Test
    void rejectsInvalidCanonicalSpecificationsAndSurfaceConstraintContradictions() {
        CanonicalPhysicsProblem conflict = CanonicalPhysicsProblem.builder("Contradictory velocity")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .velocityComponents(q(-100.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation conflictEval = registry.evaluate(conflict);
        assertEquals(SupportStatus.INVALID_INPUT, conflictEval.status());
        assertTrue(conflictEval.invalidDiagnostics().contains("conflicting-velocity-specification"));

        CanonicalPhysicsProblem surfaceMismatch = CanonicalPhysicsProblem.builder("Horizontal constraint with inclined geometry")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 25.0))
                .addConstraint("horizontal-landing-surface")
                .addUnsupportedConstraint("bounce-restitution")
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        // INVALID_INPUT takes strict precedence over UNSUPPORTED, while preserving unsupported findings
        PhysicsCapabilityRegistry.CapabilityEvaluation mismatchEval = registry.evaluate(surfaceMismatch);
        assertEquals(SupportStatus.INVALID_INPUT, mismatchEval.status());
        assertTrue(mismatchEval.invalidDiagnostics().contains(
                "constraint-contradicts-landing-surface:expected-horizontal-got-inclined"));
        assertTrue(mismatchEval.unsupportedConstraints().contains(ConstraintKind.BOUNCE_OR_RESTITUTION));

        assertEquals(SupportStatus.INVALID_INPUT, registry.evaluate(null).status());
    }

    @Test
    void handlesMixedSupportedAndUnsupportedTargetsWithinSingleProblem() {
        CanonicalPhysicsProblem mixed = CanonicalPhysicsProblem.builder("Valid range plus restricted incline inverse")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(20.0, PhysicalUnit.METRE)))
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval = registry.evaluate(mixed);
        assertEquals(SupportStatus.UNSUPPORTED, eval.status());
        assertEquals(SupportStatus.SUPPORTED, eval.targetAssessments().get(0).status());
        assertEquals(SupportStatus.UNSUPPORTED, eval.targetAssessments().get(1).status());
    }

    @Test
    void classifiesConstraintTokensDirectlyAndNormalizesUnsupportedConstraintsWithoutBypassingUnknowns() {
        var drag = registry.classifyConstraint("drag");
        assertTrue(drag.known());
        assertFalse(drag.supported());
        assertEquals(ConstraintKind.AIR_RESISTANCE, drag.kind());
        assertEquals("air-resistance", drag.canonicalId());

        var noWind = registry.classifyConstraint("neglect wind");
        assertTrue(noWind.known());
        assertTrue(noWind.supported());
        assertEquals(ConstraintKind.NO_WIND, noWind.kind());
        assertEquals("no-wind", noWind.canonicalId());

        var unknown = registry.classifyConstraint("quantum-tunneling");
        assertFalse(unknown.known());
        assertFalse(unknown.supported());
        assertNull(unknown.kind());

        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Unsupported synonym and unknown token")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addUnsupportedConstraint("drag")
                .addUnsupportedConstraint("quantum-tunneling")
                .addTarget(Target.HORIZONTAL_RANGE)
                .build();

        PhysicsCapabilityRegistry.CapabilityEvaluation eval = registry.evaluate(problem);
        assertEquals(SupportStatus.UNSUPPORTED, eval.status());
        assertTrue(eval.unsupportedConstraints().contains(ConstraintKind.AIR_RESISTANCE));
        assertTrue(eval.unsupportedReasons().contains("unsupported-constraint:air-resistance"));
        assertEquals(List.of("quantum-tunneling"), eval.unknownConstraints());
        assertTrue(eval.unsupportedReasons().contains("unknown-unmapped-constraint:quantum-tunneling"));
    }

    private static PhysicalQuantity q(double value, PhysicalUnit unit) {
        return new PhysicalQuantity(value, unit);
    }
}

