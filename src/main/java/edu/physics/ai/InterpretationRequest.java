package edu.physics.ai;

import java.util.List;

/** Natural-language input and relevant clarification turns sent for interpretation. */
public record InterpretationRequest(String question, List<ClarificationTurn> clarifications) {
    public InterpretationRequest {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Question is required");
        clarifications = List.copyOf(clarifications);
    }

    public InterpretationRequest(String question) {
        this(question, List.of());
    }

    public record ClarificationTurn(String question, String answer) {
        public ClarificationTurn {
            if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
                throw new IllegalArgumentException("Clarification question and answer are required");
            }
        }
    }
}
