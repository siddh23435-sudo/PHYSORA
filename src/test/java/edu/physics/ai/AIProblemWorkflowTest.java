package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class AIProblemWorkflowTest {
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
            return explanation;
        }
    }
}
