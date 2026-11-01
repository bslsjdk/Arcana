package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalValueStoreTest {
    private final ArcaneWeave.StandardSymbolRegistry registry =
            ArcaneWeave.StandardSymbolRegistry.builtins();

    private static CanonicalExecutionBudget.Session budget(int values) {
        return new CanonicalExecutionBudget.Session(
                new CanonicalExecutionBudget.Limits(100, 8, 16, values));
    }

    private ArcaneWeave.CanonicalWeave connectedNumbers() {
        return new ArcaneWeave.CanonicalWeave("arcana:value_test", 1, List.of(
                glyph("left", "arcana:reference/number", Map.of("value", "12.5")),
                glyph("right", "arcana:reference/number", Map.of("value", "2")),
                glyph("sum", "arcana:data/add", Map.of())
        ), List.of(
                edge("left", "number", "sum", "left"),
                edge("right", "number", "sum", "right")
        ));
    }

    @Test
    void resolvesOnlyValuesPublishedByTheConnectedStandardOutput() {
        var weave = connectedNumbers();
        var budget = budget(4);
        var store = new CanonicalValueStore(registry, budget);

        assertEquals(CanonicalValueStore.Status.VALUE_NOT_AVAILABLE,
                store.readInput(weave, "sum", "left").status());
        var source = weave.glyphs().getFirst();
        assertEquals(CanonicalValueStore.Status.STORED,
                store.writeOutput(source, "number", new CanonicalValueStore.NumberValue("12.5")).status());

        var resolved = store.readInput(weave, "sum", "left");
        assertEquals(CanonicalValueStore.Status.RESOLVED, resolved.status());
        assertEquals(new CanonicalValueStore.NumberValue("12.5"), resolved.value());
    }

    @Test
    void rejectsUnknownPortsAndMismatchedRuntimeTypes() {
        var store = new CanonicalValueStore(registry, budget(4));
        var number = glyph("number", "arcana:reference/number", Map.of("value", "3"));

        assertEquals(CanonicalValueStore.Status.UNKNOWN_PORT,
                store.writeOutput(number, "missing", new CanonicalValueStore.NumberValue("3")).status());
        assertEquals(CanonicalValueStore.Status.TYPE_MISMATCH,
                store.writeOutput(number, "number", new CanonicalValueStore.BooleanValue(true)).status());
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void replacementDoesNotConsumeAnAdditionalValueSlot() {
        var budget = budget(1);
        var store = new CanonicalValueStore(registry, budget);
        var number = glyph("number", "arcana:reference/number", Map.of("value", "3"));

        assertEquals(CanonicalValueStore.Status.STORED,
                store.writeOutput(number, "number", new CanonicalValueStore.NumberValue("3")).status());
        assertEquals(CanonicalValueStore.Status.REPLACED,
                store.writeOutput(number, "number", new CanonicalValueStore.NumberValue("4")).status());
        assertEquals(1, budget.snapshot().runtimeValues());
        assertEquals(new CanonicalValueStore.NumberValue("4"),
                store.snapshot().get(new CanonicalValueStore.PortAddress("number", "number")));
    }

    @Test
    void refusesNewValuesWhenBudgetIsExhaustedWithoutChangingStore() {
        var budget = budget(1);
        var store = new CanonicalValueStore(registry, budget);
        var first = glyph("first", "arcana:reference/number", Map.of("value", "1"));
        var second = glyph("second", "arcana:reference/number", Map.of("value", "2"));

        assertEquals(CanonicalValueStore.Status.STORED,
                store.writeOutput(first, "number", new CanonicalValueStore.NumberValue("1")).status());
        assertEquals(CanonicalValueStore.Status.BUDGET_EXHAUSTED,
                store.writeOutput(second, "number", new CanonicalValueStore.NumberValue("2")).status());
        assertEquals(1, store.snapshot().size());
        assertEquals(1, budget.snapshot().runtimeValues());
    }

    @Test
    void clearingGlyphOutputsReleasesBudgetSlots() {
        var budget = budget(2);
        var store = new CanonicalValueStore(registry, budget);
        var position = glyph("position", "arcana:reference/position",
                Map.of("x", "1", "y", "2", "z", "3"));

        assertEquals(CanonicalValueStore.Status.STORED,
                store.writeOutput(position, "position",
                        new CanonicalValueStore.Vector3Value(1, 2, 3)).status());
        assertEquals(1, budget.snapshot().runtimeValues());
        store.clearGlyph("position");
        assertTrue(store.snapshot().isEmpty());
        assertEquals(0, budget.snapshot().runtimeValues());
    }

    @Test
    void inputResolutionRequiresAnExplicitConnection() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:unbound", 1, List.of(
                glyph("sum", "arcana:data/add", Map.of())
        ));
        var store = new CanonicalValueStore(registry, budget(2));

        assertEquals(CanonicalValueStore.Status.UNBOUND_INPUT,
                store.readInput(weave, "sum", "left").status());
    }

    @Test
    void valuesAndSnapshotsAreImmutableAndBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalValueStore.NumberValue("1e100"));
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalValueStore.Vector3Value(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalValueStore.TextValue("x".repeat(4097)));

        var store = new CanonicalValueStore(registry, budget(2));
        var number = glyph("number", "arcana:reference/number", Map.of("value", "1"));
        store.writeOutput(number, "number", new CanonicalValueStore.NumberValue("1"));
        assertThrows(UnsupportedOperationException.class, () -> store.snapshot().clear());
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
