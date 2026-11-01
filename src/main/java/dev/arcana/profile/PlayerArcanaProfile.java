package dev.arcana.profile;

import java.util.Objects;

/**
 * Immutable world-scoped profile for one player.
 *
 * Converter rules are configuration, not learned knowledge. Knowledge and
 * research progression must be stored by a separate system and must not be
 * inferred from the presence of this profile.
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
        language.keywordAliases().forEach((alias, replacement) -> {
            if (alias.length() > 128 || replacement.length() > 32 || alias.isBlank()) {
                throw new IllegalArgumentException("Invalid keyword alias length.");
            }
        });
        language.phraseRules().forEach(rule -> {
            if (rule.sourcePhrase().length() > 256 || rule.canonicalReplacement().length() > 4_096) {
                throw new IllegalArgumentException("Phrase rule exceeds its size limit.");
            }
        });
    }

    public PlayerArcanaProfile(
            ArcanaLanguage.LanguageDefinition language,
            CastingTriggerRecognizer.TriggerProfile triggers) {
        this(CURRENT_SCHEMA_VERSION, language, triggers);
    }
}
