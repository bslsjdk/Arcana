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
                        Map.of("maxIterations", maxIterations)),
                new ArcaneWeave.CanonicalGlyph("bodyNode", "arcana:control/entry", Map.of())
        ), List.of(
                new ArcaneWeave.CanonicalConnection("start", "flow", "repeat", "flow"),
                new ArcaneWeave.CanonicalConnection("repeat", "body", "bodyNode", "flow")
        ), List.of(new ArcaneWeave.CanonicalRegion("body", "repeat", "body",
                        "bodyNode", "bodyNode", List.of("bodyNode"), null)));
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

    @Test
    void branchRequiresDistinctTrueAndFalseRegions() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        var branch = new ArcaneWeave.CanonicalWeave("arcana:branch_regions", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("start", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("left", "arcana:reference/number", Map.of("value", "1")),
                new ArcaneWeave.CanonicalGlyph("right", "arcana:reference/number", Map.of("value", "2")),
                new ArcaneWeave.CanonicalGlyph("condition", "arcana:data/equal", Map.of()),
                new ArcaneWeave.CanonicalGlyph("branch", "arcana:control/branch", Map.of()),
                new ArcaneWeave.CanonicalGlyph("trueStart", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("falseStart", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("join", "arcana:control/join", Map.of())
        ), List.of(
                new ArcaneWeave.CanonicalConnection("start", "flow", "branch", "flow"),
                new ArcaneWeave.CanonicalConnection("left", "number", "condition", "left"),
                new ArcaneWeave.CanonicalConnection("right", "number", "condition", "right"),
                new ArcaneWeave.CanonicalConnection("condition", "result", "branch", "condition"),
                new ArcaneWeave.CanonicalConnection("branch", "whenTrue", "trueStart", "flow"),
                new ArcaneWeave.CanonicalConnection("branch", "whenFalse", "falseStart", "flow"),
                new ArcaneWeave.CanonicalConnection("trueStart", "flow", "join", "left"),
                new ArcaneWeave.CanonicalConnection("falseStart", "flow", "join", "right")
        ), List.of(
                new ArcaneWeave.CanonicalRegion("trueArm", "branch", "whenTrue",
                        "trueStart", "trueStart", List.of("trueStart"), null),
                new ArcaneWeave.CanonicalRegion("falseArm", "branch", "whenFalse",
                        "falseStart", "falseStart", List.of("falseStart"), null)
        ));

        assertTrue(ArcaneWeave.validateCanonical(branch, registry).valid());
        var missingArm = new ArcaneWeave.CanonicalWeave(branch.id(), branch.version(),
                branch.glyphs(), branch.connections(), List.of(branch.regions().getFirst()));
        assertTrue(ArcaneWeave.validateCanonical(missingArm, registry).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_BRANCH_REGION_MISSING")));
    }

    @Test
    void nestedRegionsMustStayInsideTheirParentAndCannotOverlapAsSiblings() {
        var registry = ArcaneWeave.StandardSymbolRegistry.builtins();
        var invalid = new ArcaneWeave.CanonicalWeave("arcana:nested_regions", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("branch", "arcana:control/branch", Map.of()),
                new ArcaneWeave.CanonicalGlyph("repeat", "arcana:control/repeat", Map.of("maxIterations", "3")),
                new ArcaneWeave.CanonicalGlyph("inside", "arcana:control/entry", Map.of()),
                new ArcaneWeave.CanonicalGlyph("outside", "arcana:control/entry", Map.of())
        ), List.of(), List.of(
                new ArcaneWeave.CanonicalRegion("parent", "branch", "whenTrue",
                        "repeat", "repeat", List.of("repeat", "inside"), null),
                new ArcaneWeave.CanonicalRegion("otherArm", "branch", "whenFalse",
                        "outside", "outside", List.of("outside"), null),
                new ArcaneWeave.CanonicalRegion("nestedBody", "repeat", "body",
                        "inside", "inside", List.of("inside"), "parent")
        ));

        var result = ArcaneWeave.validateCanonical(invalid, registry);
        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNBOUND_REQUIRED_INPUT")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_REGION_OWNER_OUTSIDE_PARENT")) == false);
        assertTrue(result.diagnostics().stream()
                .noneMatch(issue -> issue.code().equals("CANONICAL_REGION_OVERLAP")));
    }


    @Test
    void unrelatedControlRegionsCannotShareGlyphInstances() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:sibling_overlap", 1, List.of(
                new ArcaneWeave.CanonicalGlyph("branch", "arcana:control/branch", Map.of()),
                new ArcaneWeave.CanonicalGlyph("shared", "arcana:control/entry", Map.of())
        ), List.of(), List.of(
                new ArcaneWeave.CanonicalRegion("trueArm", "branch", "whenTrue",
                        "shared", "shared", List.of("shared"), null),
                new ArcaneWeave.CanonicalRegion("falseArm", "branch", "whenFalse",
                        "shared", "shared", List.of("shared"), null)
        ));

        assertTrue(ArcaneWeave.validateCanonical(
                weave, ArcaneWeave.StandardSymbolRegistry.builtins()).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_REGION_OVERLAP")));
    }

}
