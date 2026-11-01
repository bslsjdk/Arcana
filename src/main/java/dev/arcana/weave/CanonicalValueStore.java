package dev.arcana.weave;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed, per-cast storage for values produced by canonical glyph outputs.
 *
 * Only registry-declared output ports can publish values. Inputs resolve
 * exclusively through explicit canonical connections; there is no implicit
 * name lookup, coercion, or fallback guessing.
 */
public final class CanonicalValueStore {
    private final ArcaneWeave.StandardSymbolRegistry registry;
    private final CanonicalExecutionBudget.Session budget;
    private final Map<PortAddress, RuntimeValue> values = new HashMap<>();

    public CanonicalValueStore(ArcaneWeave.StandardSymbolRegistry registry,
                               CanonicalExecutionBudget.Session budget) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public sealed interface RuntimeValue permits NumberValue, BooleanValue, TextValue, Vector3Value {
        ArcaneWeave.Essence essence();
    }

    public record NumberValue(BigDecimal value) implements RuntimeValue {
        public NumberValue {
            Objects.requireNonNull(value, "value");
            if (value.precision() > 128 || Math.abs(value.scale()) > 64)
                throw new IllegalArgumentException("Number exceeds runtime precision limits.");
        }
        public NumberValue(String value) { this(new BigDecimal(value)); }
        @Override public ArcaneWeave.Essence essence() { return ArcaneWeave.Essence.NUMBER; }
    }

    public record BooleanValue(boolean value) implements RuntimeValue {
        @Override public ArcaneWeave.Essence essence() { return ArcaneWeave.Essence.BOOLEAN; }
    }

