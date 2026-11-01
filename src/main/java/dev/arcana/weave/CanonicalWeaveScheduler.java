package dev.arcana.weave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Deterministic first-stage scheduler for canonical pure-data weaves.
 *
 * It orders data evaluations from explicit typed connections. Control-flow
 * execution (branch/join/repeat), world access, mana, and capabilities are
 * deliberately rejected until their runtime semantics are implemented.
 */
public final class CanonicalWeaveScheduler {
    private static final Set<String> SUPPORTED = Set.of(
            "arcana:reference/number",
            "arcana:reference/position",
            "arcana:data/add",
            "arcana:data/equal",
            "arcana:data/less_than");

    private final ArcaneWeave.CanonicalWeave weave;
    private final ArcaneWeave.StandardSymbolRegistry registry;
    private final CanonicalControlFlowPlan.Plan plan;
    private final CanonicalNodeEvaluator evaluator;

    public CanonicalWeaveScheduler(
            ArcaneWeave.CanonicalWeave weave,
            ArcaneWeave.StandardSymbolRegistry registry,
            CanonicalControlFlowPlan.Plan plan,
            CanonicalExecutionBudget.Session budget) {
        this.weave = Objects.requireNonNull(weave, "weave");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.plan = Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(budget, "budget");
        if (!plan.weaveId().equals(weave.id()) || plan.version() != weave.version())
            throw new IllegalArgumentException("Control-flow plan does not belong to this weave.");
        CanonicalControlFlowPlan.Compilation compilation =
                CanonicalControlFlowPlan.compile(weave, registry);
        if (!compilation.successful())
            throw new IllegalArgumentException("Cannot schedule an invalid canonical weave.");
        if (!compilation.plan().equals(plan))
            throw new IllegalArgumentException("Control-flow plan does not match the canonical weave.");
        this.evaluator = new CanonicalNodeEvaluator(weave, registry, budget);
    }

    public enum Status {
        COMPLETED,
        FAILED,
        BUDGET_EXHAUSTED,
        UNSUPPORTED_SYMBOL,
        UNSUPPORTED_CONTROL_FLOW,
        CANCELLED
    }

    public record Result(Status status, List<String> evaluationOrder,
                         List<String> diagnostics,
                         Map<CanonicalValueStore.PortAddress, CanonicalValueStore.RuntimeValue> values,
                         CanonicalExecutionBudget.Snapshot budget) {
        public Result {
            Objects.requireNonNull(status, "status");
            evaluationOrder = List.copyOf(evaluationOrder);
            diagnostics = List.copyOf(diagnostics);
            values = Map.copyOf(values);
            Objects.requireNonNull(budget, "budget");
        }
        public boolean successful() { return status == Status.COMPLETED; }
    }

    /** Runs the complete pure-data dependency graph once, without retry loops. */
    public Result execute() {
        List<String> diagnostics = new ArrayList<>();
        List<String> order = new ArrayList<>();

        for (ArcaneWeave.CanonicalGlyph glyph : weave.glyphs()) {
            ArcaneWeave.StandardSymbolDefinition definition = registry.definition(glyph.symbolId());
            if (definition == null || !definition.instantiable()) {
                diagnostics.add("Unregistered or abstract symbol at glyph '" + glyph.instanceId() + "'.");
                return result(Status.UNSUPPORTED_SYMBOL, order, diagnostics);
            }
            if (definition.controlFlow() != ArcaneWeave.ControlFlow.ENTRY
                    && definition.controlFlow() != ArcaneWeave.ControlFlow.DATA) {
                diagnostics.add("Control-flow execution is not implemented for glyph '"
                        + glyph.instanceId() + "' (" + glyph.symbolId() + ").");
                return result(Status.UNSUPPORTED_CONTROL_FLOW, order, diagnostics);
            }
            if (definition.controlFlow() == ArcaneWeave.ControlFlow.DATA
                    && !SUPPORTED.contains(glyph.symbolId())) {
                diagnostics.add("No pure-data evaluator is registered for glyph '"
                        + glyph.instanceId() + "' (" + glyph.symbolId() + ").");
                return result(Status.UNSUPPORTED_SYMBOL, order, diagnostics);
            }
        }

        List<String> dataGlyphs = weave.glyphs().stream()
                .filter(glyph -> registry.definition(glyph.symbolId()).controlFlow()
                        == ArcaneWeave.ControlFlow.DATA)
                .map(ArcaneWeave.CanonicalGlyph::instanceId)
                .sorted()
                .toList();
        Set<String> dataSet = Set.copyOf(dataGlyphs);
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        dataGlyphs.forEach(id -> {
            indegree.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        });

        for (ArcaneWeave.CanonicalConnection edge : plan.connections()) {
            if (dataSet.contains(edge.fromGlyph()) && dataSet.contains(edge.toGlyph())) {
                outgoing.get(edge.fromGlyph()).add(edge.toGlyph());
                indegree.compute(edge.toGlyph(), (ignored, value) -> value + 1);
            }
        }
        outgoing.values().forEach(targets -> targets.sort(Comparator.naturalOrder()));

        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });

        while (!ready.isEmpty()) {
            String glyphId = ready.remove();
            CanonicalNodeEvaluator.Evaluation evaluation = evaluator.evaluate(glyphId);
            if (!evaluation.successful()) {
                diagnostics.add("Glyph '" + glyphId + "' failed: " + evaluation.status()
                        + (evaluation.diagnostic() == null ? "" : " (" + evaluation.diagnostic() + ")"));
                Status status = evaluation.status() == CanonicalNodeEvaluator.Status.BUDGET_EXHAUSTED
                        ? Status.BUDGET_EXHAUSTED
                        : evaluation.status() == CanonicalNodeEvaluator.Status.UNSUPPORTED_SYMBOL
                        ? Status.UNSUPPORTED_SYMBOL : Status.FAILED;
                return result(status, order, diagnostics);
            }
            order.add(glyphId);
            for (String target : outgoing.get(glyphId)) {
                int remaining = indegree.compute(target, (ignored, value) -> value - 1);
                if (remaining == 0) ready.add(target);
            }
        }

        if (order.size() != dataGlyphs.size()) {
            diagnostics.add("Data dependency graph could not be fully scheduled.");
            return result(Status.FAILED, order, diagnostics);
        }
        return result(Status.COMPLETED, order, diagnostics);
    }

    private Result result(Status status, List<String> order, List<String> diagnostics) {
        return new Result(status, order, diagnostics, evaluator.valueStore().snapshot(),
                evaluator.budgetSnapshot());
    }

    public CanonicalNodeEvaluator evaluator() { return evaluator; }
}
