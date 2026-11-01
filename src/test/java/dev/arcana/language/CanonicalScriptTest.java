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
}
