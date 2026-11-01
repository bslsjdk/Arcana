package dev.arcana.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.arcana.casting.CastingTriggerRecognizer;
import dev.arcana.language.ArcanaLanguage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict, bounded JSON codec for world-scoped player Arcana configuration.
 * Unknown fields and unsupported schema versions are rejected rather than
 * silently interpreted with potentially different semantics.
 */
public final class PlayerArcanaProfileCodec {
    public static final int MAX_JSON_LENGTH = 262_144;
    private static final int MAX_ALIAS_LENGTH = 128;
    private static final int MAX_REPLACEMENT_LENGTH = 4_096;
    private static final int MAX_PHRASE_LENGTH = 256;
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private PlayerArcanaProfileCodec() { }

    public static String encode(PlayerArcanaProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile cannot be null.");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", profile.schemaVersion());

        JsonObject language = new JsonObject();
        language.addProperty("id", profile.language().id());
        language.addProperty("version", profile.language().version());

        JsonObject aliases = new JsonObject();
        profile.language().keywordAliases().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> aliases.addProperty(entry.getKey(), entry.getValue()));
        language.add("keywordAliases", aliases);

        JsonArray phraseRules = new JsonArray();
        for (ArcanaLanguage.PhraseRule rule : profile.language().phraseRules()) {
            JsonObject item = new JsonObject();
            item.addProperty("sourcePhrase", rule.sourcePhrase());
            item.addProperty("canonicalReplacement", rule.canonicalReplacement());
            phraseRules.add(item);
        }
        language.add("phraseRules", phraseRules);
        root.add("language", language);

        JsonObject triggers = new JsonObject();
        triggers.add("activationPhrases", strings(profile.triggers().activationPhrases()));
        triggers.add("cancellationPhrases", strings(profile.triggers().cancellationPhrases()));
        root.add("triggers", triggers);

        String encoded = GSON.toJson(root);
        if (encoded.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Encoded player profile exceeds the size limit.");
        }
        return encoded;
    }

    public static PlayerArcanaProfile decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Profile JSON cannot be null or blank.");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("Profile JSON exceeds the size limit.");
        }

        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw invalid("Root must be a JSON object.");
            JsonObject root = parsed.getAsJsonObject();
            requireKeys(root, Set.of("schemaVersion", "language", "triggers"), "root");

            int schemaVersion = integer(root, "schemaVersion");
            if (schemaVersion != PlayerArcanaProfile.CURRENT_SCHEMA_VERSION) {
                throw invalid("Unsupported player profile schema version: " + schemaVersion);
            }

            JsonObject languageObject = object(root, "language");
            requireKeys(languageObject,
                    Set.of("id", "version", "keywordAliases", "phraseRules"), "language");

            String languageId = string(languageObject, "id", 256);
            int languageVersion = integer(languageObject, "version");

            JsonObject aliasesObject = object(languageObject, "keywordAliases");
            if (aliasesObject.size() > 512) {
                throw invalid("A player language may define at most 512 keyword aliases.");
            }
            Map<String, String> aliases = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : aliasesObject.entrySet()) {
                if (entry.getKey().length() > MAX_ALIAS_LENGTH || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw invalid("Invalid keyword alias entry.");
                }
                aliases.put(entry.getKey(), entry.getValue().getAsString());
            }

            JsonArray rulesArray = array(languageObject, "phraseRules");
            if (rulesArray.size() > 256) {
                throw invalid("A player language may define at most 256 phrase rules.");
            }
            List<ArcanaLanguage.PhraseRule> rules = new ArrayList<>();
            for (JsonElement element : rulesArray) {
                if (!element.isJsonObject()) throw invalid("Phrase rule must be an object.");
                JsonObject rule = element.getAsJsonObject();
                requireKeys(rule, Set.of("sourcePhrase", "canonicalReplacement"), "phrase rule");
                String sourcePhrase = string(rule, "sourcePhrase", MAX_PHRASE_LENGTH);
                String replacement = string(rule, "canonicalReplacement", MAX_REPLACEMENT_LENGTH);
                rules.add(new ArcanaLanguage.PhraseRule(sourcePhrase, replacement));
            }

            ArcanaLanguage.LanguageDefinition language =
                    new ArcanaLanguage.LanguageDefinition(languageId, languageVersion, aliases, rules);

            JsonObject triggerObject = object(root, "triggers");
            requireKeys(triggerObject,
                    Set.of("activationPhrases", "cancellationPhrases"), "triggers");
            List<String> activation = stringList(triggerObject, "activationPhrases");
            List<String> cancellation = stringList(triggerObject, "cancellationPhrases");
            CastingTriggerRecognizer.TriggerProfile triggerProfile =
                    new CastingTriggerRecognizer.TriggerProfile(activation, cancellation);

            return new PlayerArcanaProfile(schemaVersion, language, triggerProfile);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (JsonParseException | UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Invalid player profile JSON.", exception);
        }
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static List<String> stringList(JsonObject object, String key) {
        JsonArray array = array(object, key);
        if (array.size() > CastingTriggerRecognizer.MAX_TRIGGERS_PER_KIND) {
            throw invalid("Too many trigger phrases.");
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid("Trigger phrases must be strings.");
            }
            String value = element.getAsString();
            if (value.length() > MAX_PHRASE_LENGTH) throw invalid("Trigger phrase is too long.");
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw invalid("Expected object field: " + key);
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw invalid("Expected array field: " + key);
        return value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String key, int maximumLength) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid("Expected string field: " + key);
        }
        String result = value.getAsString();
        if (result.isBlank() || result.length() > maximumLength) {
            throw invalid("Invalid string length for field: " + key);
        }
        return result;
    }

    private static int integer(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid("Expected integer field: " + key);
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid("Field must be an exact integer: " + key);
        }
    }

    private static void requireKeys(JsonObject object, Set<String> allowed, String label) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw invalid("Unknown " + label + " field: " + key);
        }
        for (String key : allowed) {
            if (!object.has(key)) throw invalid("Missing " + label + " field: " + key);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
