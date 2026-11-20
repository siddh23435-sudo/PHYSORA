package edu.physics.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

/** In-memory multi-turn sessions with cancellation and version checks to prevent stale updates. */
public final class AIConversationService implements AutoCloseable {
    private final AIProblemWorkflow workflow;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public AIConversationService(AIProblemWorkflow workflow) {
        if (workflow == null) throw new IllegalArgumentException("AI workflow is required");
        this.workflow = workflow;
    }

    public Submission submit(String question) {
        ensureOpen();
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Question is required");
        UUID id = UUID.randomUUID();
        Session session = new Session(id, question);
        sessions.put(id, session);
        return new Submission(id, execute(session, new InterpretationRequest(question), () -> {}));
    }

    public CompletableFuture<SessionSnapshot> answerClarifications(UUID sessionId, List<String> answers) {
        Session session = requireSession(sessionId);
        List<InterpretationDraft.Clarification> questions;
        synchronized (session) {
            if (session.latest != null && session.latest.status() == AIWorkflowResult.Status.NEEDS_CLARIFICATION
                    && session.latest.validation() != null) {
                session.pendingClarifications = List.copyOf(session.latest.validation().clarifications());
            }
            if (session.pendingClarifications == null || session.pendingClarifications.isEmpty()) {
                throw new IllegalStateException("The current session is not waiting for clarification");
            }
            questions = session.pendingClarifications;
        }
        if (answers == null || answers.size() != questions.size()) {
            throw new IllegalArgumentException("Provide exactly one answer for each clarification question");
        }
        List<InterpretationRequest.ClarificationTurn> turns = new ArrayList<>();
        synchronized (session) { turns.addAll(session.clarificationHistory); }
        for (int index = 0; index < questions.size(); index++) {
            turns.add(new InterpretationRequest.ClarificationTurn(questions.get(index).prompt(), answers.get(index)));
        }
        synchronized (session) { session.clarificationHistory.clear(); session.clarificationHistory.addAll(turns); }
        return execute(session, new InterpretationRequest(session.question, turns), () -> {});
    }

    /** A material change starts a fresh interpretation and invalidates prior solver results. */
    public CompletableFuture<SessionSnapshot> revise(UUID sessionId, String revisedQuestion) {
        Session session = requireSession(sessionId);
        if (revisedQuestion == null || revisedQuestion.isBlank()) throw new IllegalArgumentException("Revised question is required");
        return execute(session, new InterpretationRequest(revisedQuestion), () -> {
            session.question = revisedQuestion;
            session.clarificationHistory.clear();
            session.pendingClarifications = null;
        });
    }

    public Optional<SessionSnapshot> snapshot(UUID sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(snapshotOf(session, true));
    }

    public boolean cancel(UUID sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) return false;
        CompletableFuture<AIWorkflowResult> pending;
        synchronized (session) {
            session.version++;
            session.inProgress = false;
            pending = session.pending;
            session.pending = null;
        }
        if (pending != null) pending.cancel(true);
        return pending != null;
    }

    private CompletableFuture<SessionSnapshot> execute(Session session, InterpretationRequest request, Runnable prepare) {
        final long version;
        final CompletableFuture<AIWorkflowResult> previous;
        CompletableFuture<AIWorkflowResult> attempt = new CompletableFuture<>();
        synchronized (session) {
            prepare.run();
            version = ++session.version;
            previous = session.pending;
            session.pending = attempt;
            session.inProgress = true;
            session.latest = null;
        }
        if (previous != null) previous.cancel(true);

        java.util.concurrent.atomic.AtomicReference<CompletableFuture<AIWorkflowResult>> active =
                new java.util.concurrent.atomic.AtomicReference<>();
        attempt.whenComplete((ignored, failure) -> {
            if (attempt.isCancelled()) {
                CompletableFuture<AIWorkflowResult> current = active.get();
                if (current != null) current.cancel(true);
            }
        });
        try {
            CompletableFuture<AIWorkflowResult> current = workflow.submit(request).toCompletableFuture();
            active.set(current);
            if (attempt.isCancelled()) current.cancel(true);
            current.whenComplete((result, failure) -> {
                if (failure == null) attempt.complete(result);
                else attempt.completeExceptionally(failure);
            });
        } catch (RuntimeException ex) {
            attempt.completeExceptionally(ex);
        }
        CompletableFuture<SessionSnapshot> update = new CompletableFuture<>();
        attempt.whenComplete((result, failure) -> {
            boolean applied;
            synchronized (session) {
                applied = session.version == version;
                if (applied) {
                    session.pending = null;
                    session.inProgress = false;
                    session.latest = failure == null ? result : unexpectedFailure(session.question, failure);
                }
            }
            update.complete(snapshotOf(session, applied));
        });
        return update;
    }

    private static AIWorkflowResult unexpectedFailure(String question, Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        AIProviderException.Kind kind = cause instanceof AIProviderException providerFailure
                ? providerFailure.kind() : AIProviderException.Kind.PROVIDER_FAILURE;
        return new AIWorkflowResult(AIWorkflowResult.Status.PROVIDER_ERROR, question, null,
                null, null, null, null, kind, "provider-request-failed");
    }

    private static SessionSnapshot snapshotOf(Session session, boolean applied) {
        synchronized (session) {
            return new SessionSnapshot(session.id, session.question, session.version, session.latest,
                    List.copyOf(session.clarificationHistory), session.inProgress, applied);
        }
    }

    private Session requireSession(UUID id) {
        ensureOpen();
        Session session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("Conversation session was not found");
        return session;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Conversation service is closed");
    }

    @Override
    public void close() {
        closed = true;
        sessions.keySet().forEach(this::cancel);
        sessions.clear();
    }

    public record SessionSnapshot(UUID id, String question, long version, AIWorkflowResult latest,
                                  List<InterpretationRequest.ClarificationTurn> clarificationHistory,
                                  boolean inProgress, boolean applied) {
        public SessionSnapshot { clarificationHistory = List.copyOf(clarificationHistory); }
    }

    public record Submission(UUID sessionId, CompletableFuture<SessionSnapshot> completion) {
        public Submission {
            if (sessionId == null || completion == null) throw new IllegalArgumentException("Session id and completion are required");
        }
    }

    private static final class Session {
        private final UUID id;
        private String question;
        private long version;
        private AIWorkflowResult latest;
        private final List<InterpretationRequest.ClarificationTurn> clarificationHistory = new ArrayList<>();
        private List<InterpretationDraft.Clarification> pendingClarifications;
        private CompletableFuture<AIWorkflowResult> pending;
        private boolean inProgress;

        private Session(UUID id, String question) { this.id = id; this.question = question; }
    }
}
