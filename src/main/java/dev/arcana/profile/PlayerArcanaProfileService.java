package dev.arcana.profile;

import dev.arcana.casting.CastingTriggerRecognizer;
import dev.arcana.language.CastingPreparationPipeline;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logical-server access point for a player's world-scoped Arcana profile.
 * Client packets must call through server-side validation and must never
 * mutate SavedData directly.
 */
public final class PlayerArcanaProfileService {
    private PlayerArcanaProfileService() { }

    public static Optional<PlayerArcanaProfile> get(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return PlayerArcanaProfilesSavedData.get(player.serverLevel())
                .getProfile(player.getUUID());
    }

    public static void save(ServerPlayer player, PlayerArcanaProfile profile) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");
        PlayerArcanaProfilesSavedData.get(player.serverLevel())
                .putProfile(player.getUUID(), profile);
    }

    public static boolean remove(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return PlayerArcanaProfilesSavedData.get(player.serverLevel())
                .removeProfile(player.getUUID());
    }

    /** Creates the stateful trigger session for ordinary voice-command input. */
    public static Optional<dev.arcana.casting.CastingSessionController> newVoiceCommandSession(
            ServerPlayer player) {
        return get(player).map(profile ->
                new dev.arcana.casting.CastingSessionController(profile.triggers()));
    }

    /** Recognizes trigger markers only for the ordinary voice-command path. */
    public static CastingTriggerRecognizer.Result recognizeVoiceCommand(
            ServerPlayer player, String recognizedTranscript) {
        Optional<PlayerArcanaProfile> profile = get(player);
        if (profile.isEmpty()) {
            return new CastingTriggerRecognizer.Result(
                    CastingTriggerRecognizer.Status.INVALID_INPUT,
                    "", false, false, -1, -1,
                    "This player has no Arcana profile configured in the current world.");
        }
        return CastingTriggerRecognizer.recognize(
                recognizedTranscript, profile.get().triggers());
    }

    /** Dedicated casting-window preparation: source text is never trigger-scanned. */
    public static CastingPreparationPipeline.Result prepareCastingWindow(
            ServerPlayer player, String source) {
        Optional<PlayerArcanaProfile> profile = get(player);
        if (profile.isEmpty()) {
            return new CastingPreparationPipeline.Result(
                    CastingPreparationPipeline.Status.INVALID_REQUEST,
                    null, null, List.of(new dev.arcana.language.ArcanaLanguage.Diagnostic(
                            dev.arcana.language.ArcanaLanguage.Severity.ERROR,
                            "CAST_PROFILE_MISSING",
                            "This player has no Arcana language configured in the current world.",
                            dev.arcana.language.ArcanaLanguage.SourceSpan.point(0, 1, 1))));
        }
        return CastingPreparationPipeline.prepare(source, profile.get().language());
    }
}
