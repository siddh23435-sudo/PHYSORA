package edu.physics.ai;

import java.util.concurrent.CompletionStage;

/** Vendor-neutral boundary for model interpretation and explanation requests. */
public interface AIProvider extends AutoCloseable {
    CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request);

    CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request);

    @Override
    default void close() {}
}
