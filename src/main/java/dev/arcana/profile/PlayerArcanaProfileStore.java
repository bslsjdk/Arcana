package dev.arcana.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe, world-scoped index of player configuration profiles.
 * Serialization is deterministic and rejects partial/unknown data.
 */
public final class PlayerArcanaProfileStore {
    public static final int STORE_SCHEMA_VERSION = 1;
    public static final int MAX_PROFILES = 4_096;
    public static final int MAX_STORE_JSON_LENGTH = 4_194_304;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<UUID, PlayerArcanaProfile> profiles = new LinkedHashMap<>();

    public synchronized Optional<PlayerArcanaProfile> get(UUID playerId) {
        return Optional.ofNullable(profiles.get(requireId(playerId)));
    }

    public synchronized Map<UUID, PlayerArcanaProfile> snapshot() {
        return Map.copyOf(profiles);
    }

    public synchronized void put(UUID playerId, PlayerArcanaProfile profile) {
        UUID id = requireId(playerId);
        if (profile == null) throw new IllegalArgumentException("Profile cannot be null.");
        if (!profiles.containsKey(id) && profiles.size() >= MAX_PROFILES) {
            throw new IllegalStateException("World profile limit reached.");
        }
        profiles.put(id, profile);
    }

    public synchronized boolean remove(UUID playerId) {
        return profiles.remove(requireId(playerId)) != null;
    }

    public synchronized int size() {
        return profiles.size();
    }

    public synchronized String encode() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", STORE_SCHEMA_VERSION);
        JsonObject entries = new JsonObject();
        profiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey().toString(),
                        JsonParser.parseString(PlayerArcanaProfileCodec.encode(entry.getValue()))));
        root.add("profiles", entries);

        String json = GSON.toJson(root);
        if (json.length() > MAX_STORE_JSON_LENGTH) {
            throw new IllegalStateException("World player profile data exceeds the storage size limit.");
        }
        return json;
    }

    public static PlayerArcanaProfileStore decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("World profile data cannot be null or blank.");
        }
        if (json.length() > MAX_STORE_JSON_LENGTH) {
            throw new IllegalArgumentException("World profile data exceeds the storage size limit.");
        }

        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid world profile JSON.", exception);
        }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("World profile root must be an object.");
        JsonObject root = parsed.getAsJsonObject();
        requireKeys(root, Set.of("schemaVersion", "profiles"), "world profile root");

        JsonElement versionElement = root.get("schemaVersion");
        if (!versionElement.isJsonPrimitive() || !versionElement.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("World profile schema version must be an integer.");
        }
        int version;
        try {
            version = versionElement.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("World profile schema version must be an integer.", exception);
        }
        if (version != STORE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world profile schema version: " + version);
        }

        JsonElement profilesElement = root.get("profiles");
        if (!profilesElement.isJsonObject()) {
            throw new IllegalArgumentException("World profiles field must be an object.");
        }
        JsonObject entries = profilesElement.getAsJsonObject();
        if (entries.size() > MAX_PROFILES) {
            throw new IllegalArgumentException("World player profile limit exceeded.");
        }

        PlayerArcanaProfileStore store = new PlayerArcanaProfileStore();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(entry.getKey());
                if (!playerId.toString().equals(entry.getKey())) {
                    throw new IllegalArgumentException("Player UUID must use canonical lowercase form.");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid player UUID in world profile data.", exception);
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Player profile entry must be an object.");
            }
            PlayerArcanaProfile profile = PlayerArcanaProfileCodec.decode(
                    GSON.toJson(entry.getValue()));
            store.profiles.put(playerId, profile);
        }
        return store;
    }

    private static UUID requireId(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("Player UUID cannot be null.");
        return playerId;
    }

    private static void requireKeys(JsonObject object, Set<String> allowed, String label) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown " + label + " field: " + key);
        }
        for (String key : allowed) {
            if (!object.has(key)) throw new IllegalArgumentException("Missing " + label + " field: " + key);
        }
    }
}
