package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The optional chunk loading of the trains (config value {@code rails.loadChunks}, off by default).
 *
 * <p>Each tick, a locomotive that runs and has fuel gives a chunk ticket to the chunk of each minecart of its train,
 * and to the chunks on its direction of travel for {@link #AHEAD} blocks. These chunks tick their entities, so the
 * train does not stop at the edge of the loaded area.
 *
 * <p>Each ticket has a lifespan of {@link #LIFESPAN} ticks. The locomotive gives its tickets again each tick, and the
 * game removes a ticket that the locomotive does not give again. Thus the tickets go away when the locomotive stops,
 * has no fuel, is removed or unloads, or when the option is off. The game does not save the tickets.
 */
public final class TrainChunks {
    /** The lifespan of a ticket, in ticks. */
    static final int LIFESPAN = 20;
    /** The distance of the area in front of the locomotive, in blocks. */
    public static final int AHEAD = 32;
    /** The interval between two positions in front of the locomotive, in blocks. Less than the width of a chunk. */
    private static final int STEP = 4;
    /** The distance of the tickets: 2 makes the chunk of the ticket tick its entities. */
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("folkhold_balance_train", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN);
    /** The locomotives that load chunks in each level, with the game time of their last tickets. */
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> LOADING = new HashMap<>();

    private TrainChunks() {
    }

    /**
     * Gives the tickets of a locomotive that runs and has fuel. Only {@code rails.maxLoadingTrains} locomotives in
     * each level give tickets. A locomotive that gives tickets keeps its place until it stops for {@link #LIFESPAN}
     * ticks.
     *
     * @param train the locomotive, then the minecarts behind it
     * @param dirX  the X part of the direction of travel
     * @param dirZ  the Z part of the direction of travel
     */
    static void keepLoaded(ServerLevel level, List<AbstractMinecart> train, double dirX, double dirZ) {
        if (!BalanceConfig.TRAIN_LOAD_CHUNKS.get() || train.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Map<UUID, Long> loading = LOADING.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        loading.values().removeIf(time -> now - time > LIFESPAN);
        UUID id = train.get(0).getUUID();
        if (!loading.containsKey(id) && loading.size() >= BalanceConfig.TRAIN_MAX_LOADING.get()) {
            return;
        }
        loading.put(id, now);
        LongSet chunks = new LongOpenHashSet();
        for (AbstractMinecart cart : train) {
            chunks.add(ChunkPos.asLong(cart.blockPosition()));
        }
        AbstractMinecart head = train.get(0);
        for (int distance = STEP; distance <= AHEAD; distance += STEP) {
            chunks.add(ChunkPos.asLong(SectionPos.blockToSectionCoord(head.getX() + dirX * distance),
                    SectionPos.blockToSectionCoord(head.getZ() + dirZ * distance)));
        }
        for (long chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk);
            level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LOADING.clear();
    }
}
