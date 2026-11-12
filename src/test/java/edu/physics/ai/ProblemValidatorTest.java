package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
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
        var normalized = result.normalizedProblem().parts().get(0).givens();
        assertEquals(20, normalized.get(ProjectileProblem.Input.INITIAL_SPEED).value(), 1e-12);
        assertEquals(45, normalized.get(ProjectileProblem.Input.LAUNCH_ANGLE).value(), 1e-12);
        assertEquals(9.81, normalized.get(ProjectileProblem.Input.GRAVITY).value(), 1e-12);
        assertEquals(PhysicalUnit.METRE_PER_SECOND, normalized.get(ProjectileProblem.Input.INITIAL_SPEED).unit());
    }

    @Test
    void missingInformationReturnsClarificationInsteadOfSendingAnIncompleteProblemToSolve() {
        var draft = draft(1.0, List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT,
                Map.of(ProjectileProblem.Input.INITIAL_SPEED,
                        new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND)))));

        var result = validator.validate("Find the maximum height", draft);

        assertEquals(ProblemValidationResult.Status.NEEDS_CLARIFICATION, result.status());
        assertEquals(2, result.clarifications().size());
        assertNull(result.normalizedProblem());
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
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new edu.physics.problem.ProjectileProblemSequence.ResultReference(1, "speed")))));

        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", invalidConfidence).status());
        assertEquals(ProblemValidationResult.Status.INVALID, validator.validate("q", forwardReference).status());
    }

    private static InterpretationDraft draft(double confidence, List<InterpretationDraft.ProblemPart> parts) {
        return new InterpretationDraft("normalized", parts, List.of("air resistance ignored"), List.of(), confidence);
    }
}
