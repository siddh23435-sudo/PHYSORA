package edu.physics.ai;

/** Safe provider failure categories; messages must not contain credentials or request secrets. */
public final class AIProviderException extends RuntimeException {
    public enum Kind { AUTHENTICATION, INVALID_REQUEST, RATE_LIMITED, TIMEOUT, NETWORK, REFUSAL, INVALID_RESPONSE, PROVIDER_FAILURE }
    public enum Reason { GENERAL, MODEL_UNAVAILABLE }

    private final Kind kind;
    private final boolean retryable;
    private final Reason reason;

    public AIProviderException(Kind kind, boolean retryable, String message) {
        this(kind, retryable, Reason.GENERAL, message);
    }

    public AIProviderException(Kind kind, boolean retryable, Reason reason, String message) {
        super(message);
        if (kind == null) throw new IllegalArgumentException("Failure kind is required");
        if (reason == null) throw new IllegalArgumentException("Failure reason is required");
        this.kind = kind;
        this.retryable = retryable;
        this.reason = reason;
    }

    public static AIProviderException modelUnavailable(String message) {
        return new AIProviderException(Kind.PROVIDER_FAILURE, false, Reason.MODEL_UNAVAILABLE, message);
    }

    public Kind kind() { return kind; }
    public boolean retryable() { return retryable; }
    public Reason reason() { return reason; }
}
