package edu.physics.ai;

import edu.physics.problem.CanonicalPhysicsProblem;
import edu.physics.problem.CanonicalPhysicsProblem.AngleReference;
import edu.physics.problem.CanonicalPhysicsProblem.SurfaceTarget;
import edu.physics.problem.CanonicalSolution;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence.ResultReference;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AIProblemWorkflowTest {

    // --- Area 13: Existing legacy workflow regression tests ---

    @Test
    void endToEndPipelineUsesTheSolverOutputAndOnlyExplainsVerifiedResults() {
        var provider = new FakeProvider(CompletableFuture.completedFuture(new ProviderResult<>(
                draft(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens()), "interpret-1", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Principle", List.of(new ExplanationDraft.Claim(
                                "Horizontal velocity stays constant.", null))),
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The required horizontal range is shown below.", "range"))))), "explain-1", "fake", null)));
        var workflow = workflow(provider);

        var result = workflow.submit(new InterpretationRequest("Find the range for a 20 m/s launch at 45 degrees."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertEquals(ProjectileSolution.Status.SOLVED, result.orchestration().solution().status());
        assertTrue(result.explanationVerification().verified());
        assertEquals(1, provider.explanationCalls);
    }

    @Test
    void ambiguousInputStopsBeforeSolverExplanationCall() {
        var ambiguity = new InterpretationDraft.Clarification("What does distance mean?", "It may refer to horizontal displacement or path length.",
                List.of("horizontal displacement", "distance along the trajectory"));
        var provider = new FakeProvider(CompletableFuture.completedFuture(new ProviderResult<>(
                new InterpretationDraft("distance query", List.of(), List.of(), List.of(ambiguity), 0.6),
                "interpret-2", "fake", null)), CompletableFuture.completedFuture(null));

        var result = workflow(provider).submit(new InterpretationRequest("Find speed at distance 10 m."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.NEEDS_CLARIFICATION, result.status());
        assertNull(result.orchestration());
        assertEquals(0, provider.explanationCalls);
    }

    @Test
    void explanationWithInventedNumberIsRejected() {
        var provider = new FakeProvider(CompletableFuture.completedFuture(new ProviderResult<>(
                draft(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens()), "interpret-3", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The range is exactly 99 m.", "range"))))), "explain-3", "fake", null)));

        var result = workflow(provider).submit(new InterpretationRequest("Find range."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.EXPLANATION_REJECTED, result.status());
        assertTrue(result.explanationVerification().issues().contains("numeric-literal-not-bound-to-result:0:0"));
    }

    @Test
    void providerErrorsAreMappedToSafeWorkflowStatus() {
        var failed = new CompletableFuture<ProviderResult<InterpretationDraft>>();
        failed.completeExceptionally(new AIProviderException(AIProviderException.Kind.RATE_LIMITED,
                true, "safe message"));
        var provider = new FakeProvider(failed, CompletableFuture.completedFuture(null));

        var result = workflow(provider).submit(new InterpretationRequest("question"))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.PROVIDER_ERROR, result.status());
        assertEquals(AIProviderException.Kind.RATE_LIMITED, result.providerError());
        assertEquals("provider-request-failed", result.messageCode());
        assertNull(result.interpretation());
    }

    @Test
    void cancellationDuringInterpretationPropagatesToProviderCall() {
        var interpretation = new CompletableFuture<ProviderResult<InterpretationDraft>>();
        var provider = new FakeProvider(interpretation, CompletableFuture.completedFuture(null));
        var workflowResult = workflow(provider).submit(new InterpretationRequest("question")).toCompletableFuture();

        assertTrue(workflowResult.cancel(true));
        assertTrue(interpretation.isCancelled());
    }

    @Test
    void clarificationPromptMustNotFabricateAResult() {
        assertThrows(IllegalArgumentException.class, () -> new ExplanationRequest("question",
                new edu.physics.problem.ProjectileSequenceSolution(ProjectileSolution.Status.UNDERDETERMINED,
                        List.of(), 0, List.of())));
    }

    // --- Area 1: End-to-end canonical projectile problem ---

    @Test
    void endToEndCanonicalProjectileProblemSolvesAndExplainsVerifiedResults() {
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(
                        draft(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens()), "interpret-canon-1", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Principle", List.of(new ExplanationDraft.Claim(
                                "Standard two-dimensional projectile kinematics applies.", null))),
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The horizontal range is calculated analytically.", "range"))))), "explain-canon-1", "fake", null)));

        var result = workflow(provider).submit(new InterpretationRequest(
                "Find the horizontal range for a projectile launched at 20 m/s at 45 degrees with g = 9.81 m/s^2."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertNotNull(result.orchestration().canonicalSolution());
        assertEquals(CanonicalSolution.OverallStatus.SOLVED, result.orchestration().canonicalSolution().status());
        assertEquals(1, result.orchestration().canonicalSolution().partResults().size());
        assertEquals(CanonicalSolution.PartStatus.SOLVED, result.orchestration().canonicalSolution().partResults().get(0).status());
        assertTrue(result.explanationVerification().verified());
        assertEquals(1, provider.explanationCalls);
        assertEquals(40.7747, result.orchestration().canonicalSolution().partResults().get(0).candidates().get(0).get("range").valueInSi(), 1e-3);
    }

    // --- Area 2: Inclined landing-surface problem ---

    @Test
    void inclinedLandingSurfaceProblemSolvesAndExplainsCanonicalSurfaceTarget() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(30.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(60.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        var draft = new InterpretationDraft(
                "Find range along 30 degree incline",
                List.of(),
                List.of("point particle", "air resistance ignored"),
                List.of(),
                0.95,
                new InterpretationDraft.SurfaceSpec.Inclined(0.0, 0.0, 30.0),
                AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE)),
                List.of(),
                List.of(),
                sharedGivens
        );

        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-incline", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Principle", List.of(new ExplanationDraft.Claim(
                                "Projectile lands on an inclined surface.", null))),
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The range along the surface is determined from trajectory-surface intersection.", "rangeAlongSurface"))))),
                        "explain-incline", "fake", null))
        );

        var result = workflow(provider).submit(new InterpretationRequest(
                "A projectile is launched at 30 m/s at 60 degrees onto an inclined surface at 30 degrees. Find range along surface."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertNotNull(result.orchestration().canonicalSolution());
        assertEquals(CanonicalSolution.OverallStatus.SOLVED, result.orchestration().canonicalSolution().status());
        assertNull(result.orchestration().solution(), "Legacy solution must be null for surface problems to prevent semantic confusion");
        assertTrue(result.explanationVerification().verified());
        assertEquals(1, provider.explanationCalls);
    }

    // --- Area 3: Multipart canonical problem with ResultReference dependencies ---

    @Test
    void multipartCanonicalProblemWithResultReferenceDependenciesSolvesAndExplains() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        var part0 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FIND_INITIAL_SPEED,
                Map.of(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(10.0, PhysicalUnit.METRE),
                       ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(0.0, PhysicalUnit.METRE))
        );
        var part1 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE,
                Map.of(),
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new ResultReference(0, "initialSpeed"))
        );
        var draft = new InterpretationDraft(
                "Solve initial speed to hit (10, 0), then find horizontal range",
                List.of(part0, part1),
                List.of(),
                List.of(),
                0.95,
                null,
                AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of(),
                sharedGivens
        );

        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-multipart", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Resolution", List.of(
                                new ExplanationDraft.Claim("Initial speed solved first, then used to evaluate horizontal range.", "range"))))),
                        "explain-multipart", "fake", null))
        );

        var result = workflow(provider).submit(new InterpretationRequest(
                "Solve initial speed to hit dx=10 m, dy=0 m at 45 degrees, then find horizontal range."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        CanonicalSolution canonical = result.orchestration().canonicalSolution();
        assertNotNull(canonical);
        assertEquals(CanonicalSolution.OverallStatus.SOLVED, canonical.status());
        assertEquals(2, canonical.partResults().size());
        assertEquals(CanonicalSolution.PartStatus.SOLVED, canonical.partResults().get(0).status());
        assertEquals(CanonicalSolution.PartStatus.SOLVED, canonical.partResults().get(1).status());
        assertEquals(1, provider.explanationCalls);
    }

    // --- Area 4: Failed upstream dependency with independent sibling results ---

    @Test
    void failedUpstreamDependencyWithIndependentSiblingEvaluatesSiblingAndRejectsExplanation() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        // Part 0: Unreachable point (dx=10, dy=20) at 45 deg under gravity -> NO_SOLUTION
        var part0 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FIND_INITIAL_SPEED,
                Map.of(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(10.0, PhysicalUnit.METRE),
                       ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(20.0, PhysicalUnit.METRE))
        );
        // Part 1: Dependent on Part 0 -> DEPENDENCY_UNAVAILABLE
        var part1 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE,
                Map.of(),
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new ResultReference(0, "initialSpeed"))
        );
        // Part 2: Independent sibling with its own initial speed 20 m/s -> SOLVED (40.0 m)
        var part2 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE,
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND))
        );
        var draft = new InterpretationDraft(
                "Upstream fail with independent sibling",
                List.of(part0, part1, part2),
                List.of(),
                List.of(),
                0.95,
                null,
                AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of(),
                sharedGivens
        );

        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-sibling", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest(
                "Evaluate three parts where upstream fails but sibling has independent launch speed."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.SOLVER_REJECTED, result.status());
        assertEquals("solver-output-partially-solved", result.messageCode());
        assertEquals(0, provider.explanationCalls, "Explanation must be blocked when overall status is PARTIALLY_SOLVED");
        CanonicalSolution canonical = result.orchestration().canonicalSolution();
        assertNotNull(canonical);
        assertEquals(CanonicalSolution.OverallStatus.PARTIALLY_SOLVED, canonical.status());
        assertEquals(3, canonical.partResults().size());
        assertEquals(CanonicalSolution.PartStatus.NO_SOLUTION, canonical.partResults().get(0).status());
        assertEquals(CanonicalSolution.PartStatus.DEPENDENCY_UNAVAILABLE, canonical.partResults().get(1).status());
        assertEquals(CanonicalSolution.PartStatus.SOLVED, canonical.partResults().get(2).status());
        assertEquals(40.0, canonical.partResults().get(2).candidates().get(0).get("range").valueInSi(), 1e-9);
    }

    // --- Area 5: Incomplete problem blocked before solver execution ---

    @Test
    void incompleteProblemBlockedBeforeSolverExecution() {
        var draft = new InterpretationDraft(
                "Find maximum height with missing angle",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.MAXIMUM_HEIGHT,
                        Map.of(ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND)))),
                List.of(),
                List.of(),
                0.95
        );
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-incomplete", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest("A ball is launched at 20 m/s. Find maximum height."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.NEEDS_CLARIFICATION, result.status());
        assertNull(result.orchestration(), "Solver must never execute for incomplete problems");
        assertEquals(0, provider.explanationCalls);
    }

    // --- Area 6: Unsupported problem blocked before solver execution ---

    @Test
    void unsupportedProblemBlockedBeforeSolverExecution() {
        var draft = new InterpretationDraft(
                "Quadratic air drag",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens())),
                List.of(),
                List.of(),
                0.95,
                null,
                AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of("air-resistance-quadratic-drag"),
                Map.of()
        );
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-unsupported", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest("Find horizontal range accounting for air drag."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.UNSUPPORTED_PROBLEM, result.status());
        assertEquals("unsupported-physics-constraints", result.messageCode());
        assertNull(result.orchestration(), "Solver must never execute for unsupported physics problems");
        assertEquals(0, provider.explanationCalls);
    }

    // --- Area 7: Invalid problem blocked before solver execution ---

    @Test
    void invalidProblemBlockedBeforeSolverExecution() {
        var draft = new InterpretationDraft(
                "Invalid problem with out-of-range confidence",
                List.of(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens())),
                List.of(),
                List.of(),
                -0.5
        );
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-invalid", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest("Find range."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.INVALID_PROBLEM, result.status());
        assertEquals("interpretation-invalid", result.messageCode());
        assertNull(result.orchestration(), "Solver must never execute for invalid problem specifications");
        assertEquals(0, provider.explanationCalls);
    }

    // --- Area 8: Multiple-solution result blocked from ordinary explanation ---

    @Test
    void multipleSolutionResultBlockedFromOrdinaryExplanation() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        var draft = new InterpretationDraft(
                "Find launch angles to hit (10, 2)",
                List.of(new InterpretationDraft.ProblemPart(
                        ProjectileProblem.Target.FIND_LAUNCH_ANGLE,
                        Map.of(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(10.0, PhysicalUnit.METRE),
                               ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(2.0, PhysicalUnit.METRE)))),
                List.of(),
                List.of(),
                0.95,
                null,
                AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of(),
                sharedGivens
        );
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-multi-sol", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest(
                "Find launch angles to hit coordinates (10, 2) at 20 m/s with g = 9.8 m/s^2."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.SOLVER_REJECTED, result.status());
        assertEquals("solver-output-multiple-solutions", result.messageCode());
        assertNotNull(result.orchestration());
        CanonicalSolution canonical = result.orchestration().canonicalSolution();
        assertEquals(CanonicalSolution.OverallStatus.MULTIPLE_SOLUTIONS, canonical.status());
        assertEquals(2, canonical.partResults().get(0).candidates().size());
        assertEquals(0, provider.explanationCalls, "Problems with multiple mathematical roots must not enter ordinary AI explanation");
    }

    // --- Area 9: Partial solution blocked from ordinary explanation ---

    @Test
    void partialSolutionBlockedFromOrdinaryExplanation() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        // Part 0: Solvable
        var part0 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE,
                Map.of(ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20.0, PhysicalUnit.METRE_PER_SECOND))
        );
        // Part 1: Impossible upward target under gravity -> NO_SOLUTION
        var part1 = new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FIND_INITIAL_SPEED,
                Map.of(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, new PhysicalQuantity(10.0, PhysicalUnit.METRE),
                       ProjectileProblem.Input.VERTICAL_DISPLACEMENT, new PhysicalQuantity(20.0, PhysicalUnit.METRE))
        );
        var draft = new InterpretationDraft(
                "Mixed solvable and unsolvable parts",
                List.of(part0, part1),
                List.of(),
                List.of(),
                0.95,
                null,
                AngleReference.HORIZONTAL,
                List.of(),
                List.of(),
                List.of(),
                sharedGivens
        );
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-partial", "fake", null)),
                CompletableFuture.completedFuture(null)
        );

        var result = workflow(provider).submit(new InterpretationRequest("Solve two parts where one part fails."))
                .toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.SOLVER_REJECTED, result.status());
        assertEquals("solver-output-partially-solved", result.messageCode());
        assertNotNull(result.orchestration());
        assertEquals(CanonicalSolution.OverallStatus.PARTIALLY_SOLVED, result.orchestration().canonicalSolution().status());
        assertEquals(0, provider.explanationCalls, "Partially solved problems must not be sent to AI explanation");
    }

    // --- Area 10: Canonical target identity preserved in explanation requests ---

    @Test
    void canonicalTargetIdentityPreservedInExplanationRequest() {
        Map<ProjectileProblem.Input, PhysicalQuantity> sharedGivens = Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(30.0, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(60.0, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)
        );
        var draft = new InterpretationDraft(
                "Find range along 30 degree incline",
                List.of(),
                List.of(),
                List.of(),
                0.95,
                new InterpretationDraft.SurfaceSpec.Inclined(0.0, 0.0, 30.0),
                AngleReference.HORIZONTAL,
                List.of(CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE)),
                List.of(),
                List.of(),
                sharedGivens
        );

        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(draft, "interpret-preserve-target", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Derivation", List.of(
                                new ExplanationDraft.Claim("Intersection yields the surface range.", "rangeAlongSurface"))))),
                        "explain-preserve-target", "fake", null))
        );

        var result = workflow(provider).submit(new InterpretationRequest("Find range along surface.")).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertEquals(1, provider.explanationCalls);
        assertNotNull(provider.lastExplanationRequest);
        assertNotNull(provider.lastExplanationRequest.canonicalSolution());
        assertEquals(CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE),
                provider.lastExplanationRequest.canonicalSolution().partResults().get(0).target());
        assertNull(provider.lastExplanationRequest.verifiedSolution(), "Legacy solution must remain null for surface problems");
    }

    // --- Area 11: Explanation verification against canonical result keys and values ---

    @Test
    void explanationVerificationValidatesAgainstCanonicalResultKeysAndValues() {
        // Test that an explanation citing an ungrounded fabricated number for a canonical target is rejected
        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(
                        draft(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens()), "interpret-bound", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The range is 12345.67 m.", "range"))))), "explain-fabricated", "fake", null)));

        var result = workflow(provider).submit(new InterpretationRequest("Find horizontal range.")).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.EXPLANATION_REJECTED, result.status());
        assertTrue(result.explanationVerification().issues().contains("numeric-literal-not-bound-to-result:0:0"));
    }

    // --- Area 12: Full clarification history preserved throughout validation and solving ---

    @Test
    void clarificationHistoryPreservedThroughoutValidationAndSolving() {
        var exchanges = List.of(
                new InterpretationRequest.ClarificationTurn("What distance is requested?", "Horizontal ground range"),
                new InterpretationRequest.ClarificationTurn("What is the launch height?", "Ground level (y=0)")
        );
        var request = new InterpretationRequest("Find distance.", exchanges);

        var provider = new FakeProvider(
                CompletableFuture.completedFuture(new ProviderResult<>(
                        draft(ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens()), "interpret-history", "fake", null)),
                CompletableFuture.completedFuture(new ProviderResult<>(new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Result", List.of(new ExplanationDraft.Claim(
                                "The verified horizontal range is presented.", "range"))))), "explain-history", "fake", null)));

        var result = workflow(provider).submit(request).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertEquals("Find distance.", result.originalQuestion());
        assertNotNull(result.validation().completenessReport());
        assertEquals(2, result.validation().completenessReport().originalRequest().clarifications().size());
        assertEquals("Horizontal ground range", result.validation().completenessReport().originalRequest().clarifications().get(0).answer());
        assertEquals("Ground level (y=0)", result.validation().completenessReport().originalRequest().clarifications().get(1).answer());
    }

    // --- Helpers ---

    private static AIProblemWorkflow workflow(AIProvider provider) {
        return new AIProblemWorkflow(provider, new ProblemValidator(),
                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()), new ExplanationVerifier());
    }

    private static InterpretationDraft draft(ProjectileProblem.Target target,
            Map<ProjectileProblem.Input, PhysicalQuantity> givens) {
        return new InterpretationDraft("normalized", List.of(new InterpretationDraft.ProblemPart(target, givens)),
                List.of("point particle", "air resistance ignored"), List.of(), 0.95);
    }

    private static Map<ProjectileProblem.Input, PhysicalQuantity> fullGivens() {
        return Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
    }

    private static final class FakeProvider implements AIProvider {
        private final CompletableFuture<ProviderResult<InterpretationDraft>> interpretation;
        private final CompletableFuture<ProviderResult<ExplanationDraft>> explanation;
        private int explanationCalls;
        private ExplanationRequest lastExplanationRequest;

        private FakeProvider(CompletableFuture<ProviderResult<InterpretationDraft>> interpretation,
                             CompletableFuture<ProviderResult<ExplanationDraft>> explanation) {
            this.interpretation = interpretation;
            this.explanation = explanation;
        }

        @Override
        public CompletableFuture<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
            return interpretation;
        }

        @Override
        public CompletableFuture<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
            explanationCalls++;
            lastExplanationRequest = request;
            return explanation;
        }
    }
}
