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

    // Canonical representation requirement (Audit Gate Case #3): no empty validationErrors fallback!
    @Test
    void rejectsSupportedConstraintWhenMissingFromCanonicalMetadataEvenWithZeroCanonicalValidationErrors() {
        List<Map.Entry<String, String>> requiredCanonicalRepresentations = List.of(
                Map.entry("air-resistance-ignored", "neglect air resistance"),
                Map.entry("no-wind", "no wind"),
                Map.entry("no-spin", "without spin"),
                Map.entry("rigid-non-bouncing-contact", "no bounce"),
                Map.entry("stationary-inertial-frame", "stationary frame"));

        for (Map.Entry<String, String> entry : requiredCanonicalRepresentations) {
            String constraintId = entry.getKey();
            String phrase = entry.getValue();
            InterpretationRequest req = new InterpretationRequest(
                    "Ball projected at 20 m/s, 45 deg, g = 9.81 m/s^2, " + phrase + ". Find range.");
            InterpretationDraft draft = draftWithConstraints(
                    new ExtractedConstraint(constraintId, ConstraintOrigin.STUDENT_EXPLICIT,
                            ConstraintResolution.ASSERTED, phrase));

            // Canonical has NO validation errors, yet has NO constraint/assumption metadata for constraintId
            CanonicalPhysicsProblem emptyMetadataCanonical = validCleanCanonical();
            assertTrue(emptyMetadataCanonical.validationErrors().isEmpty());

            CompletenessReport missingReport = validator.validate(req, draft, emptyMetadataCanonical);
            assertEquals(Outcome.INVALID_INPUT, missingReport.outcome(),
                    "Should reject missing canonical metadata for " + constraintId);
            assertFalse(missingReport.isReadyForSolving());
            assertTrue(missingReport.invalidDiagnostics().contains("constraint-missing-from-canonical:" + constraintId));
            ConstraintCoverage missingEntry = ledgerEntry(missingReport, constraintId);
            assertEquals(ConstraintCoverageStatus.MISSING_FROM_CANONICAL, missingEntry.status());
            assertEquals("None", missingEntry.canonicalRepresentation());

            // Genuinely represented via canonical constraint metadata -> accepted
            CanonicalPhysicsProblem representedViaConstraint = CanonicalPhysicsProblem.builder(req.question())
                    .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                    .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                    .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                    .addConstraint(constraintId)
                    .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                    .build();
            CompletenessReport acceptedByConstraint = validator.validate(req, draft, representedViaConstraint);
            assertEquals(Outcome.READY_FOR_SOLVING, acceptedByConstraint.outcome(),
                    "Expected READY_FOR_SOLVING when constraint attached for " + constraintId);
            assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                    ledgerEntry(acceptedByConstraint, constraintId).status());
            assertEquals("ConstraintMetadata(" + constraintId + ")",
                    ledgerEntry(acceptedByConstraint, constraintId).canonicalRepresentation());

            // Genuinely represented via explicit canonical model assumption -> accepted
            CanonicalPhysicsProblem representedViaAssumption = CanonicalPhysicsProblem.builder(req.question())
                    .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                    .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                    .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                    .addAssumption(constraintId)
                    .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                    .build();
            CompletenessReport acceptedByAssumption = validator.validate(req, draft, representedViaAssumption);
            assertEquals(Outcome.READY_FOR_SOLVING, acceptedByAssumption.outcome());
            assertEquals("ExplicitModelAssumption(" + constraintId + ")",
                    ledgerEntry(acceptedByAssumption, constraintId).canonicalRepresentation());
        }
    }

    // Evidence provenance requirement: fabricated, unrelated, and misattributed evidence must fail
    @Test
    void enforcesGroundedEvidenceOriginAndConditionCorrespondence() {
        InterpretationRequest req = new InterpretationRequest(
                "Ball projected at 20 m/s, 45 deg, g = 9.81 m/s^2 with no wind. Find range.",
                List.of(
                        new InterpretationRequest.ClarificationTurn("Is gravity different?", "g = 9.81 m/s^2"),
                        new InterpretationRequest.ClarificationTurn("Is spin present?", "No spin during flight.")));

        CanonicalPhysicsProblem canonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addConstraint("no-wind")
                .addConstraint("no-spin")
                .addConstraint("constant-downward-gravity")
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                .build();

        // 1. Null and blank evidence
        CompletenessReport blankReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-wind", ConstraintOrigin.STUDENT_EXPLICIT, ConstraintResolution.ASSERTED, "   "),
                new ExtractedConstraint("no-spin", ConstraintOrigin.CLARIFICATION_ANSWER, ConstraintResolution.ASSERTED, null)),
                canonical);
        assertEquals(Outcome.INVALID_INPUT, blankReport.outcome());
        assertTrue(blankReport.invalidDiagnostics().contains("missing-constraint-source-evidence:no-wind"));
        assertTrue(blankReport.invalidDiagnostics().contains("missing-constraint-source-evidence:no-spin"));

        // 2. Fabricated "xyz" / hallucinated quotes
        CompletenessReport fabricatedReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-wind", ConstraintOrigin.STUDENT_EXPLICIT, ConstraintResolution.ASSERTED, "xyz"),
                new ExtractedConstraint("no-spin", ConstraintOrigin.CLARIFICATION_ANSWER, ConstraintResolution.ASSERTED, "student whispered no spin")),
                canonical);
        assertEquals(Outcome.INVALID_INPUT, fabricatedReport.outcome());
        assertTrue(fabricatedReport.invalidDiagnostics().contains("ungrounded-constraint-source-evidence:no-wind"));
        assertTrue(fabricatedReport.invalidDiagnostics().contains("ungrounded-constraint-source-evidence:no-spin"));
        assertNotEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, ledgerEntry(fabricatedReport, "no-wind").status());

        // 3. Unrelated substring copied from original question ("at 20 m/s" for "no-wind")
        CompletenessReport unrelatedQuoteReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-wind", ConstraintOrigin.STUDENT_EXPLICIT, ConstraintResolution.ASSERTED, "at 20 m/s")),
                canonical);
        assertEquals(Outcome.INVALID_INPUT, unrelatedQuoteReport.outcome());
        assertTrue(unrelatedQuoteReport.invalidDiagnostics().contains("unrelated-constraint-source-evidence:no-wind"));
        assertNotEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, ledgerEntry(unrelatedQuoteReport, "no-wind").status());

        // 4. Question text wrongly labeled CLARIFICATION_ANSWER, and clarification answer wrongly labeled STUDENT_EXPLICIT
        CompletenessReport wrongOriginReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-wind", ConstraintOrigin.CLARIFICATION_ANSWER, ConstraintResolution.ASSERTED, "no wind"),
                new ExtractedConstraint("no-spin", ConstraintOrigin.STUDENT_EXPLICIT, ConstraintResolution.ASSERTED, "No spin during flight")),
                canonical);
        assertEquals(Outcome.INVALID_INPUT, wrongOriginReport.outcome());
        assertTrue(wrongOriginReport.invalidDiagnostics().contains("ungrounded-constraint-source-evidence:no-wind"));
        assertTrue(wrongOriginReport.invalidDiagnostics().contains("ungrounded-constraint-source-evidence:no-spin"));

        // 5. Evidence copied from another clarification turn ("g = 9.81 m/s^2" from Turn 1 claimed for "no-spin")
        CompletenessReport crossTurnReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-spin", ConstraintOrigin.CLARIFICATION_ANSWER, ConstraintResolution.ASSERTED, "g = 9.81 m/s^2")),
                canonical);
        assertEquals(Outcome.INVALID_INPUT, crossTurnReport.outcome());
        assertTrue(crossTurnReport.invalidDiagnostics().contains("ungrounded-constraint-source-evidence:no-spin"));

        // 6. Valid grounded question + clarification evidence succeeds
        CompletenessReport validReport = validator.validate(req, draftWithConstraints(
                new ExtractedConstraint("no-wind", ConstraintOrigin.STUDENT_EXPLICIT, ConstraintResolution.ASSERTED, "with no wind"),
                new ExtractedConstraint("no-spin", ConstraintOrigin.CLARIFICATION_ANSWER, ConstraintResolution.ASSERTED, "No spin during flight.")),
                canonical);
        assertEquals(Outcome.READY_FOR_SOLVING, validReport.outcome());
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, ledgerEntry(validReport, "no-wind").status());
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, ledgerEntry(validReport, "no-spin").status());
    }

    // Audit Gate Case #1 & Requirement 3: All 13 negation & multi-condition clause cases
    @Test
    void parsesLocalNegationAndConjunctionScopeWithoutLeakingAcrossConditions() {
        // Gate Case #1: "Air resistance is neglected and wind is present." -> NO_AIR_RESISTANCE + active WIND
        CompletenessReport gateCase = validator.validate(
                new InterpretationRequest("A ball is thrown at 20 m/s at 45 deg with g = 9.81 m/s^2. Air resistance is neglected and wind is present. Find range."),
                simpleRangeDraft(),
                canonicalWithAssumptions("air-resistance-ignored"));
        assertEquals(Outcome.UNSUPPORTED, gateCase.outcome());
        assertTrue(gateCase.unsupportedDiagnostics().contains("incomplete-constraint-extraction:wind"));
        assertTrue(gateCase.unsupportedDiagnostics().contains("unsupported-constraint:wind"));
        assertFalse(gateCase.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));
        assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION, ledgerEntry(gateCase, "wind").status());
        assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED, ledgerEntry(gateCase, "air-resistance-ignored").status());

        // Cases expected to yield ACTIVE AIR_RESISTANCE (and/or WIND) as UNSUPPORTED
        record ExpectedCueCase(String text, String expectedActiveId, String expectedNegatedId) {}
        List<ExpectedCueCase> multiConditionCases = List.of(
                new ExpectedCueCase("Do not neglect air resistance and ignore wind.", "air-resistance", "no-wind"),
                new ExpectedCueCase("Air resistance is ignored and wind is present.", "wind", "air-resistance-ignored"),
                new ExpectedCueCase("Air resistance is present and wind is ignored.", "air-resistance", "no-wind"),
                new ExpectedCueCase("Spin is not negligible and neglect air resistance.", "spin", "air-resistance-ignored"),
                new ExpectedCueCase("Air resistance is not negligible.", "air-resistance", null),
                new ExpectedCueCase("Air resistance cannot be neglected.", "air-resistance", null),
                new ExpectedCueCase("Wind should not be ignored.", "wind", null),
                new ExpectedCueCase("Neglect air resistance, but consider wind.", "wind", "air-resistance-ignored"),
                new ExpectedCueCase("Without air resistance, the projectile experiences wind.", "wind", "air-resistance-ignored"),
                new ExpectedCueCase("Do not ignore wind.", "wind", null));

        for (ExpectedCueCase c : multiConditionCases) {
            String fullQuestion = "Ball launched at 20 m/s, 45 deg, g = 9.81 m/s^2. " + c.text() + " Find range.";
            CanonicalPhysicsProblem caseCanonical = c.expectedNegatedId() != null
                    ? canonicalWithAssumptions(c.expectedNegatedId())
                    : validCleanCanonical();
            CompletenessReport report = validator.validate(
                    new InterpretationRequest(fullQuestion), simpleRangeDraft(), caseCanonical);
            assertEquals(Outcome.UNSUPPORTED, report.outcome(), "Expected UNSUPPORTED for: " + c.text()
                    + " invalid=" + report.invalidDiagnostics());
            assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:" + c.expectedActiveId()),
                    "Expected omitted active " + c.expectedActiveId + " for [" + c.text() + "] but got " + report.unsupportedDiagnostics());
            if (c.expectedNegatedId() != null) {
                assertEquals(ConstraintCoverageStatus.REPRESENTED_AND_SUPPORTED,
                        ledgerEntry(report, c.expectedNegatedId()).status(),
                        "Expected negated idealization " + c.expectedNegatedId() + " for: " + c.text());
            }
        }

        // Explicitly negated single conditions -> READY_FOR_SOLVING (never false-positive UNSUPPORTED)
        CanonicalPhysicsProblem canonicalIdeal = canonicalWithAssumptions(
                "air-resistance-ignored", "no-wind", "rigid-non-bouncing-contact");
        for (String negatedQuestion : List.of(
                "Ball thrown at 20 m/s, 45 deg, g = 9.81 m/s^2. The ball does not bounce. Find range.",
                "Ball thrown at 20 m/s, 45 deg, g = 9.81 m/s^2. Wind is not present. Find range.",
                "Ball thrown at 20 m/s, 45 deg, g = 9.81 m/s^2. Neglect air resistance and wind. Find range.",
                "Ball thrown at 20 m/s, 45 deg, g = 9.81 m/s^2. Air resistance and wind are neglected. Find range.")) {
            CompletenessReport report = validator.validate(
                    new InterpretationRequest(negatedQuestion), simpleRangeDraft(), canonicalIdeal);
            assertEquals(Outcome.READY_FOR_SOLVING, report.outcome(),
                    "Expected READY_FOR_SOLVING for negated question: " + negatedQuestion
                            + " diagnostics=" + report.unsupportedDiagnostics() + " invalid=" + report.invalidDiagnostics());
            assertTrue(report.unsupportedDiagnostics().isEmpty());
        }

        // Clarification answer "Cannot be neglected." / "Should not be ignored." resolves active effect
        CompletenessReport clarDoubleNeg = validator.validate(
                new InterpretationRequest("Launched at 20 m/s, 45 deg, g = 9.81 m/s^2. Find range.",
                        List.of(new InterpretationRequest.ClarificationTurn(
                                "Can air resistance be neglected?", "Cannot be neglected."))),
                simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.UNSUPPORTED, clarDoubleNeg.outcome());
        assertTrue(clarDoubleNeg.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));

        CompletenessReport clarShouldNotIgnore = validator.validate(
                new InterpretationRequest("Launched at 20 m/s, 45 deg, g = 9.81 m/s^2. Find range.",
                        List.of(new InterpretationRequest.ClarificationTurn(
                                "Should wind be ignored?", "Should not be ignored."))),
                simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.UNSUPPORTED, clarShouldNotIgnore.outcome());
        assertTrue(clarShouldNotIgnore.unsupportedDiagnostics().contains("incomplete-constraint-extraction:wind"));

        // Uncertain polarity yields INSUFFICIENT_INFORMATION instead of silent guess
        CompletenessReport ambiguousPolarity = validator.validate(
                new InterpretationRequest("Launched at 20 m/s, 45 deg, g = 9.81 m/s^2. Unclear whether air resistance applies. Find range."),
                simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.INSUFFICIENT_INFORMATION, ambiguousPolarity.outcome());
        assertTrue(ambiguousPolarity.insufficientDiagnostics().contains("ambiguous-constraint-polarity:air-resistance"));
        assertEquals(ConstraintCoverageStatus.UNRESOLVED_OR_AMBIGUOUS,
                ledgerEntry(ambiguousPolarity, "air-resistance").status());
    }

    // Audit Gate Case #2 & Requirement 4: Clarification gravity parsing & multi-turn consistency
    @Test
    void parsesExplicitGravityClarificationsSafelyWithoutMisreadingUnrelatedNumbersOrPartScopedNotes() {
        // Gate Case #2 & unrelated numerical answers must NOT trigger false gravity mismatches
        List<InterpretationRequest.ClarificationTurn> safeNonGravityTurns = List.of(
                new InterpretationRequest.ClarificationTurn("In Earth gravity, how long does the flight take?", "3 seconds"),
                new InterpretationRequest.ClarificationTurn("How long is the flight?", "The flight lasts 5 seconds"),
                new InterpretationRequest.ClarificationTurn("Clarify part 2 acceleration?", "Part 2 uses 10 m/s²"),
                new InterpretationRequest.ClarificationTurn("What is the height clearance?", "Above 25 m"));

        for (InterpretationRequest.ClarificationTurn turn : safeNonGravityTurns) {
            InterpretationRequest req = new InterpretationRequest(
                    "Ball thrown at 20 m/s, 45 deg, g = 9.81 m/s^2. Find range.",
                    List.of(turn));
            CompletenessReport report = validator.validate(req, simpleRangeDraft(), validCleanCanonical());
            assertEquals(Outcome.READY_FOR_SOLVING, report.outcome(),
                    "Should NOT misread [" + turn.question() + " -> " + turn.answer() + "] as gravity mismatch; invalid="
                            + report.invalidDiagnostics());
        }

        // Ambiguous answer to explicit gravity question yields INSUFFICIENT_INFORMATION rather than silent override
        CompletenessReport unresolvedGrav = validator.validate(
                new InterpretationRequest("Ball thrown at 20 m/s, 45 deg. Find range.",
                        List.of(new InterpretationRequest.ClarificationTurn(
                                "What value of gravity should be used?", "Part 2 uses 10 m/s²"))),
                simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.INSUFFICIENT_INFORMATION, unresolvedGrav.outcome());
        assertTrue(unresolvedGrav.insufficientDiagnostics().contains("unresolved-clarification-quantity:gravity"));

        // Explicit positive gravity labels/units parsed cleanly
        for (String explicitAnswer : List.of(
                "g = 10 m/s^2",
                "gravity is 10 m/s²",
                "Use 10 metres per second squared")) {
            InterpretationRequest staleReq = new InterpretationRequest(
                    "Ball thrown at 20 m/s, 45 deg. Find range.",
                    List.of(new InterpretationRequest.ClarificationTurn(
                            "What value of gravity should be used?", explicitAnswer)));
            CompletenessReport staleReport = validator.validate(staleReq, simpleRangeDraft(), validCleanCanonical());
            assertEquals(Outcome.INVALID_INPUT, staleReport.outcome());
            assertTrue(staleReport.invalidDiagnostics().contains("clarification-mismatch:gravity-not-updated-in-canonical"),
                    "Failed to detect stale canonical for: " + explicitAnswer);

            CanonicalPhysicsProblem updatedCanonical = CanonicalPhysicsProblem.builder(staleReq.question())
                    .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                    .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                    .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                    .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE)
                    .build();
            CompletenessReport syncedReport = validator.validate(staleReq, simpleRangeDraft(), updatedCanonical);
            assertEquals(Outcome.READY_FOR_SOLVING, syncedReport.outcome());
        }

        // Conflicting multi-turn gravity clarifications without explicit correction -> rejected even if canonical matches later turn
        InterpretationRequest conflictingTurnsReq = new InterpretationRequest(
                "Ball thrown at 20 m/s, 45 deg. Find range.",
                List.of(
                        new InterpretationRequest.ClarificationTurn("Gravity value?", "g = 10 m/s^2"),
                        new InterpretationRequest.ClarificationTurn("Confirm gravity?", "g = 9.81 m/s^2")));
        CompletenessReport conflictingTurnsReport = validator.validate(
                conflictingTurnsReq, simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, conflictingTurnsReport.outcome());
        assertTrue(conflictingTurnsReport.invalidDiagnostics().contains("conflicting-clarification-answers:gravity"));

        // Explicit multi-turn correction accepted when canonical adopts the corrected value
        InterpretationRequest correctedTurnReq = new InterpretationRequest(
                "Ball thrown at 20 m/s, 45 deg. Find range.",
                List.of(
                        new InterpretationRequest.ClarificationTurn("Gravity value?", "g = 10 m/s^2"),
                        new InterpretationRequest.ClarificationTurn("Confirm gravity?", "Actually, change g to 9.81 m/s^2 instead")));
        CompletenessReport correctedReport = validator.validate(
                correctedTurnReq, simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.READY_FOR_SOLVING, correctedReport.outcome());
    }

    // Bidirectional target coverage, duplicate target preservation, and cross-unit conversion
    @Test
    void enforcesBidirectionalTargetConsistencyDuplicateTargetsAndCompatibleUnitEquivalence() {
        InterpretationRequest req = new InterpretationRequest(
                "Ball thrown at 36 km/h at PI/4 rad with g = 9.81 m/s^2. Find velocity at 2000 ms.");

        // 1. Cross-unit equivalence accepted (36 km/h == 10 m/s, PI/4 rad == 45 deg, 2000 ms == 2 s)
        InterpretationDraft unitDraft = new InterpretationDraft(
                "Find velocity at 2 s",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.VELOCITY_AT_TIME,
                        Map.of(
                                ProjectileProblem.Input.INITIAL_SPEED, q(36.0, PhysicalUnit.KILOMETRE_PER_HOUR),
                                ProjectileProblem.Input.LAUNCH_ANGLE, q(Math.PI / 4.0, PhysicalUnit.RADIAN),
                                ProjectileProblem.Input.TIME, q(2000.0, PhysicalUnit.MILLISECOND)))),
                List.of(), List.of(), 0.95);
        CanonicalPhysicsProblem siCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(10.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(ProjectileProblem.Target.VELOCITY_AT_TIME),
                        Map.of(ProjectileProblem.Input.TIME, q(2.0, PhysicalUnit.SECOND)))
                .build();
        assertEquals(Outcome.READY_FOR_SOLVING, validator.validate(req, unitDraft, siCanonical).outcome());

        // 2. Reverse target check rejects extra unrequested target in canonical
        CanonicalPhysicsProblem extraTargetCanonical = CanonicalPhysicsProblem.builder(req.question())
                .polarVelocity(q(10.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(ProjectileProblem.Target.VELOCITY_AT_TIME),
                        Map.of(ProjectileProblem.Input.TIME, q(2.0, PhysicalUnit.SECOND)))
                .addTarget(ProjectileProblem.Target.MAXIMUM_HEIGHT)
                .build();
        CompletenessReport extraReport = validator.validate(req, unitDraft, extraTargetCanonical);
        assertEquals(Outcome.INVALID_INPUT, extraReport.outcome());
        assertTrue(extraReport.invalidDiagnostics().contains("canonical-extra-unrequested-target:MAXIMUM_HEIGHT"));

        // 3. Legitimate repeated targets preserved when both match; extra canonical duplicate rejected
        InterpretationDraft twoTimesDraft = new InterpretationDraft(
                "Find velocity at t=1 s and t=3 s",
                List.of(
                        new InterpretationDraft.ProblemPart(ProjectileProblem.Target.VELOCITY_AT_TIME,
                                Map.of(ProjectileProblem.Input.TIME, q(1.0, PhysicalUnit.SECOND))),
                        new InterpretationDraft.ProblemPart(ProjectileProblem.Target.VELOCITY_AT_TIME,
                                Map.of(ProjectileProblem.Input.TIME, q(3.0, PhysicalUnit.SECOND)))),
                List.of(), List.of(), 0.95);
        CanonicalPhysicsProblem twoTimesCanonical = CanonicalPhysicsProblem.builder("Two times")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(ProjectileProblem.Target.VELOCITY_AT_TIME),
                        Map.of(ProjectileProblem.Input.TIME, q(1.0, PhysicalUnit.SECOND)))
                .addTarget(CanonicalPhysicsProblem.target(ProjectileProblem.Target.VELOCITY_AT_TIME),
                        Map.of(ProjectileProblem.Input.TIME, q(3.0, PhysicalUnit.SECOND)))
                .build();
        assertEquals(Outcome.READY_FOR_SOLVING, validator.validate(
                new InterpretationRequest("Velocity at 1 s and 3 s"), twoTimesDraft, twoTimesCanonical).outcome());

        // Single draft target paired against duplicate canonical part rejects unrequested duplicate
        CompletenessReport extraDupReport = validator.validate(
                new InterpretationRequest("Velocity at 1 s"),
                new InterpretationDraft("One time", List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.VELOCITY_AT_TIME,
                        Map.of(ProjectileProblem.Input.TIME, q(1.0, PhysicalUnit.SECOND)))),
                        List.of(), List.of(), 0.95),
                twoTimesCanonical);
        assertEquals(Outcome.INVALID_INPUT, extraDupReport.outcome());
        assertTrue(extraDupReport.invalidDiagnostics().contains("canonical-extra-unrequested-target:VELOCITY_AT_TIME"));

        // 4. Shared given mismatch rejected
        InterpretationDraft wrongGravityGiven = new InterpretationDraft(
                "Find velocity at 2 s",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.VELOCITY_AT_TIME,
                        Map.of(ProjectileProblem.Input.GRAVITY, q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED)))),
                List.of(), List.of(), 0.95);
        CompletenessReport givenMismatch = validator.validate(req, wrongGravityGiven, siCanonical);
        assertEquals(Outcome.INVALID_INPUT, givenMismatch.outcome());
        assertTrue(givenMismatch.invalidDiagnostics().contains("canonical-given-parameter-mismatch:GRAVITY"));
    }

    // Lexical cue additions & false-positive prevention (Requirement 6)
    @Test
    void screensNewAirFrictionTerminalSpeedWindPluralsViscousDampingAndSlopeRampCues() {
        List<Map.Entry<String, String>> omittedCues = List.of(
                Map.entry("Ball experiences air friction during flight.", "air-resistance"),
                Map.entry("Projectile approaches terminal speed before impact.", "air-resistance"),
                Map.entry("Motion is slowed by viscous damping.", "air-resistance"),
                Map.entry("Strong crosswinds push the projectile off course.", "wind"),
                Map.entry("Headwinds reduce the projectile range.", "wind"),
                Map.entry("Tailwinds carry the ball farther.", "wind"));

        for (Map.Entry<String, String> cue : omittedCues) {
            CompletenessReport report = validator.validate(
                    new InterpretationRequest("Ball at 20 m/s, 45 deg, g = 9.81 m/s^2. " + cue.getKey()),
                    simpleRangeDraft(), validCleanCanonical());
            assertEquals(Outcome.UNSUPPORTED, report.outcome(), "Expected UNSUPPORTED for: " + cue.getKey());
            assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:" + cue.getValue()));
            // Ensure "air friction" was classified as air-resistance, NOT sliding-friction!
            assertFalse(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:sliding-friction"),
                    "Air friction misclassified as sliding-friction for: " + cue.getKey());
            assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION,
                    ledgerEntry(report, cue.getValue()).status());
        }

        // Explicitly negated "air friction" and "viscous damping" must NOT trigger active unsupported drag
        CompletenessReport negatedAirFriction = validator.validate(
                new InterpretationRequest("Ball at 20 m/s, 45 deg, g = 9.81 m/s^2. Neglect air friction and viscous damping. Find range."),
                simpleRangeDraft(), canonicalWithAssumptions("air-resistance-ignored"));
        assertEquals(Outcome.READY_FOR_SOLVING, negatedAirFriction.outcome());
        assertTrue(negatedAirFriction.unsupportedDiagnostics().isEmpty());

        // Common slope/ramp phrasing omitted from horizontal canonical -> blocked!
        for (String slopePrompt : List.of(
                "Ball projected at 20 m/s up a 30 degree slope. Find range.",
                "Ball launched at 20 m/s on a ramp with 30 deg angle. Find range.")) {
            CompletenessReport omittedSlope = validator.validate(
                    new InterpretationRequest(slopePrompt), simpleRangeDraft(), validCleanCanonical());
            assertEquals(Outcome.INVALID_INPUT, omittedSlope.outcome());
            assertTrue(omittedSlope.invalidDiagnostics().contains("incomplete-constraint-extraction:inclined-landing-surface"),
                    "Expected omitted inclined-landing-surface for: " + slopePrompt);
        }
    }

    // Scenario 1: Original question mentions air resistance, draft omits it
    @Test
    void blocksSolvingWhenOriginalQuestionMentionsAirResistanceAndDraftOmitsIt() {
        InterpretationRequest req = new InterpretationRequest(
                "A ball is projected from an inclined plane at 25 m/s and 45 deg with g = 10 m/s^2. It experiences air resistance. Calculate its range.");
        InterpretationDraft omittedDragDraft = new InterpretationDraft(
                "Calculate range along 30 deg inclined plane",
                List.of(), List.of(), List.of(), 0.93,
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
        CompletenessReport report = validator.validate(req, simpleRangeDraft(), validCleanCanonical());
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
        CompletenessReport report = validator.validate(req, simpleRangeDraft(), validCleanCanonical());
        assertEquals(Outcome.UNSUPPORTED, report.outcome());
        assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:air-resistance"));
        assertTrue(report.clarificationDerivedConditions().stream().anyMatch(c -> c.contains("air-resistance")));

        ConstraintCoverage entry = ledgerEntry(report, "air-resistance");
        assertEquals(ConstraintCoverageStatus.OMITTED_FROM_INTERPRETATION, entry.status());
        assertEquals(ConstraintOrigin.CLARIFICATION_ANSWER, entry.origin());
        assertFalse(entry.extracted());
    }

    // Scenarios 6-10: Contradictions across all 5 opposing pairs + draft assumptions
    @Test
    void rejectsContradictoryConstraintPairsAcrossAllRequiredOpposites() {
        CompletenessReport dragConflict = validator.validate(
                new InterpretationRequest("Thrown with air resistance and vacuum."),
                draftWithConstraints(
                        new ExtractedConstraint("air-resistance", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "air resistance"),
                        new ExtractedConstraint("no-air-resistance", ConstraintOrigin.INTERPRETER_INFERRED,
                                ConstraintResolution.ASSERTED, "vacuum")),
                validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, dragConflict.outcome());
        assertTrue(dragConflict.invalidDiagnostics().contains("contradictory-constraints:air-resistance-both-present-and-ignored"));
        assertFalse(dragConflict.modelAssumptions().contains("air-resistance-ignored"));

        CompletenessReport windConflict = validator.validate(
                new InterpretationRequest("Ball launched at 20 m/s at 45 deg in steady crosswind."),
                draftWithConstraints(
                        new ExtractedConstraint("wind", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "steady crosswind"),
                        new ExtractedConstraint("no-wind", ConstraintOrigin.INTERPRETER_INFERRED,
                                ConstraintResolution.ASSERTED, "calm air")),
                validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, windConflict.outcome());
        assertTrue(windConflict.invalidDiagnostics().contains("contradictory-constraints:wind-both-present-and-ignored"));

        CompletenessReport spinConflict = validator.validate(
                new InterpretationRequest("Ball launched at 20 m/s with topspin magnus effect."),
                draftWithConstraints(
                        new ExtractedConstraint("magnus-effect", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "topspin magnus effect"),
                        new ExtractedConstraint("no-spin", ConstraintOrigin.INTERPRETER_INFERRED,
                                ConstraintResolution.ASSERTED, "zero spin")),
                validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, spinConflict.outcome());
        assertTrue(spinConflict.invalidDiagnostics().contains("contradictory-constraints:spin-both-present-and-ignored"));

        CompletenessReport bounceConflict = validator.validate(
                new InterpretationRequest("Ball launched at 20 m/s with elastic bounce e = 0.8."),
                draftWithConstraints(
                        new ExtractedConstraint("bounce-restitution", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "elastic bounce"),
                        new ExtractedConstraint("rigid-non-bouncing-contact", ConstraintOrigin.INTERPRETER_INFERRED,
                                ConstraintResolution.ASSERTED, "no bounce")),
                validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, bounceConflict.outcome());
        assertTrue(bounceConflict.invalidDiagnostics().contains("contradictory-constraints:bounce-restitution-both-present-and-ignored"));

        CompletenessReport gravityConflict = validator.validate(
                new InterpretationRequest("Ball launched with altitude-dependent gravity and g = 9.81 m/s^2."),
                draftWithConstraints(
                        new ExtractedConstraint("variable-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "altitude-dependent gravity"),
                        new ExtractedConstraint("constant-downward-gravity", ConstraintOrigin.STUDENT_EXPLICIT,
                                ConstraintResolution.ASSERTED, "g = 9.81 m/s^2")),
                validCleanCanonical());
        assertEquals(Outcome.INVALID_INPUT, gravityConflict.outcome());
        assertTrue(gravityConflict.invalidDiagnostics().contains("contradictory-constraints:variable-gravity-both-present-and-ignored"));
        assertFalse(gravityConflict.modelAssumptions().contains("constant-downward-gravity"));
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

    // Scenario 13: INTERPRETER_INFERRED excluded from explicit student conditions & provenance buckets separated
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

        CanonicalPhysicsProblem representedCanonical = canonicalWithAssumptions("no-wind", "stationary-inertial-frame");
        CompletenessReport report = validator.validate(req, draft, representedCanonical);
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

    // Scenario 15: Simultaneous diagnostics preserved across precedence
    @Test
    void preservesAllSimultaneousUnsupportedInvalidAndMissingDiagnosticsAcrossPrecedence() {
        InterpretationRequest req = new InterpretationRequest("Thrown with drag, shearing wind, and unspecified spin.");
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
        assertTrue(invalidTakesPrecedence.unresolvedConditions().contains("spin-axis"));
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

    // Scenario 17: Backward-compatible 5-arg draft
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

        for (Map.Entry<String, String> cueCase : omittedCueCases) {
            CompletenessReport report = validator.validate(
                    new InterpretationRequest(cueCase.getKey()), simpleRangeDraft(), validCleanCanonical());
            assertEquals(Outcome.UNSUPPORTED, report.outcome(), "Expected UNSUPPORTED for: " + cueCase.getKey());
            assertTrue(report.unsupportedDiagnostics().contains("incomplete-constraint-extraction:" + cueCase.getValue()));
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

    private static InterpretationDraft simpleRangeDraft() {
        return new InterpretationDraft(
                "Find horizontal range",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, Map.of())),
                List.of(), List.of(), 0.94);
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

    private static CanonicalPhysicsProblem canonicalWithAssumptions(String... assumptions) {
        CanonicalPhysicsProblem.Builder builder = CanonicalPhysicsProblem.builder("Horizontal projectile with explicit assumptions")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addTarget(ProjectileProblem.Target.HORIZONTAL_RANGE);
        for (String a : assumptions) {
            builder.addAssumption(a);
        }
        return builder.build();
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
