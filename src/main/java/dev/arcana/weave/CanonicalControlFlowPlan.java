package dev.arcana.weave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, validated control-flow description for a canonical weave.
 *
 * This is a compilation artifact, not an interpreter. In particular, a join
 * represents a merge of whichever branch arm was selected, not a barrier that
 * waits for both mutually exclusive arms.
 */
public final class CanonicalControlFlowPlan {
    private CanonicalControlFlowPlan() { }

    public static Compilation compile(ArcaneWeave.CanonicalWeave weave,
                                      ArcaneWeave.StandardSymbolRegistry registry) {
        Objects.requireNonNull(weave, "weave");
        Objects.requireNonNull(registry, "registry");
        ArcaneWeave.SymbolValidation validation = ArcaneWeave.validateCanonical(weave, registry);
        if (!validation.valid()) return new Compilation(null, validation.diagnostics());

        List<ArcaneWeave.CanonicalGlyph> glyphs = weave.glyphs().stream()
                .sorted(Comparator.comparing(ArcaneWeave.CanonicalGlyph::instanceId)).toList();
        List<ArcaneWeave.CanonicalConnection> connections = weave.connections().stream()
                .sorted(Comparator.comparing(ArcaneWeave.CanonicalConnection::fromGlyph)
                        .thenComparing(ArcaneWeave.CanonicalConnection::fromPort)
                        .thenComparing(ArcaneWeave.CanonicalConnection::toGlyph)
                        .thenComparing(ArcaneWeave.CanonicalConnection::toPort)).toList();
        List<ArcaneWeave.CanonicalRegion> regions = weave.regions().stream()
                .sorted(Comparator.comparing(ArcaneWeave.CanonicalRegion::id)).toList();

        List<String> nestedEntries = regions.stream()
                .map(ArcaneWeave.CanonicalRegion::entryGlyph).toList();
        List<String> rootEntries = glyphs.stream()
                .filter(glyph -> glyph.symbolId().equals("arcana:control/entry"))
                .map(ArcaneWeave.CanonicalGlyph::instanceId)
                .filter(id -> !nestedEntries.contains(id))
                .toList();

        List<RegionPlan> regionPlans = regions.stream().map(region -> new RegionPlan(
                region.id(), region.ownerGlyph(), region.ownerPort(), region.entryGlyph(),
                region.exitGlyph(), region.exitPort(), region.memberGlyphs(), region.parentRegionId()))
                .toList();

        List<BranchPlan> branches = new ArrayList<>();
        List<RepeatPlan> repeats = new ArrayList<>();
        for (ArcaneWeave.CanonicalGlyph glyph : glyphs) {
            if (glyph.symbolId().equals("arcana:control/branch")) {
                ArcaneWeave.CanonicalRegion trueArm = findRegion(regions, glyph.instanceId(), "whenTrue");
                ArcaneWeave.CanonicalRegion falseArm = findRegion(regions, glyph.instanceId(), "whenFalse");
                ArcaneWeave.CanonicalConnection condition = connections.stream()
                        .filter(edge -> edge.toGlyph().equals(glyph.instanceId())
                                && edge.toPort().equals("condition"))
                        .findFirst().orElseThrow();
                ArcaneWeave.CanonicalConnection trueExit = findExit(connections, trueArm);
                ArcaneWeave.CanonicalConnection falseExit = findExit(connections, falseArm);
                branches.add(new BranchPlan(glyph.instanceId(),
                        condition.fromGlyph(), condition.fromPort(),
                        trueArm.id(), falseArm.id(), trueExit.toGlyph(),
                        trueExit.toPort(), falseExit.toPort()));
            } else if (glyph.symbolId().equals("arcana:control/repeat")) {
                ArcaneWeave.CanonicalRegion body = findRegion(regions, glyph.instanceId(), "body");
                ArcaneWeave.CanonicalConnection completed = connections.stream()
                        .filter(edge -> edge.fromGlyph().equals(glyph.instanceId())
                                && edge.fromPort().equals("completed"))
                        .findFirst().orElse(null);
                repeats.add(new RepeatPlan(glyph.instanceId(),
                        Integer.parseInt(glyph.parameters().get("maxIterations")), body.id(),
                        completed == null ? null : completed.toGlyph(),
                        completed == null ? null : completed.toPort()));
            }
        }
        return new Compilation(new Plan(weave.id(), weave.version(), rootEntries,
                regionPlans, branches, repeats, connections), List.of());
    }

    private static ArcaneWeave.CanonicalRegion findRegion(
            List<ArcaneWeave.CanonicalRegion> regions, String owner, String port) {
        return regions.stream().filter(region -> region.ownerGlyph().equals(owner)
                && region.ownerPort().equals(port)).findFirst().orElseThrow();
    }

    private static ArcaneWeave.CanonicalConnection findExit(
            List<ArcaneWeave.CanonicalConnection> connections,
            ArcaneWeave.CanonicalRegion region) {
        return connections.stream().filter(edge -> edge.fromGlyph().equals(region.exitGlyph())
                && edge.fromPort().equals(region.exitPort())).findFirst().orElseThrow();
    }

    public record Compilation(Plan plan, List<ArcaneWeave.SymbolDiagnostic> diagnostics) {
        public Compilation {
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if ((plan == null) == diagnostics.isEmpty())
                throw new IllegalArgumentException("Compilation must contain either a plan or diagnostics.");
        }
        public boolean successful() { return plan != null; }
    }

    public record Plan(String weaveId, int version, List<String> rootEntryGlyphs,
                       List<RegionPlan> regions, List<BranchPlan> branches,
                       List<RepeatPlan> repeats,
                       List<ArcaneWeave.CanonicalConnection> connections) {
        public Plan {
            Objects.requireNonNull(weaveId, "weaveId");
            rootEntryGlyphs = List.copyOf(rootEntryGlyphs);
            regions = List.copyOf(regions);
            branches = List.copyOf(branches);
            repeats = List.copyOf(repeats);
            connections = List.copyOf(connections);
        }
    }

    public record RegionPlan(String id, String ownerGlyph, String ownerPort,
                             String entryGlyph, String exitGlyph, String exitPort,
                             List<String> memberGlyphs, String parentRegionId) {
        public RegionPlan { memberGlyphs = List.copyOf(memberGlyphs); }
    }

    public record BranchPlan(String ownerGlyph, String conditionSourceGlyph,
                             String conditionSourcePort, String trueRegionId,
                             String falseRegionId, String joinGlyph,
                             String trueJoinPort, String falseJoinPort) { }

    public record RepeatPlan(String ownerGlyph, int maximumIterations,
                             String bodyRegionId, String completionTargetGlyph,
                             String completionTargetPort) { }
}
