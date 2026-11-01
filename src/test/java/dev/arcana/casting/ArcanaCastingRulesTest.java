package dev.arcana.casting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArcanaCastingRulesTest {
    private static ArcanaCastingRules.SpellProfile spell(
            ArcanaCastingRules.Rank rank,
            ArcanaCastingRules.Rank requiredRank,
            ArcanaCastingRules.MediumKind... media) {
        return new ArcanaCastingRules.SpellProfile(
                "arcana:spell/test", rank, requiredRank, List.of(media));
    }

    @Test
    void higherRankCasterCanCastLowerRankSpell() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.MASTER);
        var spell = spell(ArcanaCastingRules.Rank.APPRENTICE,
                ArcanaCastingRules.Rank.APPRENTICE, ArcanaCastingRules.MediumKind.STAFF);
        var staff = new ArcanaCastingRules.MediumProfile(
                ArcanaCastingRules.MediumKind.STAFF,
                ArcanaCastingRules.Rank.ARCHMAGE, true, true);

        var result = ArcanaCastingRules.evaluate(
                caster, spell, staff, ArcanaCastingRules.CastingMode.DIRECT);

        assertTrue(result.allowed(), () -> result.issues().toString());
    }

    @Test
    void lowRankCasterCannotCastHigherRankSpell() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.APPRENTICE);
        var spell = spell(ArcanaCastingRules.Rank.MASTER,
                ArcanaCastingRules.Rank.EXPERT, ArcanaCastingRules.MediumKind.STAFF);
        var staff = new ArcanaCastingRules.MediumProfile(
                ArcanaCastingRules.MediumKind.STAFF,
                ArcanaCastingRules.Rank.SOVEREIGN, true, true);

        var result = ArcanaCastingRules.evaluate(
                caster, spell, staff, ArcanaCastingRules.CastingMode.DIRECT);

        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("SPELL_RANK_TOO_HIGH")));
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("CASTER_RANK_TOO_LOW")));
    }

    @Test
    void mediumCapacityIsIndependentFromCasterRank() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.ARCHMAGE);
        var spell = spell(ArcanaCastingRules.Rank.MASTER,
                ArcanaCastingRules.Rank.MASTER, ArcanaCastingRules.MediumKind.WAND);
        var wand = new ArcanaCastingRules.MediumProfile(
                ArcanaCastingRules.MediumKind.WAND,
                ArcanaCastingRules.Rank.ADEPT, true, false);

        var result = ArcanaCastingRules.evaluate(
                caster, spell, wand, ArcanaCastingRules.CastingMode.DIRECT);

        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("MEDIUM_RANK_LIMIT")));
    }

    @Test
    void grimoireCanStoreButNotDirectlyCastSpells() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.EXPERT);
        var spell = spell(ArcanaCastingRules.Rank.APPRENTICE,
                ArcanaCastingRules.Rank.APPRENTICE, ArcanaCastingRules.MediumKind.GRIMOIRE);
        var book = new ArcanaCastingRules.MediumProfile(
                ArcanaCastingRules.MediumKind.GRIMOIRE,
                ArcanaCastingRules.Rank.MASTER, false, false);

        var result = ArcanaCastingRules.evaluate(
                caster, spell, book, ArcanaCastingRules.CastingMode.DIRECT);

        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("MEDIUM_NOT_DIRECT")));
    }

    @Test
    void rejectsIncompatibleMedium() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.MASTER);
        var spell = spell(ArcanaCastingRules.Rank.ADEPT,
                ArcanaCastingRules.Rank.ADEPT, ArcanaCastingRules.MediumKind.MAGIC_CIRCLE);
        var staff = new ArcanaCastingRules.MediumProfile(
                ArcanaCastingRules.MediumKind.STAFF,
                ArcanaCastingRules.Rank.SOVEREIGN, true, true);

        var result = ArcanaCastingRules.evaluate(
                caster, spell, staff, ArcanaCastingRules.CastingMode.RITUAL);

        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("INCOMPATIBLE_MEDIUM")));
    }
}
