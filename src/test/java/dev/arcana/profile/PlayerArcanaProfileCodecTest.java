package dev.arcana.profile;

import dev.arcana.casting.CastingTriggerRecognizer;
import dev.arcana.language.ArcanaLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerArcanaProfileCodecTest {
    private static PlayerArcanaProfile profile() {
        var language = new ArcanaLanguage.LanguageDefinition(
                "player:moonlit", 3,
                Map.of("grim", "spell", "sigil", "invoke"),
                List.of(new ArcanaLanguage.PhraseRule(
                        "when the silver moon awakens", "spell")));
        var triggers = new CastingTriggerRecognizer.TriggerProfile(
                List.of("Astra", "open the veil"),
                List.of("Nora", "close the veil"));
        return new PlayerArcanaProfile(language, triggers);
    }

    @Test
    void profileRoundTripsWithoutLosingLanguageOrTriggerConfiguration() {
        var original = profile();

        var decoded = PlayerArcanaProfileCodec.decode(
                PlayerArcanaProfileCodec.encode(original));

        assertEquals(original, decoded);
        assertEquals(original.language().keywordAliases(),
                decoded.language().keywordAliases());
        assertEquals(original.language().phraseRules(),
                decoded.language().phraseRules());
        assertEquals(original.triggers(), decoded.triggers());
    }

    @Test
    void encodingIsDeterministicForEquivalentProfiles() {
        var first = profile();
        var second = new PlayerArcanaProfile(
                new ArcanaLanguage.LanguageDefinition(
                        "player:moonlit", 3,
                        Map.of("sigil", "invoke", "grim", "spell"),
                        List.of(new ArcanaLanguage.PhraseRule(
                                "when the silver moon awakens", "spell"))),
                new CastingTriggerRecognizer.TriggerProfile(
                        List.of("Astra", "open the veil"),
                        List.of("Nora", "close the veil")));

        assertEquals(PlayerArcanaProfileCodec.encode(first),
                PlayerArcanaProfileCodec.encode(second));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        String json = PlayerArcanaProfileCodec.encode(profile())
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2");

        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode(json));
    }

    @Test
    void rejectsUnknownFieldsInsteadOfSilentlyIgnoringThem() {
        String json = PlayerArcanaProfileCodec.encode(profile())
                .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"trusted\":true");

        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode(json));
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode("{\"schemaVersion\":1}"));
    }

    @Test
    void rejectsMalformedJsonAndNonObjectRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode("{broken"));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode("[]"));
    }

    @Test
    void rejectsOversizedPayloads() {
        String oversized = " ".repeat(PlayerArcanaProfileCodec.MAX_JSON_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileCodec.decode(oversized));
    }

    @Test
    void restoredProfileCanDriveTheSharedLanguageAndVoiceInputPipelines() {
        var restored = PlayerArcanaProfileCodec.decode(
                PlayerArcanaProfileCodec.encode(profile()));

        var preparation = dev.arcana.language.CastingPreparationPipeline.prepare(
                "grim \\"spark\\" { sigil \\"arcana:effect/light\\"(power = 2); }",
                restored.language());
        assertTrue(preparation.successful(), () -> preparation.diagnostics().toString());

        var trigger = CastingTriggerRecognizer.recognize(
                "grim \\"spark\\" {} Astra", restored.triggers());
        assertEquals(CastingTriggerRecognizer.Status.ACTIVATED, trigger.status());
    }

    @Test
    void profileDoesNotContainOrInferLearnedKnowledge() {
        var saved = profile();
        assertNotNull(saved.language());
        assertNotNull(saved.triggers());
        assertEquals(2, saved.language().keywordAliases().size());
        // The profile schema intentionally has no learned-spell or knowledge field.
        assertEquals(3, saved.language().version());
    }

    @Test
    void rejectsOversizedAliasAndPhraseConfigurations() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArcanaLanguage.LanguageDefinition(
                        "player:bad", 1, Map.of("x".repeat(129), "spell"), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new ArcanaLanguage.PhraseRule("x", "z".repeat(4_097)));
    }
}
