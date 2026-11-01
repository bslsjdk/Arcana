package dev.arcana.casting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loader-independent prototype for caster control, working complexity, and
 * carrier limits. Rank is a coarse control-capacity measure, never a knowledge unlock.
 */
public final class ArcanaCastingRules {
    private ArcanaCastingRules() { }

    public static CastingResult evaluate(CasterProfile caster, SpellProfile spell,
                                         MediumProfile medium, CastingMode mode) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(mode, "mode");
        List<CastingIssue> issues = new ArrayList<>();
        if (!caster.controlCapacity().includes(spell.complexity()))
            issues.add(new CastingIssue("CASTER_CONTROL_LIMIT",
                    "The working exceeds the caster's current control capacity."));
        if (!medium.maximumComplexity().includes(spell.complexity()))
            issues.add(new CastingIssue("MEDIUM_CAPACITY_LIMIT",
                    "This carrier cannot safely sustain a working of this complexity."));
        if (mode == CastingMode.DIRECT && !medium.supportsDirectCasting())
            issues.add(new CastingIssue("MEDIUM_NOT_DIRECT",
                    "This carrier does not expose a direct activation interface."));
        if (mode == CastingMode.RITUAL && !medium.supportsRitualCasting())
            issues.add(new CastingIssue("MEDIUM_NOT_RITUAL",
                    "This carrier cannot anchor a ritual."));
        if (!spell.allowedMedia().contains(medium.kind()))
            issues.add(new CastingIssue("INCOMPATIBLE_MEDIUM",
                    "The working is not compatible with this carrier interface."));
        return new CastingResult(issues);
    }

    /** Legacy progression labels retained as configurable control-capacity tiers. */
    public enum Rank {
        NOVICE(0), APPRENTICE(1), ADEPT(2), EXPERT(3), MASTER(4), ARCHMAGE(5), SOVEREIGN(6);
        private final int value;
        Rank(int value) { this.value = value; }
        public int value() { return value; }
        public boolean includes(Rank required) { return value >= required.value; }
    }

    public enum MediumKind { GRIMOIRE, FOCUS_CRYSTAL, SCROLL, MAGIC_CIRCLE }
    public enum CastingMode { DIRECT, RITUAL }

    public record CasterProfile(Rank controlCapacity) {
        public CasterProfile { Objects.requireNonNull(controlCapacity, "controlCapacity"); }
    }

    /** The old class name is retained temporarily; rank now means complexity only. */
    public record SpellProfile(String id, Rank complexity, List<MediumKind> allowedMedia) {
        public SpellProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(complexity, "complexity");
            allowedMedia = List.copyOf(Objects.requireNonNull(allowedMedia, "allowedMedia"));
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Working id must use namespace:path format.");
            if (allowedMedia.isEmpty()) throw new IllegalArgumentException("A working must declare at least one compatible carrier.");
        }
    }

    public record MediumProfile(MediumKind kind, Rank maximumComplexity,
                                boolean supportsDirectCasting, boolean supportsRitualCasting) {
        public MediumProfile {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(maximumComplexity, "maximumComplexity");
        }
    }

    public record CastingIssue(String code, String message) {
        public CastingIssue { Objects.requireNonNull(code); Objects.requireNonNull(message); }
    }
    public record CastingResult(List<CastingIssue> issues) {
        public CastingResult { issues = List.copyOf(issues); }
        public boolean allowed() { return issues.isEmpty(); }
    }
}