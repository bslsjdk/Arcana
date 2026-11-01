package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalNodeEvaluatorTest {
    private final ArcaneWeave.StandardSymbolRegistry registry =
            ArcaneWeave.StandardSymbolRegistry.builtins();

    private static CanonicalExecutionBudget.Session budget(long steps, int values) {
        return new CanonicalExecutionBudget.Session(
                new CanonicalExecutionBudget.Limits(steps, 8, 16, values));
    }

    private ArcaneWeave.CanonicalWeave addWeave() {
        return new ArcaneWeave.CanonicalWeave("arcana:add_test", 1, List.of(
                glyph("a", "arcana:reference/number", Map.of("value", "12.5")),
                glyph("b", "arcana:reference/number", Map.of("value", "2.25")),
                glyph("sum", "arcana:data/add", Map.of())
        ), List.of(
                edge("a", "number", "sum", "left"),
                edge("b", "number", "sum", "right")
        ));
    }

    @Test
    void evaluatesNumberReferencesAndAdditionThroughExplicitConnections() {
        var evaluator = new CanonicalNodeEvaluator(addWeave(), registry, budget(10, 4));

        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, evaluator.evaluate("a").status());
        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, evaluator.evaluate("b").status());
        var result = evaluator.evaluate("sum");

        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, result.status());
        assertEquals("result", result.outputPort());
        assertEquals(new CanonicalValueStore.NumberValue("14.75"), result.output());
    }

    @Test
    void evaluatesNumericEqualityWithoutScaleSensitiveComparison() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:equal_test", 1, List.of(
                glyph("a", "arcana:reference/number", Map.of("value", "1.0")),
                glyph("b", "arcana:reference/number", Map.of("value", "1.00")),
                glyph("equal", "arcana:data/equal", Map.of())
        ), List.of(
                edge("a", "number", "equal", "left"),
                edge("b", "number", "equal", "right")
        ));
        var evaluator = new CanonicalNodeEvaluator(weave, registry, budget(10, 4));

        evaluator.evaluate("a");
        evaluator.evaluate("b");
        var result = evaluator.evaluate("equal");

        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, result.status());
        assertEquals(new CanonicalValueStore.BooleanValue(true), result.output());
    }

    @Test
    void evaluatesLessThanToFalseWhenLeftIsGreater() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:less_test", 1, List.of(
                glyph("a", "arcana:reference/number", Map.of("value", "9")),
                glyph("b", "arcana:reference/number", Map.of("value", "4")),
                glyph("less", "arcana:data/less_than", Map.of())
        ), List.of(
                edge("a", "number", "less", "left"),
                edge("b", "number", "less", "right")
        ));
        var evaluator = new CanonicalNodeEvaluator(weave, registry, budget(10, 4));

        evaluator.evaluate("a");
        evaluator.evaluate("b");
        var result = evaluator.evaluate("less");

        assertEquals(new CanonicalValueStore.BooleanValue(false), result.output());
    }

    @Test
    void evaluatesPositionReferenceAsFiniteVector() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:position_test", 1, List.of(
                glyph("origin", "arcana:reference/position",
                        Map.of("x", "1.5", "y", "-2", "z", "3"))
        ));
        var evaluator = new CanonicalNodeEvaluator(weave, registry, budget(2, 2));

        var result = evaluator.evaluate("origin");

        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, result.status());
        assertEquals(new CanonicalValueStore.Vector3Value(1.5, -2, 3), result.output());
    }

    @Test
    void refusesToEvaluateBeforeConnectedInputsHaveValues() {
        var evaluator = new CanonicalNodeEvaluator(addWeave(), registry, budget(10, 4));

        var result = evaluator.evaluate("sum");

        assertEquals(CanonicalNodeEvaluator.Status.INPUT_UNAVAILABLE, result.status());
        assertTrue(evaluator.valueStore().snapshot().isEmpty());
    }

    @Test
    void nodeStepBudgetIsConsumedAndStopsFurtherEvaluation() {
        var evaluator = new CanonicalNodeEvaluator(addWeave(), registry, budget(2, 4));

        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, evaluator.evaluate("a").status());
        assertEquals(CanonicalNodeEvaluator.Status.EVALUATED, evaluator.evaluate("b").status());
        assertEquals(CanonicalNodeEvaluator.Status.BUDGET_EXHAUSTED, evaluator.evaluate("sum").status());
        assertEquals(2, evaluator.valueStore().snapshot().size());
        assertEquals(2, budgetSnapshotSteps(evaluator));
    }

    @Test
    void unknownGlyphAndUnsupportedSymbolAreRejected() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:unsupported_test", 1, List.of(
                glyph("entity", "arcana:reference/entity", Map.of("selector", "nearest"))
        ));
        var evaluator = new CanonicalNodeEvaluator(weave, registry, budget(4, 2));

        assertEquals(CanonicalNodeEvaluator.Status.UNKNOWN_GLYPH, evaluator.evaluate("missing").status());
        assertEquals(CanonicalNodeEvaluator.Status.UNSUPPORTED_SYMBOL, evaluator.evaluate("entity").status());
    }

    @Test
    void invalidCanonicalWeaveCannotCreateEvaluator() {
        var invalid = new ArcaneWeave.CanonicalWeave("arcana:invalid_eval", 1, List.of(
                glyph("sum", "arcana:data/add", Map.of())
        ));
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalNodeEvaluator(invalid, registry, budget(4, 2)));
    }

    private static long budgetSnapshotSteps(CanonicalNodeEvaluator evaluator) {
        // The evaluator and its value store share this per-cast session.
        return evaluator.executionStepsForTesting();
    }

    private static ArcaneWeave.CanonicalGlyph glyph(
            String id, String symbol, Map<String, String> parameters) {
        return new ArcaneWeave.CanonicalGlyph(id, symbol, parameters);
    }

    private static ArcaneWeave.CanonicalConnection edge(
            String from, String fromPort, String to, String toPort) {
        return new ArcaneWeave.CanonicalConnection(from, fromPort, to, toPort);
    }
}
