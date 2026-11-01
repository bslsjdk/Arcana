package dev.arcana.language;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CastingPreparationPipelineTest {
    private static ArcanaLanguage.LanguageDefinition language() {
        return new ArcanaLanguage.LanguageDefinition(
                "player:poetic", 1,
                Map.of("grim", "spell", "sigil", "invoke"),
                List.of(new ArcanaLanguage.PhraseRule(
                        "when the silver moon awakens", "spell")));
    }

    @Test
    void translatesParsesAndSemanticallyValidatesPlayerLanguage() {
        String source = """
                grim "spark" {
                    let power: Number = 2;
                    sigil "arcana:effect/light"(power = power);
                }
                """;

        var result = CastingPreparationPipeline.prepare(source, language());

        assertTrue(result.successful(), () -> result.diagnostics().toString());
        assertEquals(CastingPreparationPipeline.Status.PREPARED, result.status());
        assertTrue(result.canonicalSource().startsWith("spell"));
        assertEquals("spark", result.program().name());
        assertEquals(2, result.program().statements().size());
    }

    @Test
    void phraseTranslationFeedsTheSameCanonicalParser() {
        var result = CastingPreparationPipeline.prepare(
                "when the silver moon awakens \"spark\" {}", language());

        assertTrue(result.successful(), () -> result.diagnostics().toString());
        assertTrue(result.canonicalSource().startsWith("spell"));
    }

    @Test
    void parseFailureDoesNotProduceAnAst() {
        var result = CastingPreparationPipeline.prepare(
                "unknown \"spark\" {}", language());

        assertEquals(CastingPreparationPipeline.Status.PARSE_FAILED, result.status());
        assertNull(result.program());
        assertFalse(result.successful());
    }

    @Test
    void semanticFailureRetainsAstForDiagnosticsButIsNotPrepared() {
        var result = CastingPreparationPipeline.prepare("""
                grim "bad" {
                    let power: Number = "not a number";
                }
                """, language());

        assertEquals(CastingPreparationPipeline.Status.SEMANTIC_FAILED, result.status());
        assertNotNull(result.program());
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM_TYPE_MISMATCH")));
        assertFalse(result.successful());
    }

    @Test
    void invalidRequestsFailClosed() {
        assertEquals(CastingPreparationPipeline.Status.INVALID_REQUEST,
                CastingPreparationPipeline.prepare(null, language()).status());
        assertEquals(CastingPreparationPipeline.Status.INVALID_REQUEST,
                CastingPreparationPipeline.prepare("source", null).status());
    }

    @Test
    void translationFailureDoesNotFallThroughToParsing() {
        var tooLarge = "x".repeat(65_537);
        var result = CastingPreparationPipeline.prepare(tooLarge, language());

        assertEquals(CastingPreparationPipeline.Status.TRANSLATION_FAILED, result.status());
        assertNull(result.program());
    }
}
