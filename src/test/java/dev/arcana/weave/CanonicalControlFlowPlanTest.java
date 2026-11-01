package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalControlFlowPlanTest {
    private final ArcaneWeave.StandardSymbolRegistry registry =
            ArcaneWeave.StandardSymbolRegistry.builtins();

    @Test
    void compilesBranchAsMutuallyExclusiveArmsThatMergeAtOneJoin() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:branch_plan", 1, List.of(
                glyph("root", "arcana:control/entry", Map.of()),
                glyph("one", "arcana:reference/number", Map.of("value", "1")),
                glyph("two", "arcana:reference/number", Map.of("value", "2")),
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
                region("trueArm", "branch", "whenTrue", "yes", "yes", "flow", List.of("yes")),
                region("falseArm", "branch", "whenFalse", "no", "no", "flow", List.of("no"))
        ));

        var result = CanonicalControlFlowPlan.compile(weave, registry);

        assertTrue(result.successful(), () -> result.diagnostics().toString());
        var plan = result.plan();
        assertEquals(List.of("root"), plan.rootEntryGlyphs());
        assertEquals(1, plan.branches().size());
        var branch = plan.branches().getFirst();
        assertEquals("condition", branch.conditionSourceGlyph());
        assertEquals("join", branch.joinGlyph());
        assertEquals("left", branch.trueJoinPort());
        assertEquals("right", branch.falseJoinPort());
        assertNotEquals(branch.trueRegionId(), branch.falseRegionId());
    }

    @Test
    void compilesRepeatWithBoundedBodyAndCompletionContinuation() {
        var weave = new ArcaneWeave.CanonicalWeave("arcana:repeat_plan", 1, List.of(
                glyph("root", "arcana:control/entry", Map.of()),
                glyph("repeat", "arcana:control/repeat", Map.of("maxIterations", "7")),
                glyph("body", "arcana:control/entry", Map.of()),
                glyph("after", "arcana:control/entry", Map.of())
        ), List.of(
                edge("root", "flow", "repeat", "flow"),
                edge("repeat", "body", "body", "flow"),
                edge("repeat", "completed", "after", "flow")
        ), List.of(
                region("bodyRegion", "repeat", "body", "body", "body", "flow", List.of("body"))
        ));

        var result = CanonicalControlFlowPlan.compile(weave, registry);

        assertTrue(result.successful(), () -> result.diagnostics().toString());
        assertEquals(List.of("root", "after"), result.plan().rootEntryGlyphs());
        var repeat = result.plan().repeats().getFirst();
        assertEquals(7, repeat.maximumIterations());
        assertEquals("bodyRegion", repeat.bodyRegionId());
        assertEquals("after", repeat.completionTargetGlyph());
    }

    @Test
    void refusesToCompileInvalidWeaves() {
        var invalid = new ArcaneWeave.CanonicalWeave("arcana:invalid_plan", 1,
                List.of(glyph("unknown", "arcana:not_registered", Map.of())));

        var result = CanonicalControlFlowPlan.compile(invalid, registry);

        assertFalse(result.successful());
        assertNull(result.plan());
        assertTrue(result.diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("CANONICAL_UNKNOWN_SYMBOL")));
    }

    private static ArcaneWeave.CanonicalGlyph glyph(
            String id, String symbol, Map<String, String> parameters) {
        return new ArcaneWeave.CanonicalGlyph(id, symbol, parameters);
    }

    private static ArcaneWeave.CanonicalConnection edge(
            String from, String fromPort, String to, String toPort) {
        return new ArcaneWeave.CanonicalConnection(from, fromPort, to, toPort);
    }

    private static ArcaneWeave.CanonicalRegion region(
            String id, String owner, String ownerPort, String entry, String exit,
            String exitPort, List<String> members) {
        return new ArcaneWeave.CanonicalRegion(
                id, owner, ownerPort, entry, exit, exitPort, members, null);
    }
}
