package dev.arcana.casting;

import dev.arcana.language.ArcanaLanguage;
import dev.arcana.language.CastingPreparationPipeline;

import java.util.Objects;

/**
 * Separates ordinary voice-command recognition from the explicit casting
 * window. The window never interprets player text as activation/cancellation
 * markers; its UI actions are the only controls in that mode.
 */
public final class CastingInputRouter {
    private CastingInputRouter() { }

    public enum Mode {
        VOICE_COMMAND,
        CASTING_WINDOW
    }

    public enum WindowAction {
        EDIT,
        SUBMIT,
        CANCEL
    }

    public enum WindowStatus {
        EDITING,
        PREPARED,
        CANCELLED,
        PREPARATION_FAILED,
        INVALID_REQUEST
    }

    public record WindowResult(
            WindowStatus status,
            String source,
            CastingPreparationPipeline.Result preparation,
            String diagnostic) {
        public WindowResult {
            Objects.requireNonNull(status);
            source = Objects.requireNonNullElse(source, "");
        }

        public boolean mayStartCasting() {
            return status == WindowStatus.PREPARED
                    && preparation != null && preparation.successful();
        }
    }

    /**
     * Ordinary voice commands use the player's trigger profile. The caller
     * supplies speech-recognition output as a complete input batch.
     */
    public static CastingSessionController.Transition acceptVoiceCommand(
            CastingSessionController session,
            String recognizedTranscript) {
        Objects.requireNonNull(session, "session");
        return session.accept(recognizedTranscript);
    }

    /**
     * Explicit casting-window actions. EDIT never prepares or executes;
     * SUBMIT sends the text directly to the language pipeline; CANCEL discards
     * the draft without scanning it for trigger phrases.
     */
    public static WindowResult handleCastingWindow(
            WindowAction action,
            String source,
            ArcanaLanguage.LanguageDefinition language) {
        Objects.requireNonNull(action, "action");

        return switch (action) {
            case EDIT -> new WindowResult(WindowStatus.EDITING,
                    Objects.requireNonNullElse(source, ""), null, null);
            case CANCEL -> new WindowResult(WindowStatus.CANCELLED,
                    "", null, "Casting-window draft cancelled by explicit UI action.");
            case SUBMIT -> prepareWindowSource(source, language);
        };
    }

    private static WindowResult prepareWindowSource(
            String source,
            ArcanaLanguage.LanguageDefinition language) {
        if (source == null || language == null) {
            return new WindowResult(WindowStatus.INVALID_REQUEST, "",
                    null, "Source and language definition are required.");
        }

        CastingPreparationPipeline.Result prepared =
                CastingPreparationPipeline.prepare(source, language);
        if (!prepared.successful()) {
            return new WindowResult(WindowStatus.PREPARATION_FAILED,
                    source, prepared, "Casting-window submission failed preparation.");
        }
        return new WindowResult(WindowStatus.PREPARED, source, prepared, null);
    }
}
