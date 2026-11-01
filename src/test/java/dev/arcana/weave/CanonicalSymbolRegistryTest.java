package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSymbolRegistryTest {
    private static ArcaneWeave.CanonicalWeave lightWeave(String id) {
        return new ArcaneWeave.CanonicalWeave(id, 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("position", "arcana:reference/position",
                        Map.of("x", "0", "y", "64", "z", "0")),
                new ArcaneWeave.CanonicalGlyph("level", "arcana:reference/number",
                        Map.of("value", "12")),
                new ArcaneWeave.CanonicalGlyph("light", "arcana:effect/light", Map.of())
        ), List.of(
                new ArcaneWeave.CanonicalConnection("start", "flow", "light", "flow"),
                new ArcaneWeave.CanonicalConnection("position", "position", "light", "position"),
                new ArcaneWeave.CanonicalConnection("level", "number", "light", "level")
        ));
    }

    private static ArcaneWeave.CanonicalWeave repeatWeave(String id, String maxIterations) {
        return new ArcaneWeave.CanonicalWeave(id, 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                        Map.of("maxIterations", maxIterations))
        ), List.of(new ArcaneWeave.CanonicalConnection("start", "flow", "repeat", "flow")));
    }

    @Test
    void acceptsConcreteArcanaStandardSymbolsAndTypedConnections() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        assertTrue(ArcaneWeave.validateCanonical(lightWeave("arcana:registered"), registry).valid());
        assertFalse(registry.contains("player:my_custom_magic"));
    }

    @Test
    void rejectsUnknownSymbolEvenWhenPlayerSuppliesItsIdentifier() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:unknown", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("custom", "player:erase_dimension", Map.of())
        ));

        var result = ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins());

        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_SYMBOL")));
    }

    @Test
    void broadSemanticHeadingsCannotBeUsedAsConcreteGlyphs() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:abstract", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("branch", "arcana:branch", Map.of())
        ));

        var result = ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins());

        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_ABSTRACT_SYMBOL")));
    }

    @Test
    void rejectsParametersThatAreNotPartOfTheStandardSymbolDefinition() {
        var weave = repeatWeave("arcana:forged", "4");
        var forged = new ArcaneWeave.CanonicalWeave(weave.id(), weave.version(),
                List.of(new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                        new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                                Map.of("inventedOperation", "anything", "maxIterations", "4"))),
                weave.connections());

        var result = ArcaneWeave.validateCanonical(
                forged, ArcaneWeave.StandardSymbolRegistry.builtins());

        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_PARAMETER")));
    }

    @Test
    void repetitionParameterIsTypedAndBoundedByRegistry() {
        var valid = repeatWeave("arcana:bounded", "12");
        var invalid = repeatWeave("arcana:unbounded", "1025");
        var malformed = repeatWeave("arcana:malformed", "many");

        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        assertTrue(ArcaneWeave.validateCanonical(valid, registry).valid());
        assertTrue(ArcaneWeave.validateCanonical(invalid, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_PARAMETER_RANGE")));
        assertTrue(ArcaneWeave.validateCanonical(malformed, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_PARAMETER_TYPE")));
    }

    @Test
    void rejectsMissingRequiredParameters() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:missing", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("position", "arcana:reference/position", Map.of("x", "1", "y", "2"))
        ));

        assertTrue(ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins()).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_MISSING_PARAMETER")));
    }

    @Test
    void registryOwnsTypedPortsAndCapabilityMapping() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        var branch = registry.definition("arcana:control/branch");
        var light = registry.definition("arcana:effect/light");

        assertNotNull(branch);
        assertEquals(ArcaneWeave.Essence.BOOLEAN, branch.inputs().get("condition").essence());
        assertEquals(ArcaneWeave.Essence.SIGNAL, branch.outputs().get("whenTrue").essence());
        assertEquals("arcana:world/set_light", light.capabilityId());
        assertThrows(UnsupportedOperationException.class, () -> branch.inputs().clear());
    }

    @Test
    void rejectsConnectionsThatForgeOrMismatchStandardPorts() {
        var mismatch = new ArcaneWeave.CanonicalWeave("arcana:mismatch", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("position", "arcana:reference/position",
                        Map.of("x", "0", "y", "0", "z", "0")),
                new ArcaneWeave.CanonicalGlyph("light", "arcana:effect/light", Map.of())
        ), List.of(new ArcaneWeave.CanonicalConnection(
                "position", "position", "light", "level")));

        var forged = new ArcaneWeave.CanonicalWeave("arcana:forged_port", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("light", "arcana:effect/light", Map.of())
        ), List.of(new ArcaneWeave.CanonicalConnection(
                "start", "playerMadePort", "light", "flow")));

        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        assertTrue(ArcaneWeave.validateCanonical(mismatch, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_PORT_TYPE_MISMATCH")));
        assertTrue(ArcaneWeave.validateCanonical(forged, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_OUTPUT_PORT")));
    }

    @Test
    void canonicalComplexityUsesRegistryWeightsNotSourceLength() {
        var weave = lightWeave("arcana:complexity");
        assertEquals(6, ArcaneWeave.canonicalComplexity(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins()));
    }

    @Test
    void rejectsDuplicateGlyphInstanceIds() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:duplicates", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("same", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("same", "arcana:effect/light", Map.of())
        ));

        assertTrue(ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins()).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_DUPLICATE_INSTANCE")));
    }
}
