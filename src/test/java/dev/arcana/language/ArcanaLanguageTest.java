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
    void enforcesInputSizeLimit() {
        var lex = ArcanaLanguage.lex("x".repeat(65_537));
        assertTrue(lex.hasErrors());
        assertEquals("LEX_SOURCE_TOO_LARGE", lex.diagnostics().getFirst().code());
    }
}