    public record TextValue(String value) implements RuntimeValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
            if (value.length() > 4096) throw new IllegalArgumentException("Text value exceeds runtime limit.");
        }
        @Override public ArcaneWeave.Essence essence() { return ArcaneWeave.Essence.TEXT; }
    }

    public record Vector3Value(double x, double y, double z) implements RuntimeValue {
        public Vector3Value {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Vector components must be finite.");
        }
        @Override public ArcaneWeave.Essence essence() { return ArcaneWeave.Essence.VECTOR3; }
    }

    public record PortAddress(String glyphId, String portId) {
        public PortAddress {
            requireId(glyphId, "glyphId");
            requireId(portId, "portId");
        }
    }

    public enum Status {
        STORED,
        REPLACED,
        RESOLVED,
        UNKNOWN_GLYPH,
        UNKNOWN_SYMBOL,
        UNKNOWN_PORT,
        TYPE_MISMATCH,
        UNBOUND_INPUT,
        VALUE_NOT_AVAILABLE,
        BUDGET_EXHAUSTED,
        INVALID_REQUEST
    }

    public record Result(Status status, RuntimeValue value, String diagnostic) {
        public Result {
            Objects.requireNonNull(status, "status");
            if (status == Status.RESOLVED && value == null)
                throw new IllegalArgumentException("A resolved result must contain a value.");
            if (status != Status.RESOLVED && status != Status.STORED && status != Status.REPLACED
                    && value != null)
                throw new IllegalArgumentException("Failed results cannot expose a value.");
        }
        public boolean successful() {
            return status == Status.RESOLVED || status == Status.STORED || status == Status.REPLACED;
        }
    }

    public Result writeOutput(ArcaneWeave.CanonicalGlyph glyph, String outputPort,
                              RuntimeValue value) {
        if (glyph == null || outputPort == null || value == null)
            return result(Status.INVALID_REQUEST, "Glyph, output port, and value are required.");
        ArcaneWeave.StandardSymbolDefinition definition = registry.definition(glyph.symbolId());
        if (definition == null) return result(Status.UNKNOWN_SYMBOL, "Symbol is not registered.");
        if (!definition.instantiable()) return result(Status.UNKNOWN_SYMBOL, "Abstract categories cannot produce values.");
        ArcaneWeave.PortDefinition port = definition.outputs().get(outputPort);
        if (port == null) return result(Status.UNKNOWN_PORT, "Output port is not declared by the standard symbol.");
        if (port.essence() != value.essence())
            return result(Status.TYPE_MISMATCH, "Runtime value type does not match the declared output type.");

        PortAddress address = new PortAddress(glyph.instanceId(), outputPort);
        boolean replacing = values.containsKey(address);
        if (!replacing) {
            CanonicalExecutionBudget.Admission admission = budget.reserveRuntimeValues(1);
            if (admission != CanonicalExecutionBudget.Admission.ACCEPTED)
                return result(Status.BUDGET_EXHAUSTED, "Runtime value budget is exhausted.");
        }
        values.put(address, value);
        return new Result(replacing ? Status.REPLACED : Status.STORED, value, null);
    }

    public Result readInput(ArcaneWeave.CanonicalWeave weave, String targetGlyphId, String inputPort) {
        if (weave == null || targetGlyphId == null || inputPort == null)
            return result(Status.INVALID_REQUEST, "Weave, target glyph, and input port are required.");
        ArcaneWeave.CanonicalGlyph target = findGlyph(weave.glyphs(), targetGlyphId);
        if (target == null) return result(Status.UNKNOWN_GLYPH, "Target glyph does not exist.");
        ArcaneWeave.StandardSymbolDefinition targetDefinition = registry.definition(target.symbolId());
        if (targetDefinition == null || !targetDefinition.instantiable())
            return result(Status.UNKNOWN_SYMBOL, "Target symbol is not executable.");
        ArcaneWeave.PortDefinition input = targetDefinition.inputs().get(inputPort);
        if (input == null) return result(Status.UNKNOWN_PORT, "Input port is not declared by the standard symbol.");

        List<ArcaneWeave.CanonicalConnection> incoming = weave.connections().stream()
                .filter(edge -> edge.toGlyph().equals(targetGlyphId) && edge.toPort().equals(inputPort))
                .toList();
        if (incoming.isEmpty()) return result(Status.UNBOUND_INPUT, "No canonical connection supplies this input.");
        if (incoming.size() != 1) return result(Status.INVALID_REQUEST, "Input has multiple incoming connections.");

        ArcaneWeave.CanonicalConnection edge = incoming.getFirst();
        ArcaneWeave.CanonicalGlyph source = findGlyph(weave.glyphs(), edge.fromGlyph());
        if (source == null) return result(Status.UNKNOWN_GLYPH, "Source glyph does not exist.");
        ArcaneWeave.StandardSymbolDefinition sourceDefinition = registry.definition(source.symbolId());
        if (sourceDefinition == null || !sourceDefinition.instantiable())
            return result(Status.UNKNOWN_SYMBOL, "Source symbol is not executable.");
        ArcaneWeave.PortDefinition output = sourceDefinition.outputs().get(edge.fromPort());
        if (output == null) return result(Status.UNKNOWN_PORT, "Source output port is not declared.");
        if (output.essence() != input.essence())
            return result(Status.TYPE_MISMATCH, "Connected port declarations have incompatible types.");

        RuntimeValue value = values.get(new PortAddress(source.instanceId(), edge.fromPort()));
        if (value == null) return result(Status.VALUE_NOT_AVAILABLE, "Connected output has not produced a runtime value.");
        if (value.essence() != input.essence())
            return result(Status.TYPE_MISMATCH, "Stored runtime value does not match the target input type.");
        return new Result(Status.RESOLVED, value, null);
    }

    /** Clears all values emitted by one glyph and releases their live slots. */
    public Result clearGlyph(String glyphId) {
        if (glyphId == null) return result(Status.INVALID_REQUEST, "Glyph id is required.");
        int removed = 0;
        var iterator = values.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().glyphId().equals(glyphId)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed == 0) return new Result(Status.REPLACED, null, null);
        CanonicalExecutionBudget.Admission admission = budget.releaseRuntimeValues(removed);
        if (admission != CanonicalExecutionBudget.Admission.ACCEPTED)
            throw new IllegalStateException("Value store and execution budget accounting diverged.");
        return new Result(Status.REPLACED, null, null);
    }

    public Map<PortAddress, RuntimeValue> snapshot() { return Map.copyOf(values); }

    private static ArcaneWeave.CanonicalGlyph findGlyph(
            List<ArcaneWeave.CanonicalGlyph> glyphs, String id) {
        return glyphs.stream().filter(glyph -> glyph.instanceId().equals(id)).findFirst().orElse(null);
    }

    private static Result result(Status status, String diagnostic) {
        return new Result(status, null, diagnostic);
    }

    private static void requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[A-Za-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid " + label + ".");
    }
}
