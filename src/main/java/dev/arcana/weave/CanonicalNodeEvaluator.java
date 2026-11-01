package dev.arcana.weave;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Deterministic evaluator for the first pure-data canonical symbols.
 *
 * This evaluator deliberately has no Minecraft, mana, capability, or world
 * dependencies. Every evaluated node consumes one per-cast node-step budget.
 */
public final class CanonicalNodeEvaluator {
    private static final MathContext ARITHMETIC_CONTEXT = MathContext.DECIMAL128;

    private final ArcaneWeave.CanonicalWeave weave;
    private final ArcaneWeave.StandardSymbolRegistry registry;
    private final CanonicalExecutionBudget.Session budget;
    private final CanonicalValueStore values;

    public CanonicalNodeEvaluator(ArcaneWeave.CanonicalWeave weave,
                                  ArcaneWeave.StandardSymbolRegistry registry,
                                  CanonicalExecutionBudget.Session budget) {
        this.weave = Objects.requireNonNull(weave, "weave");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.values = new CanonicalValueStore(weave, registry, budget);
    }

    public enum Status {
        EVALUATED,
        UNKNOWN_GLYPH,
        UNSUPPORTED_SYMBOL,
        INPUT_UNAVAILABLE,
        INVALID_PARAMETER,
        INVALID_RESULT,
        BUDGET_EXHAUSTED
    }

    public record Evaluation(Status status, String glyphId,
                             CanonicalValueStore.RuntimeValue output,
                             String outputPort, String diagnostic) {
        public Evaluation {
            Objects.requireNonNull(status, "status");
            if (status == Status.EVALUATED && (output == null || outputPort == null))
                throw new IllegalArgumentException("Successful evaluation must contain an output.");
        }
        public boolean successful() { return status == Status.EVALUATED; }
    }

    public Evaluation evaluate(String glyphId) {
        if (glyphId == null) return failure(Status.UNKNOWN_GLYPH, null, "Glyph id is required.");
        ArcaneWeave.CanonicalGlyph glyph = weave.glyphs().stream()
                .filter(candidate -> candidate.instanceId().equals(glyphId))
                .findFirst().orElse(null);
        if (glyph == null) return failure(Status.UNKNOWN_GLYPH, glyphId, "Glyph is not part of this weave.");

        ArcaneWeave.StandardSymbolDefinition definition = registry.definition(glyph.symbolId());
        if (definition == null || !definition.instantiable())
            return failure(Status.UNSUPPORTED_SYMBOL, glyphId, "Symbol is not an instantiable standard symbol.");

        CanonicalExecutionBudget.Admission admission = budget.consumeNodeStep();
        if (admission != CanonicalExecutionBudget.Admission.ACCEPTED)
            return failure(Status.BUDGET_EXHAUSTED, glyphId, "Node-step budget is exhausted.");

        try {
            return switch (glyph.symbolId()) {
                case "arcana:reference/number" -> evaluateNumber(glyph);
                case "arcana:reference/position" -> evaluatePosition(glyph);
                case "arcana:data/add" -> evaluateArithmetic(glyph, Arithmetic.ADD);
                case "arcana:data/equal" -> evaluateComparison(glyph, Comparison.EQUAL);
                case "arcana:data/less_than" -> evaluateComparison(glyph, Comparison.LESS_THAN);
                default -> failure(Status.UNSUPPORTED_SYMBOL, glyphId,
                        "No pure-data evaluator is registered for this standard symbol.");
            };
        } catch (NumberFormatException | IllegalArgumentException ex) {
            return failure(Status.INVALID_PARAMETER, glyphId,
                    "A numeric parameter or runtime value is invalid: " + safeMessage(ex));
        } catch (ArithmeticException ex) {
            return failure(Status.INVALID_RESULT, glyphId,
                    "The operation produced a value outside the supported numeric range.");
        }
    }

    public CanonicalValueStore valueStore() { return values; }

    private Evaluation evaluateNumber(ArcaneWeave.CanonicalGlyph glyph) {
        BigDecimal value = new BigDecimal(glyph.parameters().get("value"));
        return publish(glyph.instanceId(), "number", new CanonicalValueStore.NumberValue(value));
    }

    private Evaluation evaluatePosition(ArcaneWeave.CanonicalGlyph glyph) {
        double x = finiteDouble(glyph.parameters().get("x"));
        double y = finiteDouble(glyph.parameters().get("y"));
        double z = finiteDouble(glyph.parameters().get("z"));
        return publish(glyph.instanceId(), "position",
                new CanonicalValueStore.Vector3Value(x, y, z));
    }

    private Evaluation evaluateArithmetic(ArcaneWeave.CanonicalGlyph glyph, Arithmetic operation) {
        BigDecimal left = readNumber(glyph.instanceId(), "left");
        if (left == null) return inputFailure(glyph.instanceId(), "left");
        BigDecimal right = readNumber(glyph.instanceId(), "right");
        if (right == null) return inputFailure(glyph.instanceId(), "right");
        BigDecimal result = switch (operation) {
            case ADD -> left.add(right, ARITHMETIC_CONTEXT);
        };
        return publish(glyph.instanceId(), "result", new CanonicalValueStore.NumberValue(result));
    }

    private Evaluation evaluateComparison(ArcaneWeave.CanonicalGlyph glyph, Comparison operation) {
        BigDecimal left = readNumber(glyph.instanceId(), "left");
        if (left == null) return inputFailure(glyph.instanceId(), "left");
        BigDecimal right = readNumber(glyph.instanceId(), "right");
        if (right == null) return inputFailure(glyph.instanceId(), "right");
        int comparison = left.compareTo(right);
        boolean result = switch (operation) {
            case EQUAL -> comparison == 0;
            case LESS_THAN -> comparison < 0;
        };
        return publish(glyph.instanceId(), "result",
                new CanonicalValueStore.BooleanValue(result));
    }

    private BigDecimal readNumber(String glyphId, String inputPort) {
        CanonicalValueStore.Result resolved = values.readInput(glyphId, inputPort);
        if (resolved.status() != CanonicalValueStore.Status.RESOLVED) return null;
        if (!(resolved.value() instanceof CanonicalValueStore.NumberValue number)) return null;
        return number.value();
    }

    private Evaluation publish(String glyphId, String outputPort,
                               CanonicalValueStore.RuntimeValue value) {
        CanonicalValueStore.Result stored = values.writeOutput(glyphId, outputPort, value);
        if (!stored.successful()) {
            Status status = stored.status() == CanonicalValueStore.Status.BUDGET_EXHAUSTED
                    ? Status.BUDGET_EXHAUSTED : Status.INVALID_RESULT;
            return failure(status, glyphId, stored.diagnostic());
        }
        return new Evaluation(Status.EVALUATED, glyphId, value, outputPort, null);
    }

    private Evaluation inputFailure(String glyphId, String port) {
        return failure(Status.INPUT_UNAVAILABLE, glyphId,
                "Required numeric input '" + port + "' has no available connected value.");
    }

    private static double finiteDouble(String raw) {
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw new NumberFormatException("Non-finite coordinate.");
        return value;
    }

    private static Evaluation failure(Status status, String glyphId, String message) {
        return new Evaluation(status, glyphId, null, null, message);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    private enum Arithmetic { ADD }
    private enum Comparison { EQUAL, LESS_THAN }
}
