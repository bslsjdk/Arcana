package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalWeaveSchedulerTest {
    private final ArcaneWeave.StandardSymbolRegistry registry =
            ArcaneWeave.StandardSymbolRegistry.builtins();

    private static CanonicalExecutionBudget.Session budget(long steps, int values) {
        return new CanonicalExecutionBudget.Session(
                new CanonicalExecutionBudget.Limits(steps, 8, 16, values));
    }

    @Test
    void schedulesDataDependenciesBeforeConsumersDeterministically() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:scheduler_add", 1, List.of(
                glyph("sum", "arcana:data/add", Map.of()),
                glyph("z", "arcana:reference/number", Map.of("value", "2")),
                glyph("a", "arcana:reference/number", Map.of("value", "3"))
        ), List.of(
                edge("a", "number", "sum", "left"),
                edge("z", "number", "sum", "right")
        ));
        var scheduler = scheduler(weave, budget(10, 4));

        var result = scheduler.execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, result.status());
        assertEquals(List.of("a", "z", "sum"), result.evaluationOrder());
        assertEquals(new CanonicalValueStore.NumberValue("5"),
                result.values().get(new CanonicalValueStore.PortAddress("sum", "result")));
    }

    @Test
    void refusesUnsupportedWorldSymbolsBeforeEvaluatingAnyNode() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:scheduler_world", 1, List.of(
                glyph("position", "arcana:reference/position",
                        Map.of("x", "0", "y", "64", "z", "0")),
                glyph("block", "arcana:sense/block", Map.of())
        ), List.of(edge("position", "position", "block", "position")));
        var scheduler = scheduler(weave, budget(10, 4));

        var result = scheduler.execute();

        assertEquals(CanonicalWeaveScheduler.Status.UNSUPPORTED_SYMBOL, result.status());
        assertTrue(result.evaluationOrder().isEmpty());
        assertTrue(result.values().isEmpty());
        assertEquals(0, result.budget().nodeSteps());
    }

    @Test
    void reportsBudgetExhaustionWithoutRetryingUnreadyNodes() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:scheduler_budget", 1, List.of(
                glyph("a", "arcana:reference/number", Map.of("value", "1")),
                glyph("b", "arcana:reference/number", Map.of("value", "2")),
                glyph("sum", "arcana:data/add", Map.of())
        ), List.of(
                edge("a", "number", "sum", "left"),
                edge("b", "number", "sum", "right")
        ));
        var scheduler = scheduler(weave, budget(1, 4));

        var result = scheduler.execute();

        assertEquals(CanonicalWeaveScheduler.Status.BUDGET_EXHAUSTED, result.status());
        assertEquals(1, result.evaluationOrder().size());
        assertEquals(1, result.budget().nodeSteps());
    }

    @Test
    void rejectsAPlanCompiledForAnotherWeave() {
        var first = new ArcaneWeave.CanonicalWeave("arcana:first", 1, List.of(
                glyph("a", "arcana:reference/number", Map.of("value", "1"))
        ));
        var second = new ArcaneWeave.CanonicalWeave("arcana:second", 1, List.of(
                glyph("b", "arcana:reference/number", Map.of("value", "2"))
        ));
        var plan = CanonicalControlFlowPlan.compile(first, registry).plan();

        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalWeaveScheduler(second, registry, plan, budget(4, 2)));
    }

    @Test
    void separateSchedulersNeverShareRuntimeValuesOrBudgetState() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:isolated_casts", 1, List.of(
                glyph("constant", "arcana:reference/number", Map.of("value", "7"))
        ));
        var first = scheduler(weave, budget(4, 2));
        var second = scheduler(weave, budget(4, 2));

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, first.execute().status());
        assertTrue(second.evaluator().valueStore().snapshot().isEmpty());
        assertEquals(0, second.evaluator().budgetSnapshot().nodeSteps());
        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, second.execute().status());
        assertEquals(1, first.evaluator().budgetSnapshot().nodeSteps());
        assertEquals(1, second.evaluator().budgetSnapshot().nodeSteps());
    }

    private CanonicalWeaveScheduler scheduler(
            ArcaneWeave.CanonicalWeave weave, CanonicalExecutionBudget.Session budget) {
        var compilation = CanonicalControlFlowPlan.compile(weave, registry);
        assertTrue(compilation.successful(), () -> compilation.diagnostics().toString());
        return new CanonicalWeaveScheduler(weave, registry, compilation.plan(), budget);
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
