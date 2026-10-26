package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.journal.JournalData.Visit;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The journal of the players: the villages and the factions that each player discovered.
 *
 * <ul>
 *   <li>A player discovers a village when the player enters its territory, and visits it when the player comes near
 *       its heart (the village message, see {@link io.github.nicolasgoyon.folkhold.world.faction.TerritoryMessages}).
 *       A visit also discovers the village.</li>
 *   <li>The player discovers a faction with its first discovered village.</li>
 *   <li>The server keeps the journals in {@link JournalData}. The key of the journal (default J) asks for the journal
 *       of the player ({@link #request}).</li>
 *   <li>After a discovery, the maps of the player show the new territory (see {@link TerritoryMaps}).</li>
 * </ul>
 */
public final class Journals {
    private static final long DAY_TICKS = 24_000L;

    private Journals() {
    }

    /** The day of the world: 0 for the first day. */
    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / DAY_TICKS;
    }

    /**
     * Records the territory and the village of a player.
     *
     * @param player    the player
     * @param territory the settlement of the territory at the position of the player, or empty
     * @param village   the id of the settlement of the village near the player, or empty
     */
    public static void update(ServerPlayer player, Optional<Settlement> territory, Optional<String> village) {
        MinecraftServer server = player.server;
        JournalData data = JournalData.get(server);
        long day = day(server);
        boolean discovered = territory.map(settlement -> data.discover(player.getUUID(), settlement.id(), day))
                .orElse(false);
        boolean visited = village.map(id -> data.visit(player.getUUID(), id, day)).orElse(false);
        if (discovered || visited) {
            TerritoryMaps.update(player);
        }
    }

    /** Sends the journal to a player. */
    public static void request(ServerPlayer player) {
        ModNetwork.send(player, payload(player));
    }

    /**
     * The journal of a player.
     *
     * @param player the player
     * @return the payload: the villages that the server knows, nearest first, and their factions
     */
    public static JournalPayload payload(ServerPlayer player) {
        MinecraftServer server = player.server;
        SettlementData settlements = SettlementData.get(server);
        FactionData factionData = FactionData.get(server);
        List<JournalPayload.Village> villages = new ArrayList<>();
        Map<String, Integer> villageCounts = new LinkedHashMap<>();
        for (Map.Entry<String, Visit> entry : JournalData.get(server).visits(player.getUUID()).entrySet()) {
            Optional<Settlement> found = settlements.registry().byId(entry.getKey());
            if (found.isEmpty()) {
                continue;
            }
            Settlement settlement = found.get();
            Optional<FactionSpec> faction = factionData.byId(settlement.factionId());
            Component factionName = faction.<Component>map(FactionVisuals::name)
                    .orElse(Component.literal(settlement.factionId()));
            ReputationTier tier = Reputations.effective(server, player, settlement.factionId(), settlement.id())
                    .map(ReputationTier::of).orElse(ReputationTier.NEUTRAL);
            int distance = (int) Math.round(Math.hypot(settlement.x() - player.getX(), settlement.z() - player.getZ()));
            Visit visit = entry.getValue();
            villages.add(new JournalPayload.Village(settlement.id(), settlement.name(), settlement.factionId(),
                    factionName, settlement.level(), SettlementLevels.tierName(settlement.level()),
                    settlement.isActive(), settlement.x(), settlement.z(), distance, tier, visit.discovered(),
                    visit.lastVisit(), VillageFolk.inhabitantCount(settlement), VillageBeds.places(settlement)));
            villageCounts.merge(settlement.factionId(), 1, Integer::sum);
        }
        villages.sort(Comparator.comparingInt(JournalPayload.Village::distance));
        List<JournalPayload.Faction> factions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : villageCounts.entrySet()) {
            factionData.byId(entry.getKey()).ifPresent(faction -> {
                int value = Reputations.effective(server, player, faction.id(), null).orElse(0);
                Component culture = Cultures.byId(faction.cultureId())
                        .<Component>map(found -> Component.translatable(found.translationKey()))
                        .orElse(Component.literal(faction.cultureId()));
                Component attitude = Component.translatable("folkhold_world.attitude."
                        + faction.attitude().name().toLowerCase(Locale.ROOT));
                factions.add(new JournalPayload.Faction(faction.id(), FactionVisuals.name(faction), culture, attitude,
                        ReputationTier.of(value), value, entry.getValue()));
            });
        }
        return new JournalPayload(villages, factions, day(server));
    }
}
