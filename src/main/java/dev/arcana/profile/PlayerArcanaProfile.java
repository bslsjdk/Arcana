package dev.arcana.profile;

import dev.arcana.casting.CastingTriggerRecognizer;
import dev.arcana.language.ArcanaLanguage;

import java.util.Objects;

/**
 * World-scoped player configuration for Arcana's language converter and
 * ordinary voice-command trigger phrases.
 *
 * This deliberately contains no learned-knowledge state. A language converter
 * is configuration data and is not proof that a player understands a language.
 */
public record PlayerArcanaProfile(
        int schemaVersion,
        ArcanaLanguage.LanguageDefinition language,
        CastingTriggerRecognizer.TriggerProfile triggers) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PlayerArcanaProfile {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported player profile schema version: " + schemaVersion);
        }
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(triggers, "triggers");
        if (language.keywordAliases().size() > 512) {
            throw new IllegalArgumentException("A player language may define at most 512 keyword aliases.");
        }
        if (language.phraseRules().size() > 256) {
            throw new IllegalArgumentException("A player language may define at most 256 phrase rules.");
        }
    }

    public PlayerArcanaProfile(
            ArcanaLanguage.LanguageDefinition language,
            CastingTriggerRecognizer.TriggerProfile triggers) {
        this(CURRENT_SCHEMA_VERSION, language, triggers);
    }
}
