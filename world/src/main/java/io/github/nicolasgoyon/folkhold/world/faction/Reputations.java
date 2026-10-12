package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * Entry point for the reputation of players: read the effective value and the tier, apply an action.
 */
public final class Reputations {

    private Reputations() {
    }

    public static ReputationSettings settings() {
        return new ReputationSettings(WorldConfig.REPUTATION_VILLAGE_WEIGHT.get(),
                WorldConfig.REPUTATION_ALLY_SPREAD.get(), WorldConfig.REPUTATION_ENEMY_SPREAD.get());
    }

    /**
     * The effective reputation of a player with a faction, in a village if one is given.
     *
     * @return the value, or empty if the faction does not exist
     */
    public static Optional<Integer> effective(MinecraftServer server, Player player, String factionId,
            @Nullable String villageId) {
        FactionData factions = FactionData.get(server);
        return factions.byId(factionId).map(faction ->
                ReputationData.get(server).get(player.getUUID()).effective(faction, villageId, settings()));
    }

    /**
     * The effective tier of a player in a village. {@link ReputationTier#NEUTRAL} if the village has no faction.
     */
    public static ReputationTier tier(MinecraftServer server, Player player, VillageHeartBlockEntity heart) {
        if (heart.getFactionId() == null) {
            return ReputationTier.NEUTRAL;
        }
        return effective(server, player, heart.getFactionId(), heart.getVillageId().toString())
                .map(ReputationTier::of)
                .orElse(ReputationTier.NEUTRAL);
    }

    /**
     * The tier of a player with a faction (no village).
     */
    public static ReputationTier tier(MinecraftServer server, Player player, String factionId) {
        return effective(server, player, factionId, null).map(ReputationTier::of).orElse(ReputationTier.NEUTRAL);
    }

    /**
     * Applies an action of a player to a faction and a village, with the spread to the other factions.
     *
     * @param factionId the faction of the action
     * @param villageId the village of the action, or null
     * @param delta     the change of reputation
     */
    public static void apply(MinecraftServer server, Player player, String factionId, @Nullable String villageId,
            int delta) {
        FactionData factions = FactionData.get(server);
        Optional<FactionSpec> faction = factions.byId(factionId);
        if (faction.isEmpty() || delta == 0) {
            return;
        }
        ReputationData data = ReputationData.get(server);
        PlayerReputation reputation = data.get(player.getUUID());
        reputation.apply(faction.get(), villageId, delta, factions.factions(), factions.relations(), settings());
        data.setDirty();
    }
}
