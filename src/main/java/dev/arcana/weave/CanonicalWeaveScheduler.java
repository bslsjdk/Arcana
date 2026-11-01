package dev.arcana.weave;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Deterministic, bounded interpreter for canonical control flow and pure-data symbols. */
public final class CanonicalWeaveScheduler {
    private static final Set<String> SUPPORTED_DATA = Set.of(
            "arcana:reference/number", "arcana:reference/position",
            "arcana:data/add", "arcana:data/equal", "arcana:data/less_than");

    private final ArcaneWeave.CanonicalWeave weave;
    private final ArcaneWeave.StandardSymbolRegistry registry;
    private final CanonicalControlFlowPlan.Plan plan;
    private final CanonicalNodeEvaluator evaluator;
    private final CanonicalExecutionBudget.Session budget;
    private final Map<String, ArcaneWeave.CanonicalGlyph> glyphs = new HashMap<>();
    private final Map<String, CanonicalControlFlowPlan.RegionPlan> regions = new HashMap<>();
    private final Map<String, CanonicalControlFlowPlan.BranchPlan> branches = new HashMap<>();
    private final Map<String, CanonicalControlFlowPlan.RepeatPlan> repeats = new HashMap<>();

    public CanonicalWeaveScheduler(ArcaneWeave.CanonicalWeave weave,
            ArcaneWeave.StandardSymbolRegistry registry,
            CanonicalControlFlowPlan.Plan plan, CanonicalExecutionBudget.Session budget) {
        this.weave = Objects.requireNonNull(weave, "weave");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (!plan.weaveId().equals(weave.id()) || plan.version() != weave.version())
            throw new IllegalArgumentException("Control-flow plan does not belong to this weave.");
        var compilation = CanonicalControlFlowPlan.compile(weave, registry);
        if (!compilation.successful() || !compilation.plan().equals(plan))
            throw new IllegalArgumentException("Control-flow plan does not match a valid canonical weave.");
        weave.glyphs().forEach(g -> glyphs.put(g.instanceId(), g));
        plan.regions().forEach(r -> regions.put(r.id(), r));
        plan.branches().forEach(b -> branches.put(b.ownerGlyph(), b));
        plan.repeats().forEach(r -> repeats.put(r.ownerGlyph(), r));
        this.evaluator = new CanonicalNodeEvaluator(weave, registry, budget);
    }

    public enum Status { COMPLETED, FAILED, BUDGET_EXHAUSTED, UNSUPPORTED_SYMBOL,
        UNSUPPORTED_CONTROL_FLOW, CANCELLED }

    public record Result(Status status, List<String> evaluationOrder, List<String> diagnostics,
            Map<CanonicalValueStore.PortAddress, CanonicalValueStore.RuntimeValue> values,
            CanonicalExecutionBudget.Snapshot budget) {
        public Result {
            Objects.requireNonNull(status);
            evaluationOrder = List.copyOf(evaluationOrder);
            diagnostics = List.copyOf(diagnostics);
            values = Map.copyOf(values);
            Objects.requireNonNull(budget);
        }
        public boolean successful() { return status == Status.COMPLETED; }
    }

    public Result execute() { return execute(() -> false); }

    /** Cancellation is checked between interpreter steps; no partial result is reported as success. */
    public Result execute(BooleanSupplier cancellationRequested) {
        Runtime state = new Runtime(Objects.requireNonNull(cancellationRequested));
        Status status = preflight(state);
        if (status != null) return result(status, state);
        if (hasControlFlow() && plan.rootEntryGlyphs().isEmpty()) {
            state.diagnostics.add("Control-flow graph has no root entry.");
            return result(Status.FAILED, state);
        }
        if (!hasControlFlow()) {
            status = evaluateAllPureData(state);
            if (status != null) return result(status, state);
        }
        for (String root : plan.rootEntryGlyphs()) {
            if (state.cancelled()) return result(Status.CANCELLED, state);
            status = walkFlow(root, null, state);
            if (status != null) return result(status, state);
        }
        return result(Status.COMPLETED, state);
    }

