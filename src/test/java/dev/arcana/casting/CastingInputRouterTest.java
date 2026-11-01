package dev.arcana.casting;

import dev.arcana.language.ArcanaLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CastingInputRouterTest {
    private static CastingTriggerRecognizer.TriggerProfile triggers() {
        return new CastingTriggerRecognizer.TriggerProfile(
                List.of("Astra"), List.of("Nora"));
    }

    private static ArcanaLanguage.LanguageDefinition language() {
        return new ArcanaLanguage.LanguageDefinition(
                "player:poetic", 1,
                Map.of("grim", "spell", "sigil", "invoke"),
                List.of());
    }

    @Test
    void voiceCommandModeUsesThePlayerTriggerProfile() {
        var session = new CastingSessionController(triggers());

        var result = CastingInputRouter.acceptVoiceCommand(
                session, "grim \"spark\" {} Astra");

        assertEquals(CastingSessionController.State.RUNNING, result.state());
        assertTrue(result.payload().startsWith("grim"));
    }

    @Test
    void castingWindowSubmissionDoesNotInterpretActivationOrCancellationWords() {
        String source = """
                grim "Astra Nora" {
                    let power: Number = 2;
                }
                """;

        var result = CastingInputRouter.handleCastingWindow(
                CastingInputRouter.WindowAction.SUBMIT, source, language());

        assertEquals(CastingInputRouter.WindowStatus.PREPARED, result.status());
        assertTrue(result.mayStartCasting());
        assertEquals("Astra Nora", result.preparation().program().name());
    }

    @Test
    void castingWindowEditDoesNotPrepareOrExecute() {
        var result = CastingInputRouter.handleCastingWindow(
                CastingInputRouter.WindowAction.EDIT, "unfinished draft", language());

        assertEquals(CastingInputRouter.WindowStatus.EDITING, result.status());
        assertEquals("unfinished draft", result.source());
        assertNull(result.preparation());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void explicitWindowCancelDiscardsDraftWithoutScanningItsText() {
        var result = CastingInputRouter.handleCastingWindow(
                CastingInputRouter.WindowAction.CANCEL,
                "grim \"Astra Nora\" { broken source",
                language());

        assertEquals(CastingInputRouter.WindowStatus.CANCELLED, result.status());
        assertEquals("", result.source());
        assertNull(result.preparation());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void windowSubmitFailsClosedWhenCanonicalPreparationFails() {
        var result = CastingInputRouter.handleCastingWindow(
                CastingInputRouter.WindowAction.SUBMIT,
                "unknown \"spark\" {}", language());

        assertEquals(CastingInputRouter.WindowStatus.PREPARATION_FAILED, result.status());
        assertNotNull(result.preparation());
        assertFalse(result.mayStartCasting());
    }

    @Test
    void windowSubmitRejectsMissingSourceOrLanguage() {
        assertEquals(CastingInputRouter.WindowStatus.INVALID_REQUEST,
                CastingInputRouter.handleCastingWindow(
                        CastingInputRouter.WindowAction.SUBMIT, null, language()).status());
        assertEquals(CastingInputRouter.WindowStatus.INVALID_REQUEST,
                CastingInputRouter.handleCastingWindow(
                        CastingInputRouter.WindowAction.SUBMIT, "source", null).status());
    }
}
