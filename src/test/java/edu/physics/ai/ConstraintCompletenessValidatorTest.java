package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.projectile.LandingSurface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static edu.physics.ai.ConstraintCompletenessValidator.CompletenessReport;
import static edu.physics.ai.ConstraintCompletenessValidator.ConstraintCoverage;
import static edu.physics.ai.ConstraintCompletenessValidator.ConstraintCoverageStatus;
import static edu.physics.ai.ConstraintCompletenessValidator.Outcome;
import static edu.physics.ai.InterpretationDraft.ConstraintOrigin;
import static edu.physics.ai.InterpretationDraft.ConstraintResolution;
import static edu.physics.ai.InterpretationDraft.ExtractedConstraint;
import static edu.physics.ai.InterpretationDraft.SurfaceSpec;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintCompletenessValidatorTest {
    private final ConstraintCompletenessValidator validator = new ConstraintCompletenessValidator();

    @Test
    void validatesSupportedHorizontalAndInclinedProblemsWithAllBucketsSeparated() {
        InterpretationRequest horizReq = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 degrees with g = 9.81 m/s^2 and no wind. Find horizontal range.");
        InterpretationDraft horizDraft = new InterpretationDraft(
                "Find horizontal range from flat ground",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of("air resistance ignored"),
                List.of(),
                0.95,
                new SurfaceSpec.Horizontal(0.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.target(ProjectileProblem.Target.HORIZONTAL_RANGE)),
                List.of(
                        new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "g = 9.81 m/s^2"),
                        new ExtractedConstraint("no-wind", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "no wind")),
                List.of());
        CanonicalPhysicsProblem horizCanonical = CanonicalPhysicsProblem.builder(horizReq.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addConstraint("constant-downward-gravity")
                .addConstraint("no-wind")
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();

        CompletenessReport horizReport = validator.validate(horizReq, horizDraft, horizCanonical);
        assertEquals(Outcome.READY_FOR_SOLVING, horizReport.outcome());
        assertTrue(horizReport.isReadyForSolving());
        assertTrue(horizReport.explicitStudentConditions().stream()
                .anyMatch(c -> c.contains("constant-downward-gravity")));
        assertTrue(horizReport.introducedDefaults().contains("initial-x-defaulted-to-zero-metres"));
        assertTrue(horizReport.introducedDefaults().contains("initial-y-defaulted-to-zero-metres"));
        assertTrue(horizReport.modelAssumptions().contains("air-resistance-ignored"));

        ConstraintCoverage gravityLedger = ledgerEntry(horizReport, "constant-downward-gravity");
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, gravityLedger.status());
        assertTrue(gravityLedger.extracted());
        assertEquals("Gravity=9.81 m/s^2", gravityLedger.canonicalRepresentation());

        ConstraintCoverage noWindLedger = ledgerEntry(horizReport, "no-wind");
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, noWindLedger.status());
        assertEquals("ConstraintMetadata(no-wind)", noWindLedger.canonicalRepresentation());

        InterpretationRequest inclineReq = new InterpretationRequest(
                "Ball projected at 25 m/s at 30 deg above a 30 deg rising incline from (5, 10). Take g = 10 m/s^2.");
        InterpretationDraft inclineDraft = new InterpretationDraft(
                "Find range along 30 deg inclined plane",
                List.of(),
                List.of(),
                List.of(),
                0.94,
                new SurfaceSpec.Inclined(5.0, 10.0, 30.0),
                CanonicalPhysicsProblem.AngleReference.INCLINE_SURFACE,
                List.of(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE),
                List.of(new ExtractedConstraint("inclined-landing-surface", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "rising 30 deg incline from (5, 10)")),
                List.of());
        CanonicalPhysicsProblem inclineCanonical = CanonicalPhysicsProblem.builder(inclineReq.question())
                .initialPosition(q(5.0, PhysicalUnit.METRE), q(10.0, PhysicalUnit.METRE))
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(30.0, PhysicalUnit.DEGREE),
                        CanonicalPhysicsProblem.AngleReference.INCLINE_SURFACE)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(5.0, 10.0, 30.0))
                .addConstraint("inclined-landing-surface")
                .addTarget(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        CompletenessReport inclineReport = validator.validate(inclineReq, inclineDraft, inclineCanonical);
        assertEquals(Outcome.READY_FOR_SOLVING, inclineReport.outcome());
        assertTrue(inclineReport.introducedDefaults().contains("launch-angle-normalized-from-incline-surface-to-horizontal"));
        ConstraintCoverage inclineLedger = ledgerEntry(inclineReport, "inclined-landing-surface");
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, inclineLedger.status());
        assertTrue(inclineLedger.canonicalRepresentation().startsWith("InclinedPlane("));
    }

    // Scenario 1: Original question mentions air resistance, draft omits it
    @Test
    void blocksSolvingWhenOriginalQuestionMentionsAirResistanceAndDraftOmitsIt() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is projected from an inclined plane at 25 m/s and 45 deg with g = 10 m/s^2. It experiences air resistance. Calculate its range.");
        InterpretationDraft omittedDragDraft = new InterpretationDraft(
                "Calculate range along 30 deg inclined plane",
                List.of(),
                List.of(),
                List.of(),
                0.93,
                new SurfaceSpec.Inclined(0.0, 0.0, 30.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE),
                List.of(new ExtractedConstraint("inclined-landing-surface", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "from an inclined plane")),
                List.of());
        CanonicalPhysicsProblem canonicalWithoutDrag = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        CompletenessReport report = validator.validate(req, omittedDragDraft, canonicalWithoutDrag);
        assertEquals(Outcome.UNSUPPORTED, report.outcome());
        assertFalse(report.isReadyForSolving());
        assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));
        assertTrue(report.unsupportedDiagnostics().contains("unsupported-constraint:air-resistance"));
        assertFalse(report.modelAssumptions().contains("air-resistance-ignored"));

        ConstraintCoverage dragEntry = ledgerEntry(report, "air-resistance");
        assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION, dragEntry.status());
        assertFalse(dragEntry.extracted());
        assertEquals("None", dragEntry.canonicalRepresentation());
        assertEquals(ConstraintOrigin.STUDENT_EXPLICIT, dragEntry.origin());
    }

    // Scenario 2: Original question mentions wind, draft omits it
    @Test
    void blocksSolvingWhenOriginalQuestionMentionsWindAndDraftOmitsIt() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is launched at 20 m/s at 45 deg with g = 9.81 m/s^2 in a strong crosswind. Find range.");
        InterpretationDraft draftOmittingWind = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.9);
        CanonicalPhysicsProblem canonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();

        CompletenessReport report = validator.validate(req, draftOmittingWind, canonical);
        assertEquals(Outcome.UNSUPPORTED, report.outcome());
        assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:wind"));
        assertTrue(report.unsupportedDiagnostics().contains("unsupported-constraint:wind"));

        ConstraintCoverage windEntry = ledgerEntry(report, "wind");
        assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION, windEntry.status());
        assertFalse(windEntry.extracted());
        assertEquals("None", windEntry.canonicalRepresentation());
    }

    // Scenario 3: Clarification answer introduces air resistance, draft omits it
    @Test
    void blocksSolvingWhenClarificationAnswerIntroducesAirResistanceAndDraftOmitsIt() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 deg with g = 9.81 m/s^2. Find range.",
                List.of(new InterpretationRequest.ClarificationTurn(
                        "Should air resistance be ignored?",
                        "No, include air resistance during the flight.")));
        InterpretationDraft draft = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.91);
        CanonicalPhysicsProblem canonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();

        CompletenessReport report = validator.validate(req, draft, canonical);
        assertEquals(Outcome.UNSUPPORTED, report.outcome());
        assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));
        assertTrue(report.clarificationDerivedConditions().stream().anyMatch(c -> c.contains("air-resistance")));

        ConstraintCoverage entry = ledgerEntry(report, "air-resistance");
        assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION, entry.status());
        assertEquals(ConstraintOrigin.CLARIFICATION_ANSWER, entry.origin());
        assertFalse(entry.extracted());
    }

    // Scenario 4: Clarification answer changes gravity, canonical retains the old value
    @Test
    void rejectsProblemWhenClarificationChangesGravityAndCanonicalRetainsOldValue() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 deg. Find range.",
                List.of(new InterpretationRequest.ClarificationTurn(
                        "What value of gravity should be used?",
                        "Use g = 10 m/s^2.")));
        InterpretationDraft draft = new InterpretationDraft(
                "Find horizontal range with g = 10",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.92,
                new SurfaceSpec.Horizontal(0.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(),
                List.of(new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.CLARIFICATION_ANSWER,
                        ConstraintResolution.ASSERTED, "Use g = 10 m/s^2.")),
                List.of());
        CanonicalPhysicsProblem staleCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addConstraint("constant-downward-gravity")
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();

        CompletenessReport report = validator.validate(req, draft, staleCanonical);
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertFalse(report.isReadyForSolving());
        assertTrue(report.invalidDiagnostics().contains("clarification-mismatch:gravity-not-updated-in-canonical"));
        assertTrue(report.clarificationDerivedConditions().stream().anyMatch(c -> c.contains("g = 10 m/s^2")));
    }

    // Scenario 5: Explicitly neglected air resistance is not treated as active drag
    @Test
    void treatsExplicitlyNeglectedAirResistanceAsSupportedIdealizationAcrossPhrasings() {
        List<String> phrasings = List.of(
                "A ball is projected at 20 m/s at 45 deg with g = 9.81 m/s^2. Neglect air resistance. Find range.",
                "A ball is projected at 20 m/s at 45 deg with g = 9.81 m/s^2. Assume no air resistance. Find range.",
                "A ball is projected at 20 m/s at 45 deg with g = 9.81 m/s^2. Air resistance is ignored. Find range.");

        for (String question : phrasings) {
            InterpretationRequest req = new InterpretationRequest(question);
            InterpretationDraft draft = new InterpretationDraft(
                    "Find horizontal range without drag",
                    List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                    List.of("air resistance ignored"),
                    List.of(), 0.95);
            CanonicalPhysicsProblem canonical = CanonicalPhysicsProblem.builder(question)
                    .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                    .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                    .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                    .build();

            CompletenessReport report = validator.validate(req, draft, canonical);
            assertEquals(Outcome.READY_FOR_SOLVING, report.outcome(), "Failed for phrasing: " + question);
            assertTrue(report.unsupportedDiagnostics().isEmpty());
            assertTrue(report.modelAssumptions().contains("air-resistance-ignored"));
            assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                    ledgerEntry(report, "air-resistance-ignored").status());
        }
    }

    // Scenario 6: Explicit drag and no-drag contradiction
    @Test
    void rejectsContradictoryDragAndNoDragConstraints() {
        InterpretationRequest req = new InterpretationRequest("Thrown with air resistance and no drag.");
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("air-resistance", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "air resistance"),
                new ExtractedConstraint("no-air-resistance", ConstraintOrigin.INTERPRETER_INFERRED,
                        ConstraintResolution.ASSERTED, "vacuum"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:air-resistance-both-present-and-ignored"));
        assertTrue(report.invalidDiagnostics().contains("contradictory-student-conditions:air-resistance-both-present-and-ignored"));
        assertFalse(report.modelAssumptions().contains("air-resistance-ignored"));
    }

    // Scenario 7: Wind and no-wind contradiction
    @Test
    void rejectsContradictoryWindAndNoWindConstraints() {
        InterpretationRequest req = new InterpretationRequest("Ball launched at 20 m/s at 45 deg.");
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("wind", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "steady crosswind"),
                new ExtractedConstraint("no-wind", ConstraintOrigin.INTERPRETER_INFERRED,
                        ConstraintResolution.ASSERTED, "calm air"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:wind-both-present-and-ignored"));
    }

    // Scenario 8: Spin and no-spin contradiction
    @Test
    void rejectsContradictorySpinAndNoSpinConstraints() {
        InterpretationRequest req = new InterpretationRequest("Ball launched at 20 m/s at 45 deg.");
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("magnus-effect", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "topspin magnus effect"),
                new ExtractedConstraint("no-spin", ConstraintOrigin.INTERPRETER_INFERRED,
                        ConstraintResolution.ASSERTED, "zero spin"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:spin-both-present-and-ignored"));
    }

    // Scenario 9: Bounce and rigid non-bouncing contradiction
    @Test
    void rejectsContradictoryBounceAndRigidNonBouncingConstraints() {
        InterpretationRequest req = new InterpretationRequest("Ball launched at 20 m/s at 45 deg.");
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("bounce-restitution", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "elastic bounce e = 0.8"),
                new ExtractedConstraint("rigid-non-bouncing-contact", ConstraintOrigin.INTERPRETER_INFERRED,
                        ConstraintResolution.ASSERTED, "no bounce"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:bounce-restitution-both-present-and-ignored"));
    }

    // Scenario 10: Variable and constant gravity contradiction
    @Test
    void rejectsContradictoryVariableAndConstantGravityConstraints() {
        InterpretationRequest req = new InterpretationRequest("Ball launched at 20 m/s at 45 deg.");
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("variable-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "altitude-dependent gravity"),
                new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "g = 9.81 m/s^2"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:variable-gravity-both-present-and-ignored"));
        assertFalse(report.modelAssumptions().contains("constant-downward-gravity"));
    }

    // Scenario 11: Contradictory draft assumptions
    @Test
    void rejectsDraftAssumptionsThatContradictExplicitStudentConditions() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 deg with g = 9.81 m/s^2 in air resistance. Find range.");
        InterpretationDraft conflictingAssumptionsDraft = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of("air resistance ignored"),
                List.of(), 0.9);

        CompletenessReport report = validator.validate(req, conflictingAssumptionsDraft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertTrue(report.invalidDiagnostics().contains("contradictory-assumption:air-resistance-ignored"));
        assertTrue(report.invalidDiagnostics().contains("contradictory-constraints:air-resistance-both-present-and-ignored"));
        assertFalse(report.modelAssumptions().contains("air-resistance-ignored"));
    }

    // Scenario 12: Missing or blank source evidence
    @Test
    void rejectsEvidenceFreeStudentOrClarificationConstraintsFromSupportedStatus() {
        InterpretationRequest req = new InterpretationRequest(
                "Ball launched at 20 m/s at 45 deg with g = 9.81 m/s^2. Find range.");
        InterpretationDraft blankEvidenceDraft = draftWithConstraints(
                new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "   "),
                new ExtractedConstraint("no-wind", ConstraintOrigin.CLARIFICATION_ANSWER,
                        ConstraintResolution.ASSERTED, null));

        CompletenessReport report = validator.validate(req, blankEvidenceDraft, validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, report.outcome());
        assertFalse(report.isReadyForSolving());
        assertTrue(report.invalidDiagnostics().contains("missing-constraint-source-evidence:constant-downward-gravity"));
        assertTrue(report.invalidDiagnostics().contains("missing-constraint-source-evidence:no-wind"));
        assertNotEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                ledgerEntry(report, "constant-downward-gravity").status());
        assertNotEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                ledgerEntry(report, "no-wind").status());
    }

    // Scenario 13: INTERPRETER_INFERRED is not included in explicit student conditions
    @Test
    void excludesInterpreterInferredConstraintsFromExplicitStudentConditionsAndSeparatesClarifications() {
        InterpretationRequest req = new InterpretationRequest(
                "Ball launched at 20 m/s at 45 deg with g = 9.81 m/s^2. Find range.",
                List.of(new InterpretationRequest.ClarificationTurn("Is there wind?", "No wind")));
        InterpretationDraft draft = draftWithConstraints(
                new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "g = 9.81 m/s^2"),
                new ExtractedConstraint("no-wind", ConstraintOrigin.CLARIFICATION_ANSWER,
                        ConstraintResolution.ASSERTED, "No wind"),
                new ExtractedConstraint("stationary-inertial-frame", ConstraintOrigin.INTERPRETER_INFERRED,
                        ConstraintResolution.ASSERTED, "standard ground frame"));

        CompletenessReport report = validator.validate(req, draft, validCleanCanonical());
        assertEquals(Outcome.READY_FOR_SOLVING, report.outcome());
        assertTrue(report.explicitStudentConditions().stream()
                .anyMatch(s -> s.contains("constant-downward-gravity")));
        assertTrue(report.explicitStudentConditions().stream()
                .noneMatch(s -> s.contains("stationary-inertial-frame")));
        assertTrue(report.explicitStudentConditions().stream()
                .noneMatch(s -> s.contains("no-wind")));
        assertTrue(report.clarificationDerivedConditions().stream()
                .anyMatch(s -> s.contains("no-wind")));
        assertTrue(report.interpreterInferredConditions().stream()
                .anyMatch(s -> s.contains("stationary-inertial-frame")));
    }

    // Scenario 14: Incorrect target parameter mapping
    @Test
    void detectsTargetParameterValueMismatchAndMissingTargetParameters() {
        InterpretationRequest req = new InterpretationRequest(
                "Ball launched at 20 m/s at 45 deg with g = 9.81 m/s^2. Find velocity at t = 2 s.");
        InterpretationDraft draft = new InterpretationDraft(
                "Find velocity at t = 2 s",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.VELOCITY_AT_TIME,
                        Map.of(ProjectileProblem.Input.TIME, q(2.0, PhysicalUnit.SECOND)))),
                List.of(), List.of(), 0.94);

        CanonicalPhysicsProblem wrongTimeCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(ProjectileProblem.Target.VELOCITY_AT_TIME),
                        Map.of(ProjectileProblem.Input.TIME, q(5.0, PhysicalUnit.SECOND)))
                .build();

        CompletenessReport mismatchReport = validator.validate(req, draft, wrongTimeCanonical);
        assertEquals(Outcome.INVALID_INPUT, mismatchReport.outcome());
        assertTrue(mismatchReport.invalidDiagnostics().contains("canonical-target-parameter-mismatch:VELOCITY_AT_TIME:TIME"));

        CanonicalPhysicsProblem missingTimeCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(ProjectileProblem.Target.VELOCITY_AT_TIME)
                .build();

        CompletenessReport missingParamReport = validator.validate(req, draft, missingTimeCanonical);
        assertEquals(Outcome.INVALID_INPUT, missingParamReport.outcome());
        assertTrue(missingParamReport.invalidDiagnostics().contains("canonical-target-parameter-missing:VELOCITY_AT_TIME:TIME"));
    }

    // Scenario 15: Unsupported and missing conditions coexist with all diagnostics preserved
    @Test
    void preservesAllSimultaneousUnsupportedInvalidAndMissingDiagnosticsAcrossPrecedence() {
        InterpretationRequest req = new InterpretationRequest("Thrown with drag and unknown torque.");
        InterpretationDraft draft = new InterpretationDraft(
                "Incomplete multi-issue problem",
                List.of(),
                List.of(),
                List.of(new InterpretationDraft.Clarification("What is the spin axis?", "Spin direction omitted.", List.of())),
                0.85,
                new SurfaceSpec.Horizontal(0.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.target(ProjectileProblem.Target.HORIZONTAL_RANGE)),
                List.of(
                        new ExtractedConstraint("air-resistance", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "with drag"),
                        new ExtractedConstraint("spin-axis", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.UNRESOLVED_OR_AMBIGUOUS, "unspecified spin"),
                        new ExtractedConstraint("wind-shear-profile", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "shearing wind")),
                List.of());

        CanonicalPhysicsProblem missingGravityCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();
        CompletenessReport unsupportedPreservesMissing = validator.validate(req, draft, missingGravityCanonical);
        assertEquals(Outcome.UNSUPPORTED, unsupportedPreservesMissing.outcome());
        assertTrue(unsupportedPreservesMissing.missingInputs().contains("GRAVITY"));
        assertTrue(unsupportedPreservesMissing.unsupportedDiagnostics().contains("unsupported-constraint:air-resistance"));
        assertTrue(unsupportedPreservesMissing.unsupportedDiagnostics().contains("unknown-unmapped-constraint:wind-shear-profile"));
        assertTrue(unsupportedPreservesMissing.unresolvedConditions().contains("spin-axis"));
        assertTrue(unsupportedPreservesMissing.unresolvedConditions().contains("What is the spin axis?"));

        CanonicalPhysicsProblem invalidGravityCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(-9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();
        CompletenessReport invalidTakesPrecedence = validator.validate(req, draft, invalidGravityCanonical);
        assertEquals(Outcome.INVALID_INPUT, invalidTakesPrecedence.outcome());
        assertTrue(invalidTakesPrecedence.invalidDiagnostics().contains("negative-nonnegative-quantity:GRAVITY"));
        assertTrue(invalidTakesPrecedence.unsupportedDiagnostics().contains("unsupported-constraint:air-resistance"));
    }

    // Scenario 16: Unknown constraint tokens remain blocking
    @Test
    void blocksUnknownConstraintTokensInDraftAndCanonicalWithoutSilentlyIgnoring() {
        InterpretationRequest req = new InterpretationRequest("Launch with relativistic correction.");
        InterpretationDraft draftUnknown = draftWithConstraints(
                new ExtractedConstraint("relativistic-lorentz-drag", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "relativistic correction"));
        CompletenessReport report = validator.validate(req, draftUnknown, validCleanCanonical());
        assertEquals(Outcome.UNSUPPORTED, report.outcome());
        assertTrue(report.unsupportedDiagnostics().contains("unknown-unmapped-constraint:relativistic-lorentz-drag"));
        assertEquals(ConstraintCoverageStatus.EXPLICITLY_UNSUPPORTED,
                ledgerEntry(report, "relativistic-lorentz-drag").status());

        InterpretationDraft draftUnsupportedListUnknown = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.9,
                new SurfaceSpec.Horizontal(0.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(), List.of(),
                List.of("quantum-tunneling-barrier"));
        CompletenessReport unsupportedListReport = validator.validate(req, draftUnsupportedListUnknown, validCleanCanonical());
        assertEquals(Outcome.UNSUPPORTED, unsupportedListReport.outcome());
        assertTrue(unsupportedListReport.unsupportedDiagnostics().contains("unknown-unmapped-constraint:quantum-tunneling-barrier"));
    }

    // Scenario 17: Backward-compatible five-argument InterpretationDraft construction
    @Test
    void supportsBackwardCompatibleFiveArgumentDraftWhileStillRunningOriginalPromptScreen() {
        InterpretationDraft legacyFiveArg = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of("air resistance ignored"),
                List.of(),
                0.96);
        assertEquals(CanonicalPhysicsProblem.AngleReference.HORIZONTAL, legacyFiveArg.angleReference());
        assertNull(legacyFiveArg.surface());

        CompletenessReport validReport = validator.validate(
                new InterpretationRequest("Launched at 20 m/s, 45 deg, g = 9.81 m/s^2; find range."),
                legacyFiveArg,
                validCleanCanonical());
        assertEquals(Outcome.READY_FOR_SOLVING, validReport.outcome());

        // Legacy 5-arg draft still fails if original question includes omitted terminal velocity
        CompletenessReport blockedLegacyReport = validator.validate(
                new InterpretationRequest("Launched at 20 m/s, 45 deg, g = 9.81 m/s^2 approaching terminal velocity; find range."),
                legacyFiveArg,
                validCleanCanonical());
        assertNotEquals(Outcome.READY_FOR_SOLVING, blockedLegacyReport.outcome());
        assertTrue(blockedLegacyReport.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));
    }

    @Test
    void screensAllRemainingUnsupportedCueFamiliesWhenOmittedByInterpreter() {
        List<Map.Entry<String, String>> omittedCueCases = List.of(
                Map.entry("Ball rebounds elastically on impact with ground.", "bounce-restitution"),
                Map.entry("Ball spins with strong Magnus effect in flight.", "spin"),
                Map.entry("Gravity varies with altitude during high launch.", "variable-gravity"),
                Map.entry("Ball slides with friction along the rough ramp before launch.", "sliding-friction"),
                Map.entry("Thrown from inside an accelerating elevator.", "moving-target"),
                Map.entry("Ball lands on a curved terrain profile.", "curved-surface"),
                Map.entry("Two balls collide in mid-air after launch.", "multiple-interacting-bodies"));

        InterpretationDraft minimalDraft = new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.9);

        for (Map.Entry<String, String> cueCase : omittedCueCases) {
            CompletenessReport report = validator.validate(
                    new InterpretationRequest(cueCase.getKey()), minimalDraft, validCleanCanonical());
            assertEquals(Outcome.UNSUPPORTED, report.outcome(), "Expected UNSUPPORTED for: " + cueCase.getKey());
            assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:" + cueCase.getValue()),
                    "Missing incomplete-constraint-extraction for " + cueCase.getValue() + ": " + report.unsupportedDiagnostics());
            assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION,
                    ledgerEntry(report, cueCase.getValue()).status());
        }
    }

    @Test
    void rejectsSurfaceMismatchAndUnresolvedSurfaceCleanly() {
        InterpretationRequest inclineReq = new InterpretationRequest("Ball launched up a 30 deg plane at 25 m/s.");
        InterpretationDraft inclinedDraft = new InterpretationDraft(
                "Range on 30 deg inclined plane",
                List.of(), List.of(), List.of(), 0.92,
                new SurfaceSpec.Inclined(0.0, 0.0, 30.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE),
                List.of(new ExtractedConstraint("inclined-landing-surface", ConstraintOrigin.STUDENT_EXPLICIT,
                        ConstraintResolution.ASSERTED, "30 deg plane")),
                List.of());
        CanonicalPhysicsProblem simplifiedHorizontal = CanonicalPhysicsProblem.builder(inclineReq.question())
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        CompletenessReport mismatchReport = validator.validate(inclineReq, inclinedDraft, simplifiedHorizontal);
        assertEquals(Outcome.INVALID_INPUT, mismatchReport.outcome());
        assertTrue(mismatchReport.invalidDiagnostics().contains("canonical-surface-mismatch:inclined-overridden-by-horizontal"));
        assertEquals(ConstraintCoverageStatus.MISSING_FROM_CANONICAL,
                ledgerEntry(mismatchReport, "inclined-landing-surface").status());

        InterpretationRequest rampReq = new InterpretationRequest("Thrown from ramp at 20 m/s. Find range.");
        InterpretationDraft unresolvedDraft = new InterpretationDraft(
                "Thrown from ramp",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.8,
                new SurfaceSpec.Unresolved("Ramp slope angle not stated", "from ramp"),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(), List.of(), List.of());

        CompletenessReport unresolvedReport = validator.validate(rampReq, unresolvedDraft, validCleanCanonical());
        assertEquals(Outcome.INSUFFICIENT_INFORMATION, unresolvedReport.outcome());
        assertEquals(ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                ledgerEntry(unresolvedReport, "landing-surface").status());
    }

    private static InterpretationDraft draftWithConstraints(ExtractedConstraint... constraints) {
        return new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(),
                List.of(),
                0.93,
                new SurfaceSpec.Horizontal(0.0),
                CanonicalPhysicsProblem.AngleReference.HORIZONTAL,
                List.of(),
                List.of(constraints),
                List.of());
    }

    private static CanonicalPhysicsProblem validCleanCanonical() {
        return CanonicalPhysicsProblem.builder("Standard horizontal projectile")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();
    }

    private static ConstraintCoverage ledgerEntry(CompletenessReport report, String identifier) {
        return report.constraintLedger().stream()
                .filter(entry -> entry.identifier().equals(identifier))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing ledger entry for " + identifier + ": " + report.constraintLedger()));
    }

    private static PhysicalQuantity q(double value, PhysicalUnit unit) {
        return new PhysicalQuantity(value, unit);
    }
}