    private Status preflight(Runtime state) {
        for (var glyph : weave.glyphs()) {
            if (state.cancelled()) return Status.CANCELLED;
            var definition = registry.definition(glyph.symbolId());
            if (definition == null || !definition.instantiable()) {
                state.diagnostics.add("Unknown or abstract symbol at '" + glyph.instanceId() + "'.");
                return Status.UNSUPPORTED_SYMBOL;
            }
            switch (definition.controlFlow()) {
                case ENTRY, BRANCH, JOIN, LOOP, DATA -> { }
                case LINEAR, TERMINAL -> {
                    state.diagnostics.add("Unsupported control operation at '" + glyph.instanceId()
                            + "' (" + glyph.symbolId() + ").");
                    return Status.UNSUPPORTED_CONTROL_FLOW;
                }
            }
            if (definition.controlFlow() == ArcaneWeave.ControlFlow.DATA
                    && !SUPPORTED_DATA.contains(glyph.symbolId())) {
                state.diagnostics.add("No pure-data evaluator is registered for '" + glyph.instanceId()
                        + "' (" + glyph.symbolId() + ").");
                return Status.UNSUPPORTED_SYMBOL;
            }
        }
        return null;
    }

    private Status evaluateAllPureData(Runtime state) {
        List<String> ids = weave.glyphs().stream()
                .filter(g -> registry.definition(g.symbolId()).controlFlow() == ArcaneWeave.ControlFlow.DATA)
                .map(ArcaneWeave.CanonicalGlyph::instanceId).sorted().toList();
        for (String id : ids) {
            Status status = evaluateDataDependency(id, state, new HashSet<>());
            if (status != null) return status;
        }
        return null;
    }

    /** Evaluate only the transitive inputs needed by a selected branch condition. */
    private Status evaluateDataDependency(String glyphId, Runtime state, Set<String> visiting) {
        if (state.evaluatedData.contains(glyphId)) return null;
        if (state.cancelled()) return Status.CANCELLED;
        ArcaneWeave.CanonicalGlyph glyph = glyphs.get(glyphId);
        if (glyph == null) {
            state.diagnostics.add("Data dependency references unknown glyph '" + glyphId + "'.");
            return Status.FAILED;
        }
        ArcaneWeave.StandardSymbolDefinition definition = registry.definition(glyph.symbolId());
        if (definition == null || definition.controlFlow() != ArcaneWeave.ControlFlow.DATA
                || !SUPPORTED_DATA.contains(glyph.symbolId())) {
            state.diagnostics.add("Branch data dependency '" + glyphId + "' is not a supported pure-data node.");
            return Status.UNSUPPORTED_SYMBOL;
        }
        if (!visiting.add(glyphId)) {
            state.diagnostics.add("Data dependency cycle detected at '" + glyphId + "'.");
            return Status.FAILED;
        }
        try {
            List<ArcaneWeave.PortDefinition> inputs = definition.inputs().values().stream()
                    .sorted(Comparator.comparing(ArcaneWeave.PortDefinition::id)).toList();
            for (ArcaneWeave.PortDefinition input : inputs) {
                List<ArcaneWeave.CanonicalConnection> incoming = plan.connections().stream()
                        .filter(edge -> edge.toGlyph().equals(glyphId) && edge.toPort().equals(input.id()))
                        .toList();
                if (incoming.isEmpty()) {
                    if (input.required()) {
                        state.diagnostics.add("Required data input '" + input.id()
                                + "' on glyph '" + glyphId + "' is unbound.");
                        return Status.FAILED;
                    }
                    continue;
                }
                if (incoming.size() != 1) {
                    state.diagnostics.add("Data input '" + input.id()
                            + "' on glyph '" + glyphId + "' has multiple sources.");
                    return Status.FAILED;
                }
                Status dependencyStatus = evaluateDataDependency(incoming.getFirst().fromGlyph(),
                        state, visiting);
                if (dependencyStatus != null) return dependencyStatus;
            }
            var evaluation = evaluator.evaluate(glyphId);
            if (!evaluation.successful()) {
                state.diagnostics.add("Glyph '" + glyphId + "' failed: " + evaluation.status()
                        + (evaluation.diagnostic() == null ? "" : " (" + evaluation.diagnostic() + ")"));
                return evaluation.status() == CanonicalNodeEvaluator.Status.BUDGET_EXHAUSTED
                        ? Status.BUDGET_EXHAUSTED
                        : evaluation.status() == CanonicalNodeEvaluator.Status.UNSUPPORTED_SYMBOL
                        ? Status.UNSUPPORTED_SYMBOL : Status.FAILED;
            }
            state.evaluatedData.add(glyphId);
            state.order.add(glyphId);
            return null;
        } finally {
            visiting.remove(glyphId);
        }
    }

