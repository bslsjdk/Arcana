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


    public record WeaveParseResult(
            dev.arcana.weave.ArcaneWeave.CanonicalWeave weave,
            List<String> diagnostics) {
        public WeaveParseResult { diagnostics = List.copyOf(diagnostics); }
        public boolean successful() { return weave != null && diagnostics.isEmpty(); }
    }

    /**
     * Encodes a validated canonical weave as a compact direct-script document.
     *
     * Grammar (PUA glyphs are the actual characters):
     *   document := (glyph-declaration | connection-declaration)*
     *   glyph-declaration := GLYPH "#" id [ "{" assignments "}" ] ";"
     *   connection-declaration := "@" id "." port ">" id "." port ";"
     *   assignments := id "=" scalar ("," id "=" scalar)*
     *
     * Declarations are deliberately explicit: visual adjacency never connects ports.
     */
    public static String encodeWeave(
            dev.arcana.weave.ArcaneWeave.CanonicalWeave weave,
            dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry registry) {
        Objects.requireNonNull(weave, "weave");
        Objects.requireNonNull(registry, "registry");
        var validation = dev.arcana.weave.ArcaneWeave.validateCanonical(weave, registry);
        if (!validation.valid()) throw new IllegalArgumentException(
                "Cannot encode invalid canonical weave: " + validation.diagnostics());
        StringBuilder out = new StringBuilder();
        for (var glyph : weave.glyphs()) {
            out.append(encodeSymbol(glyph.symbolId())).append('#').append(glyph.instanceId());
            if (!glyph.parameters().isEmpty()) {
                out.append('{');
                boolean first = true;
                for (var entry : new java.util.TreeMap<>(glyph.parameters()).entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    requireScriptIdentifier(entry.getKey());
                    requireScriptScalar(entry.getValue());
                    out.append(entry.getKey()).append('=').append(entry.getValue());
                }
                out.append('}');
            }
            out.append(';');
        }
        for (var connection : weave.connections()) {
            out.append('@').append(connection.fromGlyph()).append('.').append(connection.fromPort())
                    .append('>').append(connection.toGlyph()).append('.').append(connection.toPort()).append(';');
        }
        for (var region : weave.regions()) {
            out.append('^').append(region.id()).append('(').append(region.ownerGlyph()).append('.')
                    .append(region.ownerPort()).append(")[")
                    .append(region.entryGlyph()).append('>').append(region.exitGlyph()).append('.').append(region.exitPort()).append("](")
                    .append(String.join(",", region.memberGlyphs())).append(")@")
                    .append(region.parentRegionId() == null ? "-" : region.parentRegionId()).append(';');
        }
        return out.toString();
    }

    /** Parses direct canonical glyph writing and applies the same registry validator as other input paths. */
    public static WeaveParseResult parseWeave(
            String source, String weaveId, int version,
            dev.arcana.weave.ArcaneWeave.StandardSymbolRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (source == null) return new WeaveParseResult(null, List.of("SCRIPT_NULL_SOURCE"));
        if (source.length() > 65_536) return new WeaveParseResult(null, List.of("SCRIPT_SOURCE_TOO_LARGE"));
        List<String> errors = new java.util.ArrayList<>();
        List<dev.arcana.weave.ArcaneWeave.CanonicalGlyph> glyphs = new java.util.ArrayList<>();
        List<dev.arcana.weave.ArcaneWeave.CanonicalConnection> connections = new java.util.ArrayList<>();
        List<dev.arcana.weave.ArcaneWeave.CanonicalRegion> regions = new java.util.ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        String[] declarations = source.split(";", -1);
        if (declarations.length > 2_561) return new WeaveParseResult(null, List.of("SCRIPT_DECLARATION_LIMIT"));
        int offset = 0;
        for (String raw : declarations) {
            String declaration = raw.trim();
            if (declaration.isEmpty()) { offset += raw.length() + 1; continue; }
            try {
                if (declaration.charAt(0) == '@') {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "@([a-zA-Z0-9][a-zA-Z0-9_.-]*)\\.([a-zA-Z][a-zA-Z0-9_]*)>([a-zA-Z0-9][a-zA-Z0-9_.-]*)\\.([a-zA-Z][a-zA-Z0-9_]*)")
                            .matcher(declaration);
                    if (!matcher.matches()) throw new IllegalArgumentException("SCRIPT_INVALID_CONNECTION");
                    connections.add(new dev.arcana.weave.ArcaneWeave.CanonicalConnection(
                            matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
                } else if (declaration.charAt(0) == '^') {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "\\^([a-zA-Z0-9][a-zA-Z0-9_.-]*)\\(([a-zA-Z0-9][a-zA-Z0-9_.-]*)\\.([a-zA-Z][a-zA-Z0-9_]*)\\)\\[([a-zA-Z0-9][a-zA-Z0-9_.-]*)>([a-zA-Z0-9][a-zA-Z0-9_.-]*)\\.([a-zA-Z][a-zA-Z0-9_]*)\\]\\(([a-zA-Z0-9_.,-]+)\\)@(-|[a-zA-Z0-9][a-zA-Z0-9_.-]*)")
                            .matcher(declaration);
                    if (!matcher.matches()) throw new IllegalArgumentException("SCRIPT_INVALID_REGION");
                    List<String> members = java.util.Arrays.stream(matcher.group(7).split(","))
                            .map(String::trim).toList();
                    regions.add(new dev.arcana.weave.ArcaneWeave.CanonicalRegion(
                            matcher.group(1), matcher.group(2), matcher.group(3),
                            matcher.group(4), matcher.group(5), matcher.group(6), members,
                            matcher.group(8).equals("-") ? null : matcher.group(8)));
                } else {
                    int codePoint = declaration.codePointAt(0);
                    int glyphWidth = Character.charCount(codePoint);
                    String symbolId = GLYPH_TO_SYMBOL.get(codePoint);
                    if (symbolId == null) throw new IllegalArgumentException("SCRIPT_UNKNOWN_GLYPH");
                    String tail = declaration.substring(glyphWidth).trim();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "#([a-zA-Z0-9][a-zA-Z0-9_.-]*)(?:\\s*\\{(.*)\\})?")
                            .matcher(tail);
                    if (!matcher.matches()) throw new IllegalArgumentException("SCRIPT_INVALID_GLYPH_DECLARATION");
                    String instanceId = matcher.group(1);
                    if (!ids.add(instanceId)) throw new IllegalArgumentException("SCRIPT_DUPLICATE_INSTANCE");
                    Map<String, String> parameters = new LinkedHashMap<>();
                    String assignments = matcher.group(2);
                    if (assignments != null && !assignments.isBlank()) {
                        for (String assignment : assignments.split(",", -1)) {
                            String[] pair = assignment.trim().split("=", 2);
                            if (pair.length != 2) throw new IllegalArgumentException("SCRIPT_INVALID_PARAMETER");
                            String key = pair[0].trim(), value = pair[1].trim();
                            requireScriptIdentifier(key);
                            requireScriptScalar(value);
                            if (parameters.putIfAbsent(key, value) != null)
                                throw new IllegalArgumentException("SCRIPT_DUPLICATE_PARAMETER");
                        }
                    }
                    glyphs.add(new dev.arcana.weave.ArcaneWeave.CanonicalGlyph(
                            instanceId, symbolId, parameters));
                }
            } catch (RuntimeException ex) {
                errors.add(ex.getMessage() == null ? "SCRIPT_INVALID_DECLARATION" : ex.getMessage()
                        + " at declaration offset " + offset);
            }
            offset += raw.length() + 1;
        }
        if (!errors.isEmpty()) return new WeaveParseResult(null, errors);
        try {
            var weave = new dev.arcana.weave.ArcaneWeave.CanonicalWeave(
                    weaveId, version, glyphs, connections, regions);
            var validation = dev.arcana.weave.ArcaneWeave.validateCanonical(weave, registry);
            if (!validation.valid()) {
                return new WeaveParseResult(null, validation.diagnostics().stream()
                        .map(d -> d.code() + ": " + d.message()).toList());
            }
            return new WeaveParseResult(weave, List.of());
        } catch (RuntimeException ex) {
            return new WeaveParseResult(null, List.of(
                    ex.getMessage() == null ? "SCRIPT_INVALID_WEAVE" : ex.getMessage()));
        }
    }

    private static void requireScriptIdentifier(String value) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9_]*"))
            throw new IllegalArgumentException("SCRIPT_INVALID_IDENTIFIER");
    }

    private static void requireScriptScalar(String value) {
        if (value == null || !value.matches("-?(?:[0-9]+(?:\\.[0-9]+)?|true|false)"))
            throw new IllegalArgumentException("SCRIPT_INVALID_SCALAR");
    }

    public static String encodeSymbols(List<String> symbolIds) {
        Objects.requireNonNull(symbolIds, "symbolIds");
        StringBuilder result = new StringBuilder();
        for (String symbolId : symbolIds) result.append(encodeSymbol(symbolId));
        return result.toString();
    }
}
