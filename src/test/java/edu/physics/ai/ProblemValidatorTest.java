package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProblemValidatorTest {
    private final ProblemValidator validator = new ProblemValidator();

    @Test
    void classifierMapsTargetsToSolverQueryCategories() {
        var classifier = new ProblemClassifier();

        assertEquals(ProblemClassifier.Category.AT_TIME,
                classifier.classify(ProjectileProblem.Target.POSITION_AT_TIME).category());
        assertEquals(ProblemClassifier.Category.INVERSE_ANGLE,
                classifier.classify(ProjectileProblem.Target.FIND_LAUNCH_ANGLE).category());
    }

    @Test
    void normalizesMixedUnitsAndPreservesTheOriginalValues() {
        var draft = draft(0.9, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.FLIGHT_TIME, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(72, PhysicalUnit.KILOMETRE_PER_HOUR),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(Math.PI / 4, PhysicalUnit.RADIAN),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
        )));

        var result = validator.validate("Find flight time", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.canonicalProblem());
        assertEquals(20.0, result.canonicalProblem().derivedInitialSpeed().getAsDouble(), 1e-9);
        assertEquals(45.0, result.canonicalProblem().derivedLaunchAngleDegrees().getAsDouble(), 1e-9);
        assertEquals(9.81, result.canonicalProblem().gravity().getAsDouble(), 1e-9);

        var normalized = result.normalizedProblem().parts().get(0).givens();
        assertEquals(20.0, normalized.get(ProjectileProblem.Input.INITIAL_SPEED).value(), 1e-9);
        assertEquals(45.0, normalized.get(ProjectileProblem.Input.LAUNCH_ANGLE).value(), 1e-9);
        assertEquals(9.81, normalized.get(ProjectileProblem.Input.GRAVITY).value(), 1e-9);
        assertEquals(PhysicalUnit.METRE_PER_SECOND, normalized.get(ProjectileProblem.Input.INITIAL_SPEED).unit());
    }

    @Test
    void missingInformationReturnsClarificationInsteadOfSendingAnIncompleteProblemToSolve() {
        var draft = draft(1.0, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT,
                Map.of(ProjectileProblem.Input.INITIAL_SPEED,
                        new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND)))));

        var result = validator.validate("Find the maximum height", draft);

        assertEquals(ProblemValidationResult.Status.NEEDS_CLARIFICATION, result.status());
        assertFalse(result.clarifications().isEmpty());
        assertTrue(result.clarifications().get(0).prompt().toLowerCase().contains("launch angle"));
        assertNull(result.normalizedProblem());
        assertTrue(result.assumptions().stream().anyMatch(a -> a.contains("gravity") || a.contains("PHYSORA_DEFAULT")));
    }

    @Test
    void modelFlaggedAmbiguityIsReturnedWithoutGuessing() {
        var ambiguity = new InterpretationDraft.Clarification("Which distance?", "Distance can mean several quantities.",
                List.of("horizontal displacement", "distance along the path"));
        var draft = new InterpretationDraft("Speed after travelling 10 m", List.of(), List.of(), List.of(ambiguity), 0.8);

        var result = validator.validate("Find speed after distance 10 m", draft);

        assertEquals(ProblemValidationResult.Status.NEEDS_CLARIFICATION, result.status());
        assertEquals(ambiguity, result.clarifications().get(0));
    }

    @Test
    void incompatibleUnitsAndNegativeSpeedAreRejected() {
        var wrongUnit = draft(0.9, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)))));
        var negativeSpeed = draft(0.9, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(-20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)))));

        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", wrongUnit).status());
        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", negativeSpeed).status());
    }

    @Test
    void invalidConfidenceAndForwardReferencesAreRejected() {
        var invalidConfidence = draft(Double.NaN, List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())));
        var forwardReference = draft(1.0, List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FLIGHT_TIME, Map.of(),
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new ProjectileProblemSequence.ResultReference(1, "speed")))));

        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", invalidConfidence).status());
        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", forwardReference).status());
    }

    @Test
    void universalParameterPromotionAcrossAllParts() {
        var part0 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(25.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(30.0, PhysicalUnit.DEGREE)));
        var part1 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(25.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(30.0, PhysicalUnit.DEGREE)));
        var draft = draft(0.95, List.of(part0, part1));

        var result = validator.validate("Find max height and range for launch at 25 m/s at 30 deg", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.canonicalProblem());
        assertTrue(result.canonicalProblem().derivedInitialSpeed().isPresent());
        assertEquals(25.0, result.canonicalProblem().derivedInitialSpeed().getAsDouble(), 1e-9);
        assertTrue(result.canonicalProblem().derivedLaunchAngleDegrees().isPresent());
        assertEquals(30.0, result.canonicalProblem().derivedLaunchAngleDegrees().getAsDouble(), 1e-9);

        // Parameters should be stripped from part-level to maintain disjointness
        assertTrue(result.canonicalProblem().parts().get(0).parameters().isEmpty());
        assertTrue(result.canonicalProblem().parts().get(1).parameters().isEmpty());
    }

    @Test
    void omittedParameterRetainedAtPartLevel() {
        // Part 0 specifies speed 20 m/s and angle 45; Part 1 specifies angle 45 but OMITS speed (e.g. inverse or independent query)
        var part0 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)));
        var part1 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.FIND_INITIAL_SPEED, Map.of(
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(40.0, PhysicalUnit.METRE),
                ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(0.0, PhysicalUnit.METRE)));
        var draft = draft(0.95, List.of(part0, part1));

        var result = validator.validate("Find max height at 20 m/s. Then find speed required for 40 m range.", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.canonicalProblem());

        // Speed was omitted in Part 1, so speed CANNOT be promoted globally!
        assertFalse(result.canonicalProblem().hasVelocity());
        assertFalse(result.canonicalProblem().derivedInitialSpeed().isPresent());

        // Part 0 retains speed 20 m/s at part level
        assertEquals(20.0, result.canonicalProblem().parts().get(0).parameters()
                .get(ProjectileProblem.Input.INITIAL_SPEED).value(), 1e-9);
    }

    @Test
    void conflictingParameterRetainedAtPartLevel() {
        // Part 0 specifies speed 20 m/s; Part 1 specifies speed 30 m/s
        var part0 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(30.0, PhysicalUnit.DEGREE)));
        var part1 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(30.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)));
        var draft = draft(0.95, List.of(part0, part1));

        var result = validator.validate("A ball is thrown at 20 m/s at 30 deg. Another ball at 30 m/s at 45 deg. Find both ranges.", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.canonicalProblem());

        // Speed & angle differ, so neither is promoted globally
        assertFalse(result.canonicalProblem().hasVelocity());

        // Part 0 retains 20 m/s; Part 1 retains 30 m/s
        assertEquals(20.0, result.canonicalProblem().parts().get(0).parameters()
                .get(ProjectileProblem.Input.INITIAL_SPEED).value(), 1e-9);
        assertEquals(30.0, result.canonicalProblem().parts().get(1).parameters()
                .get(ProjectileProblem.Input.INITIAL_SPEED).value(), 1e-9);
    }

    @Test
    void explicitSharedGivensPromotedToProblemLevel() {
        var part0 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, Map.of());
        var part1 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of());
        var draft = new InterpretationDraft(
                "A projectile launched at 15 m/s at 60 deg.",
                List.of(part0, part1),
                List.of(),
                List.of(),
                0.95)
                .withSharedGivens(Map.of(
                        ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(15.0, PhysicalUnit.METRE_PER_SECOND),
                        ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(60.0, PhysicalUnit.DEGREE)));

        var result = validator.validate("Find max height and range for projectile launched at 15 m/s at 60 deg", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertTrue(result.canonicalProblem().derivedInitialSpeed().isPresent());
        assertEquals(15.0, result.canonicalProblem().derivedInitialSpeed().getAsDouble(), 1e-9);
    }

    @Test
    void referencedParameterNeverPromotedGlobally() {
        var part0 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.FIND_INITIAL_SPEED, Map.of(
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(50.0, PhysicalUnit.METRE),
                ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(0.0, PhysicalUnit.METRE)));
        var part1 = new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT,
                Map.of(ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)),
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new ProjectileProblemSequence.ResultReference(0, "initialSpeed")));
        var draft = draft(0.95, List.of(part0, part1));

        var result = validator.validate("Find initial speed for 50 m range. Then find max height using that speed.", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        // Speed is referenced in Part 1, so it cannot be promoted globally
        assertFalse(result.canonicalProblem().derivedInitialSpeed().isPresent());
        assertTrue(result.canonicalProblem().parts().get(1).references().containsKey(ProjectileProblem.Input.INITIAL_SPEED));
    }

    @Test
    void allFourValidationOutcomesPreserved() {
        // 1. READY
        var readyDraft = draft(0.95, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)))));
        assertEquals(ProblemValidationResult.Status.READY, validator.validate("Find range", readyDraft).status());

        // 2. NEEDS_CLARIFICATION
        var incompleteDraft = draft(0.95, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND)))));
        assertEquals(ProblemValidationResult.Status.NEEDS_CLARIFICATION, validator.validate("Find range", incompleteDraft).status());

        // 3. UNSUPPORTED
        var unsupportedDraft = new InterpretationDraft(
                "Find range with air resistance",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                        ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                        ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                        ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)))),
                List.of(),
                List.of(),
                0.95)
                .withCanonicalMetadata(null, CanonicalPhysicsProblem.AngleReference.HORIZONTAL, List.of(),
                        List.of(), List.of("air-resistance"));
        assertEquals(ProblemValidationResult.Status.UNSUPPORTED,
                validator.validate("Find range with air resistance", unsupportedDraft).status());

        // 4. INVALID
        var invalidDraft = draft(0.95, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(-20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)))));
        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("Find range", invalidDraft).status());
    }

    @Test
    void inferredContradictoryAssumptionRejected() {
        // Student prompt explicitly says "with air resistance", but provider assumes "air-resistance-ignored"
        var draft = new InterpretationDraft(
                "Find range with air resistance",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                        ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                        ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)))),
                List.of("air-resistance-ignored"),
                List.of(),
                0.95);

        var result = validator.validate("A ball is projected at 20 m/s at 45 deg in air resistance. Find range.", draft);

        // Contradictory provider assumption must be rejected by completeness validator
        assertEquals(ProblemValidationResult.Status.INVALID, result.status());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("contradict") || e.contains("air-resistance")));
    }

    @Test
    void physoraDefaultProvenanceRecordedInCompletenessReport() {
        var draft = draft(0.95, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)))));

        var result = validator.validate("A ball is thrown at 20 m/s at 45 deg", draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.completenessReport());

        // Gravity and ground landing must be recorded as introduced defaults
        assertTrue(result.completenessReport().introducedDefaults().stream()
                .anyMatch(d -> d.contains("gravity") || d.contains("PHYSORA_DEFAULT")));
        assertTrue(result.assumptions().stream()
                .anyMatch(a -> a.contains("gravity") || a.contains("PHYSORA_DEFAULT")));
    }

    @Test
    void legacyFiveArgumentDraftCompatibility() {
        // Tests that 5-argument constructor drafts without canonical metadata are accepted and correctly validated
        var legacyFiveArg = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of(
                        ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                        ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE)))),
                List.of("Motion treated as 2D point particle under uniform downward gravity."),
                List.of(),
                0.96);

        var result = validator.validate("Find horizontal range for launch at 20 m/s at 45 deg", legacyFiveArg);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.canonicalProblem());
        assertNotNull(result.normalizedProblem());
    }

    @Test
    void preservesFullClarificationHistory() {
        var request = new InterpretationRequest("Find projectile speed after 10 m", List.of(
                new InterpretationRequest.ClarificationTurn("Which distance was meant?", "Horizontal displacement of 10 m"),
                new InterpretationRequest.ClarificationTurn("What is the launch speed?", "Launch speed is 25 m/s"),
                new InterpretationRequest.ClarificationTurn("What is the launch angle?", "Launch angle is 30 degrees")
        ));

        var draft = draft(0.95, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.SPEED_AT_HORIZONTAL_DISPLACEMENT, Map.of(
                ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(10.0, PhysicalUnit.METRE),
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(25.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(30.0, PhysicalUnit.DEGREE)))));

        var result = validator.validate(request, draft);

        assertEquals(ProblemValidationResult.Status.READY, result.status());
        assertNotNull(result.completenessReport());
        // Verify that clarification turns reached the completeness validator and were recorded
        assertEquals(3, result.completenessReport().originalRequest().clarifications().size());
        assertEquals("Horizontal displacement of 10 m",
                result.completenessReport().originalRequest().clarifications().get(0).answer());
    }

    private static InterpretationDraft draft(double confidence, List<InterpretationDraft.ProblemPart> parts) {
        return new InterpretationDraft("normalized", parts, List.of("air resistance ignored"), List.of(), confidence);
    }
}
