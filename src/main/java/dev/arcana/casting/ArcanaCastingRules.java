package dev.arcana.casting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure, loader-independent rules for deciding whether a spell can be cast
 * through a particular medium. This class does not consume resources or
 * perform any Minecraft world mutation.
 */
public final class ArcanaCastingRules {
    private ArcanaCastingRules() { }

    public static CastingResult evaluate(
            CasterProfile caster,
            SpellProfile spell,
            MediumProfile medium,
            CastingMode mode) {
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(spell, "spell");
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(mode, "mode");

        List<CastingIssue> issues = new ArrayList<>();

        if (caster.rank().value() < spell.requiredCasterRank().value()) {
            issues.add(new CastingIssue("CASTER_RANK_TOO_LOW",
                    "The caster has not reached the minimum rank required by this spell."));
        }

        if (spell.rank().value() > caster.rank().value()) {
            issues.add(new CastingIssue("SPELL_RANK_TOO_HIGH",
                    "The spell rank exceeds the caster's current rank."));
        }

        if (spell.rank().value() > medium.maximumSpellRank().value()) {
            issues.add(new CastingIssue("MEDIUM_RANK_LIMIT",
                    "This medium cannot safely channel a spell of this rank."));
        }

        if (mode == CastingMode.DIRECT && !medium.supportsDirectCasting()) {
            issues.add(new CastingIssue("MEDIUM_NOT_DIRECT",
                    "This medium stores or teaches spells but cannot cast them directly."));
        }

        if (mode == CastingMode.RITUAL && !medium.supportsRitualCasting()) {
            issues.add(new CastingIssue("MEDIUM_NOT_RITUAL",
                    "This medium cannot anchor a ritual."));
        }

        if (!spell.allowedMedia().contains(medium.kind())) {
            issues.add(new CastingIssue("INCOMPATIBLE_MEDIUM",
                    "The spell is not designed for this kind of casting medium."));
        }

        return new CastingResult(issues);
    }

    public enum Rank {
        NOVICE(0), APPRENTICE(1), ADEPT(2), EXPERT(3), MASTER(4), ARCHMAGE(5), SOVEREIGN(6);

        private final int value;
        Rank(int value) { this.value = value; }
        public int value() { return value; }

        public boolean includes(Rank required) {
            return value >= required.value;
        }
    }

    public enum MediumKind {
        GRIMOIRE,
        FOCUS_CRYSTAL,
        
        SCROLL,
        MAGIC_CIRCLE
    }

    public enum CastingMode {
        DIRECT,
        RITUAL
    }

    public record CasterProfile(Rank rank) {
        public CasterProfile { Objects.requireNonNull(rank, "rank"); }
    }

    public record SpellProfile(
            String id,
            Rank rank,
            Rank requiredCasterRank,
            List<MediumKind> allowedMedia) {
        public SpellProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(requiredCasterRank, "requiredCasterRank");
            allowedMedia = List.copyOf(allowedMedia);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Spell id must use namespace:path format.");
            }
            if (allowedMedia.isEmpty()) {
                throw new IllegalArgumentException("A spell must declare at least one allowed medium.");
            }
        }
    }

    public record MediumProfile(
            MediumKind kind,
            Rank maximumSpellRank,
            boolean supportsDirectCasting,
            boolean supportsRitualCasting) {
        public MediumProfile {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(maximumSpellRank, "maximumSpellRank");
        }
    }

    public record CastingIssue(String code, String message) {
        public CastingIssue {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    public record CastingResult(List<CastingIssue> issues) {
        public CastingResult { issues = List.copyOf(issues); }
        public boolean allowed() { return issues.isEmpty(); }
    }
}
