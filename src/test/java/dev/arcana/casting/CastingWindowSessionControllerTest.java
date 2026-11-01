package dev.arcana.casting;

import dev.arcana.language.ArcanaLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CastingWindowSessionControllerTest {
    private static ArcanaLanguage.LanguageDefinition language() {
        return new ArcanaLanguage.LanguageDefinition(
                "player:poetic", 1,
                Map.of("grim", "spell", "sigil", "invoke"),
                List.of());
    }

    private static String validSource() {
        return """
                grim "Astra Nora" {
                    let power: Number = 2;
                }
                """;
    }

    @Test
    void submitPreparesDirectlyWithoutActivationOrCancellationMarkers() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());

        var prepared = session.submit(language());

        assertEquals(CastingWindowSessionController.State.PREPARED, prepared.state());
        assertTrue(prepared.mayStartCasting());
        assertEquals("Astra Nora", prepared.preparation().program().name());
        assertFalse(prepared.cancellationRequested());
    }

    @Test
    void keyboardAndWindowSpeechTextUseTheSameAppendPath() {
        var session = new CastingWindowSessionController();
        session.appendInput("grim \"Astra ");
        session.appendInput("Nora\" { let power: Number = 2; }");

        var prepared = session.submit(language());

        assertTrue(prepared.mayStartCasting(), () -> String.valueOf(prepared.diagnostic()));
        assertEquals("Astra Nora", prepared.preparation().program().name());
    }

    @Test
    void cancelWhileEditingDiscardsDraftWithoutRuntimeSignal() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());

        var cancelled = session.cancel();

        assertEquals(CastingWindowSessionController.State.CANCELLED, cancelled.state());
        assertEquals("", cancelled.source());
        assertFalse(cancelled.cancellationRequested());
        assertFalse(session.cancellationRequested());
    }

    @Test
    void cancelWhilePreparedDiscardsPreparedDraftWithoutRuntimeSignal() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());
        assertEquals(CastingWindowSessionController.State.PREPARED,
                session.submit(language()).state());

        var cancelled = session.cancel();

        assertEquals(CastingWindowSessionController.State.CANCELLED, cancelled.state());
        assertNull(cancelled.preparation());
        assertFalse(cancelled.cancellationRequested());
    }

    @Test
    void cancelWhileRunningRaisesCooperativeRuntimeSignal() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());
        session.submit(language());
        session.markRunning();

        var cancelled = session.cancel();

        assertEquals(CastingWindowSessionController.State.CANCELLED, cancelled.state());
        assertTrue(cancelled.cancellationRequested());
        assertTrue(session.cancellationRequested());
        assertTrue(cancelled.diagnostic().contains("Runtime cancellation"));
    }

    @Test
    void detachingTheWindowDoesNotCancelOrModifyTheSession() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());
        session.submit(language());
        session.markRunning();

        var before = session.snapshot();
        var detached = session.detachView();

        assertEquals(before.state(), detached.state());
        assertEquals(before.source(), detached.source());
        assertEquals(before.preparation(), detached.preparation());
        assertFalse(detached.cancellationRequested());
    }

    @Test
    void failedPreparationCannotEnterRuntime() {
        var session = new CastingWindowSessionController();
        session.replaceInput("unknown \"bad\" {}");

        var failed = session.submit(language());

        assertEquals(CastingWindowSessionController.State.FAILED, failed.state());
        assertFalse(failed.mayStartCasting());
        assertEquals(CastingWindowSessionController.State.FAILED,
                session.markRunning().state());
    }

    @Test
    void emptyInputAndMissingLanguageDoNotPrepare() {
        var empty = new CastingWindowSessionController();
        assertEquals(CastingWindowSessionController.State.EDITING,
                empty.submit(language()).state());

        var missingLanguage = new CastingWindowSessionController();
        missingLanguage.replaceInput(validSource());
        assertEquals(CastingWindowSessionController.State.EDITING,
                missingLanguage.submit(null).state());
    }

    @Test
    void sourceLengthIsBounded() {
        var session = new CastingWindowSessionController();
        var result = session.replaceInput("x".repeat(
                CastingWindowSessionController.MAX_SOURCE_LENGTH + 1));

        assertEquals(CastingWindowSessionController.State.EDITING, result.state());
        assertEquals("", result.source());
        assertNotNull(result.diagnostic());
    }

    @Test
    void terminalSessionRequiresExplicitReset() {
        var session = new CastingWindowSessionController();
        session.replaceInput(validSource());
        session.submit(language());
        session.cancel();

        assertEquals(CastingWindowSessionController.State.EDITING, session.reset().state());
        assertEquals("", session.snapshot().source());
        assertFalse(session.cancellationRequested());
    }
}
