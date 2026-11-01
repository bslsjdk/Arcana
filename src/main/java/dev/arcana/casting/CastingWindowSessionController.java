package dev.arcana.casting;

import dev.arcana.language.ArcanaLanguage;
import dev.arcana.language.CastingPreparationPipeline;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns a dedicated casting-window draft and its prepared/runtime lifecycle.
 * No trigger profile or trigger recognizer is accepted by this controller.
 *
 * Closing or detaching the UI is deliberately not a cancellation operation:
 * the session may outlive the screen that created it.
 */
public final class CastingWindowSessionController {
    public static final int MAX_SOURCE_LENGTH = 65_536;

    public enum State {
        EDITING,
        PREPARED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record Snapshot(
            State state,
            String source,
            CastingPreparationPipeline.Result preparation,
            boolean cancellationRequested,
            String diagnostic) {
        public Snapshot {
            Objects.requireNonNull(state);
            source = Objects.requireNonNullElse(source, "");
        }

        public boolean mayStartCasting() {
            return state == State.PREPARED
                    && preparation != null && preparation.successful();
        }
    }

    private final StringBuilder source = new StringBuilder();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private State state = State.EDITING;
    private CastingPreparationPipeline.Result preparation;
    private String diagnostic;

    /** Adds keyboard text or speech-recognition output from inside the window. */
    public synchronized Snapshot appendInput(String input) {
        if (state != State.EDITING) return snapshot("Input is accepted only while editing.");
        if (input == null) return snapshot("Input cannot be null.");
        if ((long) source.length() + input.length() > MAX_SOURCE_LENGTH) {
            return snapshot("Casting-window source exceeds the 65536-character limit.");
        }
        source.append(input);
        diagnostic = null;
        return snapshot(null);
    }

    /** Replaces the current draft, independent of whether it came from typing or speech. */
    public synchronized Snapshot replaceInput(String input) {
        if (state != State.EDITING) return snapshot("Input is accepted only while editing.");
        if (input == null) return snapshot("Input cannot be null.");
        if (input.length() > MAX_SOURCE_LENGTH) {
            return snapshot("Casting-window source exceeds the 65536-character limit.");
        }
        source.setLength(0);
        source.append(input);
        diagnostic = null;
        return snapshot(null);
    }

    /** Submit is the only transition from editing into canonical preparation. */
    public synchronized Snapshot submit(ArcanaLanguage.LanguageDefinition language) {
        if (state != State.EDITING) return snapshot("Only an editing draft can be submitted.");
        if (language == null) {
            diagnostic = "A player language definition is required.";
            return snapshot(diagnostic);
        }
        if (source.isEmpty()) {
            diagnostic = "Casting-window source cannot be empty.";
            return snapshot(diagnostic);
        }

        preparation = CastingPreparationPipeline.prepare(source.toString(), language);
        if (!preparation.successful()) {
            state = State.FAILED;
            diagnostic = "Casting-window submission failed canonical preparation.";
            return snapshot(diagnostic);
        }

        state = State.PREPARED;
        diagnostic = null;
        return snapshot(null);
    }

    /** Called only when a runtime adapter actually accepts the prepared cast. */
    public synchronized Snapshot markRunning() {
        if (state != State.PREPARED || preparation == null || !preparation.successful()) {
            return snapshot("Only a successfully prepared cast can enter the runtime.");
        }
        state = State.RUNNING;
        diagnostic = null;
        return snapshot(null);
    }

    public synchronized Snapshot complete() {
        if (state != State.RUNNING) return snapshot("Only a running cast can complete.");
        state = State.COMPLETED;
        diagnostic = null;
        return snapshot(null);
    }

    public synchronized Snapshot fail(String reason) {
        if (state == State.RUNNING) cancellationRequested.set(true);
        state = State.FAILED;
        diagnostic = Objects.requireNonNullElse(reason, "Casting failed.");
        return snapshot(diagnostic);
    }

    /**
     * UI cancel discards an editing/prepared draft. For a running cast it
     * raises a cooperative runtime signal; it does not claim rollback.
     */
    public synchronized Snapshot cancel() {
        if (isTerminal(state)) return snapshot("Session is already terminal.");
        boolean wasRunning = state == State.RUNNING;
        if (wasRunning) cancellationRequested.set(true);
        source.setLength(0);
        preparation = null;
        state = State.CANCELLED;
        diagnostic = wasRunning
                ? "Runtime cancellation requested."
                : "Casting-window draft cancelled.";
        return snapshot(diagnostic);
    }

    /** Screen close/detach must not alter the cast session lifecycle. */
    public synchronized Snapshot detachView() {
        return snapshot(null);
    }

    public synchronized Snapshot reset() {
        if (!isTerminal(state)) return snapshot("Only a terminal session can be reset.");
        source.setLength(0);
        preparation = null;
        cancellationRequested.set(false);
        state = State.EDITING;
        diagnostic = null;
        return snapshot(null);
    }

    public boolean cancellationRequested() {
        return cancellationRequested.get();
    }

    public synchronized Snapshot snapshot() {
        return snapshot(null);
    }

    private Snapshot snapshot(String message) {
        return new Snapshot(state, source.toString(), preparation,
                cancellationRequested.get(), message == null ? diagnostic : message);
    }

    private static boolean isTerminal(State state) {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }
}