    private Status walkFlow(String start, CanonicalControlFlowPlan.RegionPlan region, Runtime state) {
        String current = start;
        Set<String> path = new HashSet<>();
        while (current != null) {
            if (state.cancelled()) return Status.CANCELLED;
            if (region != null && !region.memberGlyphs().contains(current)) {
                state.diagnostics.add("Flow escaped region '" + region.id() + "' at '" + current + "'.");
                return Status.FAILED;
            }
            if (!path.add(current)) {
                state.diagnostics.add("Unexpected control-flow cycle at '" + current + "'.");
                return Status.FAILED;
            }
            ArcaneWeave.CanonicalGlyph glyph = glyphs.get(current);
            if (glyph == null) {
                state.diagnostics.add("Flow reached unknown glyph '" + current + "'.");
                return Status.FAILED;
            }
            if (region != null && current.equals(region.exitGlyph()))
                return consumeControlStep(current, state);

            if (branches.containsKey(current)) {
                String join = executeBranch(branches.get(current), state);
                if (state.failure != null) return state.failure;
                if (region != null && join.equals(region.exitGlyph())) return null;
                path.add(join);
                current = nextFlowTarget(join, "flow");
                continue;
            }
            if (repeats.containsKey(current)) {
                current = executeRepeat(repeats.get(current), state);
                if (state.failure != null) return state.failure;
                continue;
            }

            Status step = consumeControlStep(current, state);
            if (step != null) return step;
            if (glyph.symbolId().equals("arcana:control/entry")
                    || glyph.symbolId().equals("arcana:control/join")) {
                current = nextFlowTarget(current, "flow");
            } else {
                state.diagnostics.add("No interpreter behavior exists for '" + current + "'.");
                return Status.UNSUPPORTED_CONTROL_FLOW;
            }
        }
        if (region != null) {
            state.diagnostics.add("Region '" + region.id() + "' ended before its declared exit.");
            return Status.FAILED;
        }
        return null;
    }

    private String executeBranch(CanonicalControlFlowPlan.BranchPlan branch, Runtime state) {
        Status step = consumeControlStep(branch.ownerGlyph(), state);
        if (step != null) { state.failure = step; return null; }
        Status conditionStatus = evaluateDataDependency(branch.conditionSourceGlyph(), state, new HashSet<>());
        if (conditionStatus != null) { state.failure = conditionStatus; return null; }
        var condition = evaluator.valueStore().readInput(branch.ownerGlyph(), "condition");
        if (condition.status() != CanonicalValueStore.Status.RESOLVED
                || !(condition.value() instanceof CanonicalValueStore.BooleanValue bool)) {
            state.diagnostics.add("Branch '" + branch.ownerGlyph() + "' has no evaluated BOOLEAN condition.");
            state.failure = Status.FAILED;
            return null;
        }
        boolean takeTrue = bool.value();
        String regionId = takeTrue ? branch.trueRegionId() : branch.falseRegionId();
        String joinPort = takeTrue ? branch.trueJoinPort() : branch.falseJoinPort();
        var selected = regions.get(regionId);
        if (selected == null) {
            state.diagnostics.add("Branch references missing region '" + regionId + "'.");
            state.failure = Status.FAILED;
            return null;
        }
        Status regionStatus = executeRegion(selected, state);
        if (regionStatus != null) { state.failure = regionStatus; return null; }
        boolean connected = plan.connections().stream().anyMatch(edge ->
                edge.fromGlyph().equals(selected.exitGlyph()) && edge.fromPort().equals(selected.exitPort())
                && edge.toGlyph().equals(branch.joinGlyph()) && edge.toPort().equals(joinPort));
        if (!connected) {
            state.diagnostics.add("Selected branch arm is not connected to its declared join input.");
            state.failure = Status.FAILED;
            return null;
        }
        Status joinStatus = consumeControlStep(branch.joinGlyph(), state);
        if (joinStatus != null) { state.failure = joinStatus; return null; }
        return branch.joinGlyph();
    }

