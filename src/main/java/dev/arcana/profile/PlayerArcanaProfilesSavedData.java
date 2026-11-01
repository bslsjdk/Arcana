package dev.arcana.profile;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative, world-scoped persistence for player language and
 * voice-command trigger configuration.
 *
 * The data is attached to the Overworld so all dimensions in one save share
 * the same player profiles. A different world has a different SavedData file.
 */
public final class PlayerArcanaProfilesSavedData extends SavedData {
    private static final String DATA_NAME = "arcana_player_profiles";
    private static final String PROFILES_KEY = "profiles_json_utf8";

    public static final Factory<PlayerArcanaProfilesSavedData> FACTORY =
            new Factory<>(PlayerArcanaProfilesSavedData::new,
                    PlayerArcanaProfilesSavedData::load, null);

    private final PlayerArcanaProfileStore store;

    public PlayerArcanaProfilesSavedData() {
        this(new PlayerArcanaProfileStore());
    }

    private PlayerArcanaProfilesSavedData(PlayerArcanaProfileStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public static PlayerArcanaProfilesSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<PlayerArcanaProfile> getProfile(UUID playerId) {
        return store.get(playerId);
    }

    public Map<UUID, PlayerArcanaProfile> snapshot() {
        return store.snapshot();
    }

    public void putProfile(UUID playerId, PlayerArcanaProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (store.get(playerId).filter(profile::equals).isPresent()) return;
        store.put(playerId, profile);
        setDirty();
    }

    public boolean removeProfile(UUID playerId) {
        boolean removed = store.remove(playerId);
        if (removed) setDirty();
        return removed;
    }

    public int profileCount() {
        return store.size();
    }

    private static PlayerArcanaProfilesSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(PROFILES_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("Saved Arcana player profile data is missing its UTF-8 payload.");
        }
        byte[] bytes = tag.getByteArray(PROFILES_KEY);
        if (bytes.length > PlayerArcanaProfileStore.MAX_STORE_JSON_BYTES) {
            throw new IllegalArgumentException("Saved Arcana player profile data exceeds the byte limit.");
        }
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Saved Arcana player profile data is not valid UTF-8.", exception);
        }
        PlayerArcanaProfileStore loaded = PlayerArcanaProfileStore.decode(json);
        return new PlayerArcanaProfilesSavedData(loaded);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        byte[] bytes = store.encode().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > PlayerArcanaProfileStore.MAX_STORE_JSON_BYTES) {
            throw new IllegalStateException("Saved Arcana player profile data exceeds the byte limit.");
        }
        tag.putByteArray(PROFILES_KEY, bytes);
        return tag;
    }
}
