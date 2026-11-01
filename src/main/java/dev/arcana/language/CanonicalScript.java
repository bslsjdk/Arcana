package dev.arcana.language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned identity table for the built-in Arcana written glyphs.
 *
 * Private-use code points are transport/rendering slots only. Semantic identity
 * always resolves to the Arcana-owned standard symbol ID.
 */
public final class CanonicalScript {
    public static final int VERSION = 1;
    public static final int FIRST_PRIVATE_USE_CODE_POINT = 0xE000;

    private static final List<String> SYMBOLS = List.of(
            "arcana:control/entry",
            "arcana:control/branch",
            "arcana:control/join",
            "arcana:control/repeat",
            "arcana:data/add",
            "arcana:data/equal",
            "arcana:data/less_than",
            "arcana:sense/block",
            "arcana:sense/entity",
            "arcana:sense/light_level",
            "arcana:mana/absorb",
            "arcana:mana/store",
            "arcana:mana/release",
            "arcana:reference/position",
            "arcana:reference/number",
            "arcana:reference/entity",
            "arcana:constraint/ward",
            "arcana:effect/light"
    );

    private static final Map<Integer, String> GLYPH_TO_SYMBOL;
    private static final Map<String, Integer> SYMBOL_TO_GLYPH;

    static {
        Map<Integer, String> forward = new LinkedHashMap<>();
        Map<String, Integer> reverse = new LinkedHashMap<>();
        for (int i = 0; i < SYMBOLS.size(); i++) {
            int codePoint = FIRST_PRIVATE_USE_CODE_POINT + i;
            String symbol = SYMBOLS.get(i);
            forward.put(codePoint, symbol);
            reverse.put(symbol, codePoint);
        }
        GLYPH_TO_SYMBOL = Map.copyOf(forward);
        SYMBOL_TO_GLYPH = Map.copyOf(reverse);
    }

    private CanonicalScript() { }

    public record GlyphToken(String symbolId, int startCodePoint, int endCodePoint) {
        public GlyphToken {
            Objects.requireNonNull(symbolId);
            if (startCodePoint < 0 || endCodePoint <= startCodePoint)
                throw new IllegalArgumentException("Invalid glyph token span.");
        }
    }

    public record DecodeResult(List<GlyphToken> tokens, List<String> diagnostics) {
        public DecodeResult {
            tokens = List.copyOf(tokens);
            diagnostics = List.copyOf(diagnostics);
        }
        public boolean successful() { return diagnostics.isEmpty(); }
    }

    public static Map<Integer, String> glyphTable() { return GLYPH_TO_SYMBOL; }

    public static String encodeSymbol(String symbolId) {
        Integer codePoint = SYMBOL_TO_GLYPH.get(Objects.requireNonNull(symbolId));
        if (codePoint == null) throw new IllegalArgumentException("Unknown canonical script symbol: " + symbolId);
        return new String(Character.toChars(codePoint));
    }

    public static DecodeResult decode(String source) {
        Objects.requireNonNull(source, "source");
        List<GlyphToken> tokens = new java.util.ArrayList<>();
        List<String> diagnostics = new java.util.ArrayList<>();
        int codePointOffset = 0;
        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                offset += width;
                codePointOffset++;
                continue;
            }
            String symbolId = GLYPH_TO_SYMBOL.get(codePoint);
            if (symbolId == null) {
                diagnostics.add("SCRIPT_UNKNOWN_GLYPH at code-point offset " + codePointOffset);
            } else {
                tokens.add(new GlyphToken(symbolId, codePointOffset, codePointOffset + 1));
            }
            offset += width;
            codePointOffset++;
        }
        return new DecodeResult(tokens, diagnostics);
    }

    public static String encodeSymbols(List<String> symbolIds) {
        Objects.requireNonNull(symbolIds, "symbolIds");
        StringBuilder result = new StringBuilder();
        for (String symbolId : symbolIds) result.append(encodeSymbol(symbolId));
        return result.toString();
    }
}
