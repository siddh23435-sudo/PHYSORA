package edu.physics.ai;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Non-blocking orchestration pipeline; provider interpretation never calculates a physics answer. */
public final class AIProblemWorkflow {
    private final AIProvider provider;
    private final ProblemValidator validator;
    private final SolverOrchestrator solver;
    private final ExplanationVerifier explanationVerifier;

    public AIProblemWorkflow(AIProvider provider, ProblemValidator validator,
                             SolverOrchestrator solver, ExplanationVerifier explanationVerifier) {
        if (provider == null || validator == null || solver == null || explanationVerifier == null) {
            throw new IllegalArgumentException("Workflow dependencies are required");
        }
        this.provider = provider;
        this.validator = validator;
        this.solver = solver;
        this.explanationVerifier = explanationVerifier;
    }

    public CompletionStage<AIWorkflowResult> submit(InterpretationRequest request) {
        if (request == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Request is required"));
        CompletableFuture<AIWorkflowResult> result = new CompletableFuture<>();
        CompletionStage<ProviderResult<InterpretationDraft>> interpretationStage;
        try {
            interpretationStage = provider.interpret(request);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(providerError(request.question(), null, ex));
        }
        CompletableFuture<?> activeCall = interpretationStage.toCompletableFuture();
        interpretationStage.whenComplete((interpretation, failure) -> {
            if (result.isCancelled()) {
                activeCall.cancel(true);
                return;
            }
            if (failure != null) {
                result.complete(providerError(request.question(), null, failure));
                return;
            }
            processInterpretation(request, interpretation, result);
        });
        result.whenComplete((ignored, failure) -> {
            if (result.isCancelled()) activeCall.cancel(true);
        });
        return result;
    }

    private void processInterpretation(InterpretationRequest request,
                                      ProviderResult<InterpretationDraft> interpretation,
                                      CompletableFuture<AIWorkflowResult> result) {
        ProblemValidationResult validation = validator.validate(request.question(), interpretation.content());
        if (validation.status() == ProblemValidationResult.Status.NEEDS_CLARIFICATION) {
            result.complete(new AIWorkflowResult(AIWorkflowResult.Status.NEEDS_CLARIFICATION, request.question(),
                    interpretation, validation, null, null, null, null, "clarification-required"));
            return;
        }
        if (validation.status() != ProblemValidationResult.Status.READY) {
            result.complete(new AIWorkflowResult(AIWorkflowResult.Status.INVALID_PROBLEM, request.question(),
                    interpretation, validation, null, null, null, null, "interpretation-invalid"));
            return;
        }
        SolverOrchestrator.OrchestrationResult orchestration = solver.solve(validation);
        if (orchestration.status() != SolverOrchestrator.Status.VERIFIED) {
            result.complete(new AIWorkflowResult(AIWorkflowResult.Status.SOLVER_REJECTED, request.question(),
                    interpretation, validation, orchestration, null, null, null, "solver-output-rejected"));
            return;
        }
        CompletionStage<ProviderResult<ExplanationDraft>> explanationStage;
        try {
            explanationStage = provider.explain(new ExplanationRequest(request.question(), orchestration.solution()));
        } catch (RuntimeException ex) {
            result.complete(providerError(request.question(), interpretation, ex));
            return;
        }
        CompletableFuture<?> activeCall = explanationStage.toCompletableFuture();
        result.whenComplete((ignored, failure) -> {
            if (result.isCancelled()) activeCall.cancel(true);
        });
        explanationStage.whenComplete((explanation, failure) -> {
            if (result.isCancelled()) {
                activeCall.cancel(true);
                return;
            }
            if (failure != null) {
                result.complete(providerError(request.question(), interpretation, failure));
                return;
            }
            ExplanationVerifier.Verification verification = explanationVerifier.verify(
                    explanation.content(), orchestration.solution());
            AIWorkflowResult.Status status = verification.verified()
                    ? AIWorkflowResult.Status.COMPLETE : AIWorkflowResult.Status.EXPLANATION_REJECTED;
            result.complete(new AIWorkflowResult(status, request.question(), interpretation, validation,
                    orchestration, explanation, verification, null,
                    verification.verified() ? "complete" : "explanation-output-rejected"));
        });
    }

    private static AIWorkflowResult providerError(String question,
                                                  ProviderResult<InterpretationDraft> interpretation,
                                                  Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        AIProviderException.Kind kind = cause instanceof AIProviderException providerFailure
                ? providerFailure.kind() : AIProviderException.Kind.PROVIDER_FAILURE;
        String detail = cause.getMessage();
        System.err.println("[PHYSORA AI] Provider error: " + detail);
        return new AIWorkflowResult(AIWorkflowResult.Status.PROVIDER_ERROR, question, interpretation,
                null, null, null, null, kind, "provider-request-failed", detail);
    }
}
