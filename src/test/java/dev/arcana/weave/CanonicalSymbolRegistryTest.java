package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSymbolRegistryTest {
    @Test
    void acceptsOnlyArcanaRegisteredStandardSymbols() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        var weave = new ArcaneWeave.CanonicalWeave("arcana:registered", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:invocation", Map.of()),
                new ArcaneWeave.CanonicalGlyph("end", "arcana:manifestation", Map.of())
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
    void rejectsParametersThatAreNotPartOfTheStandardSymbolDefinition() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:forged", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("branch", "arcana:branch",
                        Map.of("inventedOperation", "anything"))
        ));

        var result = ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins());

        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_PARAMETER")));
    }

    @Test
    void repetitionHasARegistryDefinedBoundedParameter() {
        var valid = new ArcaneWeave.CanonicalWeave("arcana:bounded", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:repetition",
                        Map.of("maxIterations", "12"))
        ));
        var invalid = new ArcaneWeave.CanonicalWeave("arcana:unbounded", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:repetition", Map.of())
        ));

        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        assertTrue(ArcaneWeave.validateCanonical(valid, registry).valid());
        assertTrue(ArcaneWeave.validateCanonical(invalid, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_REPETITION_UNBOUNDED")));
    }

    @Test
    void rejectsDuplicateGlyphInstanceIds() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:duplicates", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("same", "arcana:invocation", Map.of()),
                new ArcaneWeave.CanonicalGlyph("same", "arcana:manifestation", Map.of())
        ));

        assertTrue(ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins()).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_DUPLICATE_INSTANCE")));
    }
}
