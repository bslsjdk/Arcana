package dev.arcana.casting;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the trigger-driven lifecycle for ordinary voice-command input only.
 * It must not be used as the controller for the dedicated casting window,
 * which has its own direct-submit lifecycle. It does not compile or execute a
 * weave; a future runtime adapter consumes the payload after activation.
 */
public final class CastingSessionController {
    private final CastingTriggerRecognizer.TriggerProfile triggerProfile;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final StringBuilder payload = new StringBuilder();
    private State state = State.IDLE;
    private String diagnostic;

    public CastingSessionController(CastingTriggerRecognizer.TriggerProfile triggerProfile) {
        this.triggerProfile = Objects.requireNonNull(triggerProfile, "triggerProfile");
    }

    public enum State {
        IDLE,
        PREPARED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record Transition(
            State state,
            String payload,
            boolean cancellationRequested,
            String diagnostic) {
        public Transition {
            Objects.requireNonNull(state);
            Objects.requireNonNull(payload);
        }
    }

    /**
     * Accepts one complete recognized input batch. Batches without a trigger
     * extend the pending payload, or the active incantation if already running.
     */
    public synchronized Transition accept(String inputBatch) {
        if (isTerminal(state)) {
            return snapshot("Session is terminal; reset it before accepting another incantation.");
        }

        CastingTriggerRecognizer.Result recognized =
                CastingTriggerRecognizer.recognize(inputBatch, triggerProfile);

        switch (recognized.status()) {
            case INVALID_INPUT, AMBIGUOUS -> {
                diagnostic = recognized.diagnostic();
                if (state == State.RUNNING) cancellationRequested.set(true);
                state = State.FAILED;
                payload.setLength(0);
                return snapshot(diagnostic);
            }
            case CANCELLED -> {
                boolean wasRunning = state == State.RUNNING
                        || recognized.cancelledAnActivatedCast();
                if (wasRunning) cancellationRequested.set(true);
                state = State.CANCELLED;
                diagnostic = recognized.diagnostic();
                payload.setLength(0);
                return snapshot(diagnostic);
            }
            case PREPARED -> {
                appendPayload(recognized.payload());
                if (state != State.RUNNING) state = State.PREPARED;
                diagnostic = null;
                return snapshot(null);
            }
            case ACTIVATED -> {
                appendPayload(recognized.payload());
                state = State.RUNNING;
                diagnostic = null;
                return snapshot(null);
            }
        }
        throw new IllegalStateException("Unhandled trigger-recognition status.");
    }

    /** Marks the active cast as completed after the runtime reports success. */
    public synchronized Transition complete() {
        if (state != State.RUNNING) {
            return snapshot("Only a running cast can complete.");
        }
        state = State.COMPLETED;
        diagnostic = null;
        return snapshot(null);
    }

    /** Marks the session failed; an active runtime must observe cancellation. */
    public synchronized Transition fail(String reason) {
        if (state == State.RUNNING) cancellationRequested.set(true);
        state = State.FAILED;
        diagnostic = Objects.requireNonNullElse(reason, "Casting failed.");
        payload.setLength(0);
        return snapshot(diagnostic);
    }

    /**
     * A terminal session can be reused only through an explicit reset, which
     * also clears its cooperative cancellation signal.
     */
    public synchronized Transition reset() {
        if (!isTerminal(state)) {
            return snapshot("Only a terminal session can be reset.");
        }
        payload.setLength(0);
        cancellationRequested.set(false);
        state = State.IDLE;
        diagnostic = null;
        return snapshot(null);
    }

    public boolean cancellationRequested() {
        return cancellationRequested.get();
    }

    public synchronized State state() {
        return state;
    }

    public synchronized String payload() {
        return payload.toString();
    }

    private void appendPayload(String addition) {
        if (addition == null || addition.isBlank()) return;
        if (payload.length() > 0 && !Character.isWhitespace(payload.charAt(payload.length() - 1))
                && !Character.isWhitespace(addition.charAt(0))) {
            payload.append(' ');
        }
        payload.append(addition);
    }

    private Transition snapshot(String message) {
        return new Transition(state, payload.toString(), cancellationRequested.get(),
                message == null ? diagnostic : message);
    }

    private static boolean isTerminal(State state) {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }
}
