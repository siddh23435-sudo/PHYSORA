package edu.physics.ai;

/** Complete structured result for one interpretation-solve-explanation attempt. */
public record AIWorkflowResult(Status status,
                               String originalQuestion,
                               ProviderResult<InterpretationDraft> interpretation,
                               ProblemValidationResult validation,
                               SolverOrchestrator.OrchestrationResult orchestration,
                               ProviderResult<ExplanationDraft> explanation,
                               ExplanationVerifier.Verification explanationVerification,
                               AIProviderException.Kind providerError,
                               String messageCode,
                               String detailMessage) {
    public enum Status { COMPLETE, NEEDS_CLARIFICATION, INVALID_PROBLEM, SOLVER_REJECTED, EXPLANATION_REJECTED, PROVIDER_ERROR }

    public AIWorkflowResult {
        if (status == null || originalQuestion == null || messageCode == null) {
            throw new IllegalArgumentException("Workflow status, question, and message code are required");
        }
    }

    public AIWorkflowResult(Status status,
                            String originalQuestion,
                            ProviderResult<InterpretationDraft> interpretation,
                            ProblemValidationResult validation,
                            SolverOrchestrator.OrchestrationResult orchestration,
                            ProviderResult<ExplanationDraft> explanation,
                            ExplanationVerifier.Verification explanationVerification,
                            AIProviderException.Kind providerError,
                            String messageCode) {
        this(status, originalQuestion, interpretation, validation, orchestration, explanation,
                explanationVerification, providerError, messageCode, null);
    }

    /** Independent provider attribution for the interpretation stage, or null if not executed. */
    public ProviderResult.ProviderAttribution interpretationAttribution() {
        return interpretation != null ? interpretation.attribution() : null;
    }

    /** Independent provider attribution for the explanation stage, or null if not executed. */
    public ProviderResult.ProviderAttribution explanationAttribution() {
        return explanation != null ? explanation.attribution() : null;
    }
}
