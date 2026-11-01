package dev.arcana.weave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArcaneWeaveTest {
    private static ArcaneWeave.Weave simpleWeave() {
        var invocation = new ArcaneWeave.Glyph("begin", ArcaneWeave.GlyphKind.INVOCATION,
                Map.of("flow", ArcaneWeave.Essence.MANA), Map.of(), Map.of());
        var manifestation = new ArcaneWeave.Glyph("glimmer", ArcaneWeave.GlyphKind.MANIFESTATION,
                Map.of(), Map.of("power", ArcaneWeave.Essence.MANA), Map.of());
        return new ArcaneWeave.Weave("arcana:test_glimmer", 1, List.of(invocation, manifestation),
                List.of(new ArcaneWeave.ManaVein("begin", "flow", "glimmer", "power")),
                Set.of("begin"), Set.of("glimmer"));
    }

    @Test
    void validatesLogicalWeaveWithoutKnowingItsHost() {
        assertTrue(ArcaneWeave.validate(simpleWeave()).valid());
    }

    @Test
    void installationCanUseDifferentHostFamilies() {
        var weave = simpleWeave();
        var page = new ArcaneWeave.Host("arcana:book_page_1", ArcaneWeave.HostKind.PAGE, 1, Set.of("ink"));
        var crystal = new ArcaneWeave.Host("arcana:focus_crystal", ArcaneWeave.HostKind.ITEM, 1, Set.of("core"));
        var placements = List.of(new ArcaneWeave.Placement("begin", page.id(), "ink"),
                new ArcaneWeave.Placement("glimmer", crystal.id(), "core"));
        var disconnected = new ArcaneWeave.Installation("arcana:install_1", weave.id(),
                List.of(page, crystal), placements, Set.of());
        assertTrue(ArcaneWeave.validate(weave).valid());
        assertFalse(ArcaneWeave.validateInstallation(weave, disconnected).valid());

        var connected = new ArcaneWeave.Installation("arcana:install_2", weave.id(),
                List.of(page, crystal), placements,
                Set.of(new ArcaneWeave.HostLink(page.id(), crystal.id())));
        assertTrue(ArcaneWeave.validateInstallation(weave, connected).valid());
    }

    @Test
    void rejectsIncompatibleManaVeinEssences() {
        var invocation = new ArcaneWeave.Glyph("begin", ArcaneWeave.GlyphKind.INVOCATION,
                Map.of("flow", ArcaneWeave.Essence.MANA), Map.of(), Map.of());
        var manifestation = new ArcaneWeave.Glyph("mark", ArcaneWeave.GlyphKind.MANIFESTATION,
                Map.of(), Map.of("target", ArcaneWeave.Essence.TEXT), Map.of());
        var weave = new ArcaneWeave.Weave("arcana:bad_weave", 1, List.of(invocation, manifestation),
                List.of(new ArcaneWeave.ManaVein("begin", "flow", "mark", "target")),
                Set.of("begin"), Set.of("mark"));
        assertTrue(ArcaneWeave.validate(weave).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("WEAVE_ESSENCE_MISMATCH")));
    }

    @Test
    void rejectsRawCyclesAndRequiresExplicitBoundedRepetitionGlyph() {
        var invocation = new ArcaneWeave.Glyph("begin", ArcaneWeave.GlyphKind.INVOCATION,
                Map.of("out", ArcaneWeave.Essence.SIGNAL), Map.of(), Map.of());
        var transform = new ArcaneWeave.Glyph("shape", ArcaneWeave.GlyphKind.TRANSFORM,
                Map.of("out", ArcaneWeave.Essence.SIGNAL), Map.of("in", ArcaneWeave.Essence.SIGNAL), Map.of());
        var manifestation = new ArcaneWeave.Glyph("end", ArcaneWeave.GlyphKind.MANIFESTATION,
                Map.of(), Map.of("in", ArcaneWeave.Essence.SIGNAL), Map.of());
        var weave = new ArcaneWeave.Weave("arcana:cycle", 1, List.of(invocation, transform, manifestation),
                List.of(new ArcaneWeave.ManaVein("begin", "out", "shape", "in"),
                        new ArcaneWeave.ManaVein("shape", "out", "shape", "in"),
                        new ArcaneWeave.ManaVein("shape", "out", "end", "in")),
                Set.of("begin"), Set.of("end"));
        assertTrue(ArcaneWeave.validate(weave).diagnostics().stream()
                .anyMatch(issue -> issue.code().equals("WEAVE_UNBOUNDED_CYCLE")));
    }
}