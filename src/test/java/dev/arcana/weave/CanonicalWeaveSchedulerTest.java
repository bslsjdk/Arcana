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

    @Test
    void branchTrueExecutesOnlyTrueArmAndMergesWithoutWaitingForFalseArm() {
        var result = scheduler(branchWeave("arcana:branch_true", "1", "1"), budget(20, 8)).execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, result.status());
        assertTrue(result.evaluationOrder().contains("yes"));
        assertFalse(result.evaluationOrder().contains("no"));
        assertEquals(1, result.evaluationOrder().stream().filter("join"::equals).count());
        assertFalse(result.values().containsKey(new CanonicalValueStore.PortAddress("unused", "number")));
    }

    @Test
    void branchFalseExecutesOnlyFalseArmAndMergesWithoutWaitingForTrueArm() {
        var result = scheduler(branchWeave("arcana:branch_false", "1", "2"), budget(20, 8)).execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, result.status());
        assertFalse(result.evaluationOrder().contains("yes"));
        assertTrue(result.evaluationOrder().contains("no"));
        assertEquals(1, result.evaluationOrder().stream().filter("join"::equals).count());
    }

    @Test
    void repeatExecutesExactlyDeclaredIterationsThenContinues() {
        var weave = repeatWeave("arcana:repeat_three", 3);
        var result = scheduler(weave, budget(20, 4)).execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, result.status());
        assertEquals(3, result.evaluationOrder().stream().filter("body"::equals).count());
        assertEquals(1, result.evaluationOrder().stream().filter("after"::equals).count());
        assertEquals(0, result.budget().regionDepth());
        assertTrue(result.budget().loopIterations().isEmpty());
    }

    @Test
    void repeatOneExecutesBodyOnce() {
        var result = scheduler(repeatWeave("arcana:repeat_one", 1), budget(8, 2)).execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, result.status());
        assertEquals(1, result.evaluationOrder().stream().filter("body"::equals).count());
        assertTrue(result.evaluationOrder().contains("after"));
    }

    @Test
    void repeatBudgetFailureReleasesRegionAndLoopAccounting() {
        var result = scheduler(repeatWeave("arcana:repeat_budget", 3), budget(2, 2)).execute();

        assertEquals(CanonicalWeaveScheduler.Status.BUDGET_EXHAUSTED, result.status());
        assertEquals(0, result.budget().regionDepth());
        assertTrue(result.budget().loopIterations().isEmpty());
        assertFalse(result.evaluationOrder().contains("after"));
    }

    @Test
    void schedulerInstanceCannotExecuteTheSameCastTwice() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:one_shot", 1, List.of(
                glyph("value", "arcana:reference/number", Map.of("value", "4"))
        ));
        var scheduler = scheduler(weave, budget(4, 2));

        var first = scheduler.execute();
        var second = scheduler.execute();

        assertEquals(CanonicalWeaveScheduler.Status.COMPLETED, first.status());
        assertEquals(CanonicalWeaveScheduler.Status.FAILED, second.status());
        assertEquals(1, first.budget().nodeSteps());
        assertEquals(1, second.budget().nodeSteps());
        assertTrue(second.diagnostics().getFirst().contains("one-shot"));
    }

    @Test
    void cancellationIsReportedWithoutClaimingCompletion() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:cancelled", 1, List.of(
                glyph("value", "arcana:reference/number", Map.of("value", "9"))
        ));
        var result = scheduler(weave, budget(4, 2)).execute(() -> true);

        assertEquals(CanonicalWeaveScheduler.Status.CANCELLED, result.status());
        assertTrue(result.evaluationOrder().isEmpty());
        assertEquals(0, result.budget().nodeSteps());
    }

    private ArcaneWeave.CanonicalWeave branchWeave(String id, String left, String right) {
        return new ArcaneWeave.CanonicalWeave(id, 1, List.of(
                glyph("root", "arcana:control/entry", Map.of()),
                glyph("one", "arcana:reference/number", Map.of("value", left)),
                glyph("two", "arcana:reference/number", Map.of("value", right)),
                glyph("unused", "arcana:reference/number", Map.of("value", "999")),
                glyph("condition", "arcana:data/equal", Map.of()),
                glyph("branch", "arcana:control/branch", Map.of()),
                glyph("yes", "arcana:control/entry", Map.of()),
                glyph("no", "arcana:control/entry", Map.of()),
                glyph("join", "arcana:control/join", Map.of())
        ), List.of(
                edge("root", "flow", "branch", "flow"),
                edge("one", "number", "condition", "left"),
                edge("two", "number", "condition", "right"),
                edge("condition", "result", "branch", "condition"),
                edge("branch", "whenTrue", "yes", "flow"),
                edge("branch", "whenFalse", "no", "flow"),
                edge("yes", "flow", "join", "left"),
                edge("no", "flow", "join", "right")
        ), List.of(
                new ArcaneWeave.CanonicalRegion("trueArm", "branch", "whenTrue",
                        "yes", "yes", "flow", List.of("yes"), null),
                new ArcaneWeave.CanonicalRegion("falseArm", "branch", "whenFalse",
                        "no", "no", "flow", List.of("no"), null)
        ));
    }

    private ArcaneWeave.CanonicalWeave repeatWeave(String id, int count) {
        return new ArcaneWeave.CanonicalWeave(id, 1, List.of(
                glyph("root", "arcana:control/entry", Map.of()),
                glyph("repeat", "arcana:control/repeat", Map.of("maxIterations", Integer.toString(count))),
                glyph("body", "arcana:control/entry", Map.of()),
                glyph("after", "arcana:control/entry", Map.of())
        ), List.of(
                edge("root", "flow", "repeat", "flow"),
                edge("repeat", "body", "body", "flow"),
                edge("repeat", "completed", "after", "flow")
        ), List.of(
                new ArcaneWeave.CanonicalRegion("bodyRegion", "repeat", "body",
                        "body", "body", "flow", List.of("body"), null)
        ));
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
