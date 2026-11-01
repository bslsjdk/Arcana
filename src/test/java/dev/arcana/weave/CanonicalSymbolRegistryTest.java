package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSymbolRegistryTest {
    @Test
    void acceptsConcreteArcanaStandardSymbols() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        var weave = new ArcaneWeave.CanonicalWeave("arcana:registered", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("end", "arcana:effect/light", Map.of())
        ));

        assertTrue(ArcaneWeave.validateCanonical(weave, registry).valid());
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
        var weave = new ArcaneWeave.CanonicalWeave("arcana:forged", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                        Map.of("inventedOperation", "anything", "maxIterations", "4"))
        ));

        var result = ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins());

        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_PARAMETER")));
    }

    @Test
    void repetitionParameterIsTypedAndBoundedByRegistry() {
        var valid = new ArcaneWeave.CanonicalWeave("arcana:bounded", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                        Map.of("maxIterations", "12"))
        ));
        var invalid = new ArcaneWeave.CanonicalWeave("arcana:unbounded", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                        Map.of("maxIterations", "1025"))
        ));
        var malformed = new ArcaneWeave.CanonicalWeave("arcana:malformed", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat",
                        Map.of("maxIterations", "many"))
        ));

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
        assertThrows(UnsupportedOperationException.class,
                () -> branch.inputs().clear());
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
