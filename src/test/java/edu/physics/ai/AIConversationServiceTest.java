package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class AIConversationServiceTest {
    @Test
    void clarificationTurnUsesTheOriginalQuestionAndStoresClarificationHistory() {
        var clarification = new InterpretationDraft.Clarification("What is gravity?", "Required for flight time.", List.of());
        var provider = new QueueProvider();
        provider.interpretations.add(completed(draft(List.of(), List.of(clarification))));
        provider.interpretations.add(completed(draft(List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.FLIGHT_TIME, Map.of(
                ProjectileProblem.Input.INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)))), List.of())));
        provider.explanations.add(completed(explanation("flightTime")));
        var service = service(provider);
        var initial = service.submit("Find the flight time for a 20 m/s launch at 45 degrees.").completion().join();

        var solved = service.answerClarifications(initial.id(), List.of("9.81 m/s^2")).join();

        assertEquals(AIWorkflowResult.Status.NEEDS_CLARIFICATION, initial.latest().status());
        assertEquals(AIWorkflowResult.Status.COMPLETE, solved.latest().status());
        assertEquals(1, solved.clarificationHistory().size());
        assertEquals(initial.question(), solved.question());
    }

    @Test
    void revisedProblemClearsClarificationsAndRecalculates() {
        var provider = new QueueProvider();
        provider.interpretations.add(completed(draft(List.of(), List.of(
                new InterpretationDraft.Clarification("What does distance mean?", "Ambiguous wording", List.of("x", "path"))))));
        provider.interpretations.add(completed(draft(List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens())), List.of())));
        provider.explanations.add(completed(explanation("range")));
        var service = service(provider);
        var initial = service.submit("Find speed after distance 10 m").completion().join();

        var revised = service.revise(initial.id(), "Find range for 20 m/s at 45 degrees with g=9.81 m/s^2").join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, revised.latest().status());
        assertTrue(revised.clarificationHistory().isEmpty());
        assertTrue(revised.version() > initial.version());
        assertEquals(1, provider.explanationRequests.size());
    }

    @Test
    void overlappingAttemptsCannotReplaceARevisedSessionResultWithStaleData() {
        var provider = new QueueProvider();
        var oldInterpretation = new NonCancellingFuture<ProviderResult<InterpretationDraft>>();
        provider.interpretations.add(oldInterpretation);
        provider.interpretations.add(completed(draft(List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens())), List.of())));
        provider.explanations.add(completed(explanation("range")));
        var service = service(provider);
        var oldAttempt = service.submit("old question");
        UUID sessionId = oldAttempt.sessionId();

        var current = service.revise(sessionId, "new question").join();
        oldInterpretation.complete(providerResult(draft(List.of(), List.of())));
        var obsolete = oldAttempt.completion().join();

        assertTrue(current.applied());
        assertEquals("new question", service.snapshot(sessionId).orElseThrow().question());
        assertEquals(AIWorkflowResult.Status.COMPLETE, service.snapshot(sessionId).orElseThrow().latest().status());
        assertFalse(obsolete.applied());
    }

    @Test
    void sessionsAreIsolatedAndClosingCancelsActiveWork() {
        var provider = new QueueProvider();
        var pending = new CompletableFuture<ProviderResult<InterpretationDraft>>();
        provider.interpretations.add(completed(draft(List.of(new InterpretationDraft.ProblemPart(
                ProjectileProblem.Target.HORIZONTAL_RANGE, fullGivens())), List.of())));
        provider.interpretations.add(pending);
        provider.explanations.add(completed(explanation("range")));
        var service = service(provider);
        var one = service.submit("one");
        var two = service.submit("two");

        assertNotEquals(one.sessionId(), two.sessionId());
        var firstResult = one.completion().join();
        service.close();
        var cancelled = two.completion().join();
        assertNotNull(firstResult.latest());
        assertFalse(cancelled.applied());
        assertNull(cancelled.latest());
        assertTrue(service.snapshot(one.sessionId()).isEmpty());
    }

    private static AIConversationService service(QueueProvider provider) {
        return new AIConversationService(new AIProblemWorkflow(provider, new ProblemValidator(),
                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()), new ExplanationVerifier()));
    }

    private static CompletableFuture<ProviderResult<InterpretationDraft>> completed(InterpretationDraft draft) {
        return CompletableFuture.completedFuture(providerResult(draft));
    }
    private static ProviderResult<InterpretationDraft> providerResult(InterpretationDraft draft) {
        return new ProviderResult<>(draft, "req", "test", null);
    }
    private static CompletableFuture<ProviderResult<ExplanationDraft>> completed(ExplanationDraft draft) {
        return CompletableFuture.completedFuture(new ProviderResult<>(draft, "exp", "test", null));
    }
    private static ExplanationDraft explanation(String resultKey) {
        return new ExplanationDraft(List.of(new ExplanationDraft.Section("Result", List.of(
                new ExplanationDraft.Claim("The answer is available in the verified result.", resultKey)))));
    }
    private static InterpretationDraft draft(List<InterpretationDraft.ProblemPart> parts,
                                              List<InterpretationDraft.Clarification> clarifications) {
        return new InterpretationDraft("normalized", parts, List.of("air resistance ignored"), clarifications, 0.9);
    }
    private static Map<ProjectileProblem.Input, PhysicalQuantity> fullGivens() {
        return Map.of(ProjectileProblem.Input.INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                ProjectileProblem.Input.LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                ProjectileProblem.Input.GRAVITY, q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED));
    }
    private static PhysicalQuantity q(double value, PhysicalUnit unit) { return new PhysicalQuantity(value, unit); }

    private static final class QueueProvider implements AIProvider {
        private final Queue<CompletableFuture<ProviderResult<InterpretationDraft>>> interpretations = new ConcurrentLinkedQueue<>();
        private final Queue<CompletableFuture<ProviderResult<ExplanationDraft>>> explanations = new ConcurrentLinkedQueue<>();
        private final List<ExplanationRequest> explanationRequests = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public CompletableFuture<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
            return interpretations.remove();
        }
        @Override public CompletableFuture<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
            explanationRequests.add(request);
            return explanations.remove();
        }
    }

    private static final class NonCancellingFuture<T> extends CompletableFuture<T> {
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
    }
}
