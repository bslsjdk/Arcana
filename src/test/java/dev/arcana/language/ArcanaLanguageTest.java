package dev.arcana.language;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArcanaLanguageTest {
    @Test
    void parsesCanonicalSpell() {
        var result = ArcanaLanguage.parse("""
                spell "spark" {
                    let power: Number = 2;
                    invoke "arcana:effect/ignite"(power = power, target = "self");
                }
                """);
        assertTrue(result.successful(), () -> result.diagnostics().toString());
        assertEquals("spark", result.program().name());
        assertEquals(2, result.program().statements().size());
        var declaration = (ArcanaLanguage.LetStatement) result.program().statements().getFirst();
        assertEquals(new BigDecimal("2"),
                ((ArcanaLanguage.LiteralExpression) declaration.initializer()).value());
    }

    @Test
    void twoPlayerVocabulariesTranslateToTheSameAst() {
        var languageA = new ArcanaLanguage.LanguageDefinition("player:a", 1,
                Map.of("grim", "spell", "sigil", "invoke"));
        var languageB = new ArcanaLanguage.LanguageDefinition("player:b", 1,
                Map.of("ritual", "spell", "weave", "invoke"));
        String sourceA = "grim \"spark\" { sigil \"arcana:effect/ignite\"(power = 2); }";
        String sourceB = "ritual \"spark\" { weave \"arcana:effect/ignite\"(power = 2); }";

        var translatedA = ArcanaLanguage.translate(sourceA, languageA);
        var translatedB = ArcanaLanguage.translate(sourceB, languageB);
        assertTrue(translatedA.successful());
        assertTrue(translatedB.successful());
        var parsedA = ArcanaLanguage.parse(translatedA.canonicalSource());
        var parsedB = ArcanaLanguage.parse(translatedB.canonicalSource());
        assertTrue(parsedA.successful(), () -> parsedA.diagnostics().toString());
        assertTrue(parsedB.successful(), () -> parsedB.diagnostics().toString());
        assertEquals(parsedA.program(), parsedB.program());
    }

    @Test
    void translatorDoesNotRewriteStringLiterals() {
        var language = new ArcanaLanguage.LanguageDefinition("player:test", 1, Map.of("grim", "spell"));
        var result = ArcanaLanguage.translate("grim \"grim\" {}", language);
        assertTrue(result.successful());
        assertEquals("spell \"grim\" {}", result.canonicalSource());
    }

    @Test
    void rejectsUnsafeKeywordMappingsAndDuplicateArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArcanaLanguage.LanguageDefinition("player:bad", 1, Map.of("boom", "java.lang.Runtime")));
        var parse = ArcanaLanguage.parse("""
                spell "bad" {
                    invoke "arcana:test"(power = 1, power = 2);
                }
                """);
        assertFalse(parse.successful());
        assertTrue(parse.diagnostics().stream().anyMatch(d -> d.code().equals("PARSE_DUPLICATE_ARGUMENT")));
    }

    @Test
    void mapsLongPhraseToOneCanonicalToken() {
        var language = new ArcanaLanguage.LanguageDefinition("player:poetic", 1, Map.of(),
                java.util.List.of(new ArcanaLanguage.PhraseRule(
                        "when the silver moon awakens", "spell")));
        var translated = ArcanaLanguage.translate(
                "when the silver moon awakens \"spark\" {}", language);

        assertTrue(translated.successful(), () -> translated.diagnostics().toString());
        assertEquals("spell \"spark\" {}", translated.canonicalSource());
    }

    @Test
    void mapsOneShortWordToAComplexCanonicalStructure() {
        String expansion = """
                spell "spark" {
                    let power: Number = 2;
                    invoke "arcana:effect/ignite"(power = power);
                }
                """;
        var language = new ArcanaLanguage.LanguageDefinition("player:compact", 1, Map.of(),
                java.util.List.of(new ArcanaLanguage.PhraseRule("x", expansion)));
        var translated = ArcanaLanguage.translate("x", language);

        assertTrue(translated.successful(), () -> translated.diagnostics().toString());
        var parsed = ArcanaLanguage.parse(translated.canonicalSource());
        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());
        assertEquals(2, parsed.program().statements().size());
    }

    @Test
    void rejectsConflictingDuplicatePhrasePatterns() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArcanaLanguage.LanguageDefinition("player:ambiguous", 1, Map.of(),
                        java.util.List.of(
                                new ArcanaLanguage.PhraseRule("moon rises", "spell"),
                                new ArcanaLanguage.PhraseRule("moon rises", "invoke"))));
    }

    @Test
    void prefersTheLongestMatchingPhraseRule() {
        var language = new ArcanaLanguage.LanguageDefinition("player:longest", 1, Map.of(),
                java.util.List.of(
                        new ArcanaLanguage.PhraseRule("silver moon", "let"),
                        new ArcanaLanguage.PhraseRule("silver moon rises", "invoke")));
        var translated = ArcanaLanguage.translate("silver moon rises", language);

        assertTrue(translated.successful());
        assertEquals("invoke", translated.canonicalSource());
    }

    @Test
    void rejectsPhraseRuleThatConflictsWithKeywordAlias() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArcanaLanguage.LanguageDefinition("player:collision", 1,
                        Map.of("spark", "spell"),
                        java.util.List.of(new ArcanaLanguage.PhraseRule("spark", "invoke"))));
    }

    @Test
    void rejectsExcessivePhraseExpansion() {
        var language = new ArcanaLanguage.LanguageDefinition("player:expansion", 1, Map.of(),
                java.util.List.of(new ArcanaLanguage.PhraseRule("x", "z".repeat(65_537))));
        var translated = ArcanaLanguage.translate("x", language);

        assertFalse(translated.successful());
        assertEquals("TRANS_OUTPUT_TOO_LARGE", translated.diagnostics().getFirst().code());
    }

    @Test
    void enforcesInputSizeLimit() {
        var lex = ArcanaLanguage.lex("x".repeat(65_537));
        assertTrue(lex.hasErrors());
        assertEquals("LEX_SOURCE_TOO_LARGE", lex.diagnostics().getFirst().code());
    }
}