    private String executeRepeat(CanonicalControlFlowPlan.RepeatPlan repeat, Runtime state) {
        Status step = consumeControlStep(repeat.ownerGlyph(), state);
        if (step != null) { state.failure = step; return null; }
        var body = regions.get(repeat.bodyRegionId());
        if (body == null) {
            state.diagnostics.add("Repeat references missing body region '" + repeat.bodyRegionId() + "'.");
            state.failure = Status.FAILED;
            return null;
        }
        String activation = "activation" + (++state.activationSequence);
        Status failure = null;
        boolean activationStarted = false;
        try {
            for (int i = 0; i < repeat.maximumIterations(); i++) {
                if (state.cancelled()) { failure = Status.CANCELLED; break; }
                var admission = budget.beginLoopIteration(repeat.ownerGlyph(), activation,
                        repeat.maximumIterations());
                if (admission != CanonicalExecutionBudget.Admission.ACCEPTED) {
                    state.diagnostics.add("Repeat '" + repeat.ownerGlyph() + "' exceeded its iteration budget.");
                    failure = admission == CanonicalExecutionBudget.Admission.LIMIT_REACHED
                            ? Status.BUDGET_EXHAUSTED : Status.FAILED;
                    break;
                }
                activationStarted = true;
                Status bodyStatus = executeRegion(body, state);
                if (bodyStatus != null) { failure = bodyStatus; break; }
            }
        } finally {
            if (activationStarted) {
                var finished = budget.finishLoop(repeat.ownerGlyph(), activation);
                if (finished != CanonicalExecutionBudget.Admission.ACCEPTED) {
                    state.diagnostics.add("Repeat activation accounting could not be finalized.");
                    if (failure == null) failure = Status.FAILED;
                }
            }
        }
        if (failure != null) { state.failure = failure; return null; }
        return repeat.completionTargetGlyph();
    }

    private Status executeRegion(CanonicalControlFlowPlan.RegionPlan region, Runtime state) {
        if (budget.enterRegion() != CanonicalExecutionBudget.Admission.ACCEPTED) {
            state.diagnostics.add("Region-depth budget is exhausted at '" + region.id() + "'.");
            return Status.BUDGET_EXHAUSTED;
        }
        try {
            return walkFlow(region.entryGlyph(), region, state);
        } finally {
            if (budget.exitRegion() != CanonicalExecutionBudget.Admission.ACCEPTED)
                state.diagnostics.add("Region-depth accounting became inconsistent.");
        }
    }

    private Status consumeControlStep(String glyphId, Runtime state) {
        if (state.cancelled()) return Status.CANCELLED;
        if (budget.consumeNodeStep() != CanonicalExecutionBudget.Admission.ACCEPTED) {
            state.diagnostics.add("Node-step budget is exhausted at '" + glyphId + "'.");
            return Status.BUDGET_EXHAUSTED;
        }
        state.order.add(glyphId);
        return null;
    }

    private String nextFlowTarget(String glyphId, String outputPort) {
        return plan.connections().stream()
                .filter(edge -> edge.fromGlyph().equals(glyphId) && edge.fromPort().equals(outputPort)
                        && edge.toPort().equals("flow"))
                .map(ArcaneWeave.CanonicalConnection::toGlyph).findFirst().orElse(null);
    }

    private boolean hasControlFlow() {
        return weave.glyphs().stream().anyMatch(g -> {
            var flow = registry.definition(g.symbolId()).controlFlow();
            return flow == ArcaneWeave.ControlFlow.ENTRY || flow == ArcaneWeave.ControlFlow.BRANCH
                    || flow == ArcaneWeave.ControlFlow.JOIN || flow == ArcaneWeave.ControlFlow.LOOP;
        });
    }

    private Result result(Status status, Runtime state) {
        return new Result(status, state.order, state.diagnostics, evaluator.valueStore().snapshot(),
                evaluator.budgetSnapshot());
    }

    public CanonicalNodeEvaluator evaluator() { return evaluator; }

    private final class Runtime {
        private final BooleanSupplier cancellation;
        private final List<String> order = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();
        private final Set<String> evaluatedData = new HashSet<>();
        private int activationSequence;
        private Status failure;
        private Runtime(BooleanSupplier cancellation) { this.cancellation = cancellation; }
        private boolean cancelled() { return cancellation.getAsBoolean(); }
    }
}
