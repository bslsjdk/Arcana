package dev.arcana.language;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalScriptTest {
    @Test
    void everyConcreteBuiltinHasAStableDistinctGlyphIdentity() {
        var registry = dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry.builtins();
        var table = CanonicalScript.glyphTable();

        assertEquals(18, table.size());
        assertEquals(18, table.values().stream().distinct().count());
        assertTrue(table.values().stream().allMatch(registry::isInstantiable));
    }

    @Test
    void encodesAndDecodesCanonicalSymbolIdsWithoutChangingIdentity() {
        List<String> symbols = List.of(
                "arcana:control/entry",
                "arcana:reference/position",
                "arcana:effect/light"
        );

        var decoded = CanonicalScript.decode(CanonicalScript.encodeSymbols(symbols));

        assertTrue(decoded.successful());
        assertEquals(symbols, decoded.tokens().stream()
                .map(CanonicalScript.GlyphToken::symbolId).toList());
    }

    @Test
    void ignoresWhitespaceButRejectsUnknownCharacters() {
        String valid = CanonicalScript.encodeSymbol("arcana:control/entry");
        var decoded = CanonicalScript.decode(valid + " " + "A");

        assertFalse(decoded.successful());
        assertEquals(1, decoded.tokens().size());
        assertTrue(decoded.diagnostics().getFirst().startsWith("SCRIPT_UNKNOWN_GLYPH"));
    }

    @Test
    void rejectsUnknownSymbolIdsWhenEncoding() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalScript.encodeSymbol("player:invented_rune"));
    }

    @Test
    void structuredScriptRoundTripsAValidatedWeave() {
        var registry = dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry.builtins();
        var weave = new dev.arcana.weave.ArcaneWeave.CanonicalWeave(
                "arcana:test_light", 1,
                List.of(
                        new dev.arcana.weave.ArcaneWeave.CanonicalGlyph("entry", "arcana:control/entry", java.util.Map.of()),
                        new dev.arcana.weave.ArcaneWeave.CanonicalGlyph("position", "arcana:reference/position",
                                java.util.Map.of("x", "1", "y", "64", "z", "-2.5")),
                        new dev.arcana.weave.ArcaneWeave.CanonicalGlyph("level", "arcana:reference/number",
                                java.util.Map.of("value", "12")),
                        new dev.arcana.weave.ArcaneWeave.CanonicalGlyph("light", "arcana:effect/light", java.util.Map.of())
                ),
                List.of(
                        new dev.arcana.weave.ArcaneWeave.CanonicalConnection("entry", "flow", "light", "flow"),
                        new dev.arcana.weave.ArcaneWeave.CanonicalConnection("position", "position", "light", "position"),
                        new dev.arcana.weave.ArcaneWeave.CanonicalConnection("level", "number", "light", "level")
                )
        );

        String source = CanonicalScript.encodeWeave(weave, registry);
        var parsed = CanonicalScript.parseWeave(source, weave.id(), weave.version(), registry);

        assertTrue(parsed.successful(), () -> parsed.diagnostics().toString());
        assertEquals(weave, parsed.weave());
        assertEquals(source, CanonicalScript.encodeWeave(parsed.weave(), registry));
    }

    @Test
    void structuredScriptRejectsUnknownGlyphsAndInvalidConnections() {
        var registry = dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry.builtins();
        var unknown = CanonicalScript.parseWeave("A#entry;", "arcana:test", 1, registry);
        assertFalse(unknown.successful());
        assertTrue(unknown.diagnostics().stream().anyMatch(d -> d.startsWith("SCRIPT_UNKNOWN_GLYPH")));

        String entry = CanonicalScript.encodeSymbol("arcana:control/entry");
        String badConnection = entry + "#entry;@entry.missing>ghost.flow;";
        var invalid = CanonicalScript.parseWeave(badConnection, "arcana:test", 1, registry);
        assertFalse(invalid.successful());
    }

    @Test
    void structuredScriptRejectsDuplicateParametersAndUnsafeScalars() {
        var registry = dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry.builtins();
        String number = CanonicalScript.encodeSymbol("arcana:reference/number");
        var duplicate = CanonicalScript.parseWeave(number + "#n{value=1,value=2};", "arcana:test", 1, registry);
        assertFalse(duplicate.successful());
        assertTrue(duplicate.diagnostics().stream().anyMatch(d -> d.startsWith("SCRIPT_DUPLICATE_PARAMETER")));

        var unsafe = CanonicalScript.parseWeave(number + "#n{value=NaN};", "arcana:test", 1, registry);
        assertFalse(unsafe.successful());
    }

}
