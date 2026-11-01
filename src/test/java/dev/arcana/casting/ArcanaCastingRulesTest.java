package dev.arcana.casting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArcanaCastingRulesTest {
    private static ArcanaCastingRules.SpellProfile working(
            ArcanaCastingRules.Rank complexity, ArcanaCastingRules.MediumKind... media) {
        return new ArcanaCastingRules.SpellProfile("arcana:working/test", complexity, List.of(media));
    }

    @Test
    void experiencedCasterCanUseSimpleWorking() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.MASTER);
        var working = working(ArcanaCastingRules.Rank.APPRENTICE, ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL);
        var focus = new ArcanaCastingRules.MediumProfile(ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL,
                ArcanaCastingRules.Rank.ARCHMAGE, true, true);
        assertTrue(ArcanaCastingRules.evaluate(caster, working, focus, ArcanaCastingRules.CastingMode.DIRECT).allowed());
    }

    @Test
    void casterCannotControlWorkingBeyondCurrentCapacity() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.APPRENTICE);
        var working = working(ArcanaCastingRules.Rank.MASTER, ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL);
        var powerfulFocus = new ArcanaCastingRules.MediumProfile(ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL,
                ArcanaCastingRules.Rank.SOVEREIGN, true, true);
        var result = ArcanaCastingRules.evaluate(caster, working, powerfulFocus, ArcanaCastingRules.CastingMode.DIRECT);
        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("CASTER_CONTROL_LIMIT")));
        assertFalse(result.issues().stream().anyMatch(i -> i.code().contains("KNOWLEDGE") || i.code().contains("LEARN")));
    }

    @Test
    void carrierCapacityIsIndependentFromCasterControl() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.ARCHMAGE);
        var working = working(ArcanaCastingRules.Rank.MASTER, ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL);
        var weakFocus = new ArcanaCastingRules.MediumProfile(ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL,
                ArcanaCastingRules.Rank.ADEPT, true, false);
        var result = ArcanaCastingRules.evaluate(caster, working, weakFocus, ArcanaCastingRules.CastingMode.DIRECT);
        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("MEDIUM_CAPACITY_LIMIT")));
    }

    @Test
    void grimoireCanStoreButDoesNotAutomaticallyActivate() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.EXPERT);
        var working = working(ArcanaCastingRules.Rank.APPRENTICE, ArcanaCastingRules.MediumKind.GRIMOIRE);
        var book = new ArcanaCastingRules.MediumProfile(ArcanaCastingRules.MediumKind.GRIMOIRE,
                ArcanaCastingRules.Rank.MASTER, false, false);
        var result = ArcanaCastingRules.evaluate(caster, working, book, ArcanaCastingRules.CastingMode.DIRECT);
        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("MEDIUM_NOT_DIRECT")));
    }

    @Test
    void rejectsIncompatibleCarrier() {
        var caster = new ArcanaCastingRules.CasterProfile(ArcanaCastingRules.Rank.MASTER);
        var working = working(ArcanaCastingRules.Rank.ADEPT, ArcanaCastingRules.MediumKind.MAGIC_CIRCLE);
        var focus = new ArcanaCastingRules.MediumProfile(ArcanaCastingRules.MediumKind.FOCUS_CRYSTAL,
                ArcanaCastingRules.Rank.SOVEREIGN, true, true);
        var result = ArcanaCastingRules.evaluate(caster, working, focus, ArcanaCastingRules.CastingMode.RITUAL);
        assertFalse(result.allowed());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("INCOMPATIBLE_MEDIUM")));
    }
}