package dev.arcana.profile;

import dev.arcana.casting.CastingTriggerRecognizer;
import dev.arcana.language.ArcanaLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerArcanaProfileStoreTest {
    private static PlayerArcanaProfile profile(String languageId) {
        return new PlayerArcanaProfile(
                new ArcanaLanguage.LanguageDefinition(
                        languageId, 1, Map.of("grim", "spell"), List.of()),
                new CastingTriggerRecognizer.TriggerProfile(
                        List.of("Astra"), List.of("Nora")));
    }

    @Test
    void storesProfilesIndependentlyByPlayerUuid() {
        var store = new PlayerArcanaProfileStore();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        store.put(first, profile("player:first"));
        store.put(second, profile("player:second"));

        assertEquals("player:first", store.get(first).orElseThrow().language().id());
        assertEquals("player:second", store.get(second).orElseThrow().language().id());
        assertEquals(2, store.size());
    }

    @Test
    void worldStoreRoundTripsProfilesAndIsDeterministic() {
        var first = new PlayerArcanaProfileStore();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        first.put(b, profile("player:second"));
        first.put(a, profile("player:first"));

        String encoded = first.encode();
        var restored = PlayerArcanaProfileStore.decode(encoded);

        assertEquals(first.snapshot(), restored.snapshot());
        assertEquals(encoded, restored.encode());
    }

    @Test
    void removingOnePlayerDoesNotAffectOtherProfiles() {
        var store = new PlayerArcanaProfileStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.put(a, profile("player:first"));
        store.put(b, profile("player:second"));

        assertTrue(store.remove(a));
        assertFalse(store.remove(a));
        assertTrue(store.get(a).isEmpty());
        assertTrue(store.get(b).isPresent());
    }

    @Test
    void snapshotsCannotMutateTheStore() {
        var store = new PlayerArcanaProfileStore();
        UUID id = UUID.randomUUID();
        store.put(id, profile("player:first"));

        var snapshot = store.snapshot();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(1, store.size());
    }

    @Test
    void rejectsUnknownStoreFieldsAndUnsupportedVersions() {
        String base = new PlayerArcanaProfileStore().encode();
        String unknown = base.replace("\"schemaVersion\":1",
                "\"schemaVersion\":1,\"extra\":true");
        String future = base.replace("\"schemaVersion\":1", "\"schemaVersion\":99");

        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileStore.decode(unknown));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileStore.decode(future));
    }

    @Test
    void rejectsInvalidPlayerIdsAndOversizedStores() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileStore.decode(
                        "{\"schemaVersion\":1,\"profiles\":{\"not-a-uuid\":{}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerArcanaProfileStore.decode(
                        " ".repeat(PlayerArcanaProfileStore.MAX_STORE_JSON_LENGTH + 1)));
    }

    @Test
    void nullPlayerIdsAndProfilesAreRejected() {
        var store = new PlayerArcanaProfileStore();
        assertThrows(IllegalArgumentException.class, () -> store.get(null));
        assertThrows(IllegalArgumentException.class, () -> store.put(UUID.randomUUID(), null));
    }
}
