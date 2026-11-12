package edu.physics.ai.local;

import edu.physics.ai.ExplanationDraft;
import edu.physics.ai.ExplanationRequest;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProblemValidationResult;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalAIProviderTest {
    private final LocalAIProvider provider = new LocalAIProvider();

    @Test
    void interpretsFullQuestionWithGravity() {
        var request = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 degrees with g = 9.81 m/s^2. Find maximum height and range.");
        var result = provider.interpret(request).toCompletableFuture().join();

        InterpretationDraft draft = result.content();
        assertNotNull(draft);
        assertTrue(draft.clarifications().isEmpty(), "No clarification needed when g is specified");
        assertEquals(2, draft.parts().size());
        assertEquals(ProjectileProblem.Target.MAXIMUM_HEIGHT, draft.parts().get(0).target());
        assertEquals(ProjectileProblem.Target.HORIZONTAL_RANGE, draft.parts().get(1).target());

        var givens = draft.parts().get(0).givens();
        assertEquals(20.0, givens.get(ProjectileProblem.Input.INITIAL_SPEED).value());
        assertEquals(PhysicalUnit.METRE_PER_SECOND, givens.get(ProjectileProblem.Input.INITIAL_SPEED).unit());
        assertEquals(45.0, givens.get(ProjectileProblem.Input.LAUNCH_ANGLE).value());
        assertEquals(PhysicalUnit.DEGREE, givens.get(ProjectileProblem.Input.LAUNCH_ANGLE).unit());
        assertEquals(9.81, givens.get(ProjectileProblem.Input.GRAVITY).value());
    }

    @Test
    void requestsClarificationWhenGravityOmitted() {
        var request = new InterpretationRequest("A stone is thrown at 15 m/s at 30 degrees. Find range.");
        var result = provider.interpret(request).toCompletableFuture().join();

        InterpretationDraft draft = result.content();
        assertEquals(1, draft.clarifications().size());
        assertTrue(draft.clarifications().get(0).prompt().contains("gravitational acceleration"));
    }

    @Test
    void bindsGravityFromClarificationTurn() {
        var initial = new InterpretationRequest("A stone is thrown at 15 m/s at 30 degrees. Find range.");
        var turns = List.of(new InterpretationRequest.ClarificationTurn(
                "What value of gravitational acceleration (g) should be used?", "10"));
        var followup = new InterpretationRequest(initial.question(), turns);

        var result = provider.interpret(followup).toCompletableFuture().join();
        InterpretationDraft draft = result.content();
        assertTrue(draft.clarifications().isEmpty());
        assertEquals(10.0, draft.parts().get(0).givens().get(ProjectileProblem.Input.GRAVITY).value());
    }

    @Test
    void endToEndLocalSolvingAndExplanationPassesVerification() {
        var request = new InterpretationRequest(
                "A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.");
        var draft = provider.interpret(request).toCompletableFuture().join().content();

        ProblemValidator validator = new ProblemValidator();
        ProblemValidationResult validation = validator.validate(request.question(), draft);
        assertEquals(ProblemValidationResult.Status.READY, validation.status());

        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        var orchestration = orchestrator.solve(validation);
        assertEquals(SolverOrchestrator.Status.VERIFIED, orchestration.status());

        ExplanationDraft explanation = provider.explain(
                new ExplanationRequest(request.question(), orchestration.solution()))
                .toCompletableFuture().join().content();
        assertNotNull(explanation);

        ExplanationVerifier verifier = new ExplanationVerifier();
        ExplanationVerifier.Verification verification = verifier.verify(explanation, orchestration.solution());
        assertTrue(verification.verified(), "Verification issues: " + verification.issues());
        assertEquals(0, verification.issues().size());
    }
}

