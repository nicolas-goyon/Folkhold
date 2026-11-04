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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The optional chunk loading of the trains (config value {@code rails.loadChunks}, off by default).
 *
 * <p>Each tick, a locomotive that runs, has fuel and moves gives a chunk ticket to the chunk of each minecart of its
 * train, and to the chunks on a straight line in front of it for {@link #AHEAD} blocks. The line follows the direction
 * of travel of the locomotive, not the rails. A ticket of distance 2 makes its chunk tick its entities, and makes the
 * 3 × 3 chunks around it tick their blocks (random ticks, crops, redstone). Thus the train does not stop at the edge
 * of the loaded area.
 *
 * <p>A locomotive gives tickets only while it moves: it must move in the last {@link #MOVE_GRACE} ticks. A train that
 * runs but cannot move (a wall, an unpowered powered rail, a slope that is too steep) does not keep its chunks loaded.
 * Only a real movement counts. A train that waits at the edge of the loaded area goes back to its position each tick
 * (see {@link Trains}): it gets its tickets when the locomotive gives it a speed for the next tick and moved in the
 * last {@link #WAIT_GRACE} ticks. Thus a train that cannot move and has a wagon at the edge of the loaded area does
 * not keep its chunks loaded.
 *
 * <p>While a locomotive gives tickets, it burns its fuel as at its maximum speed, also when it does not move (see
 * {@link Locomotive#tick()}). Thus the chunk loading always costs fuel.
 *
 * <p>Each ticket has a lifespan of {@link #LIFESPAN} ticks. The locomotive gives its tickets again each tick, and the
 * game removes a ticket that the locomotive does not give again. Thus the tickets go away when the locomotive stops
 * moving, stops, has no fuel, is removed or unloads, or when the option is off. The game does not save the tickets.
 *
 * <p>Only {@code rails.maxLoadingTrains} locomotives on the server give tickets at the same time, first come first
 * served. A locomotive keeps its place until it gives no tickets for {@link #LIFESPAN} ticks.
 */
public final class TrainChunks {
    /** The lifespan of a ticket, in ticks. */
    public static final int LIFESPAN = 20;
    /** The locomotive gives tickets when it moved in this number of ticks. */
    public static final int MOVE_GRACE = 20;
    /** A train that waits at the edge of the loaded area gets tickets when it moved in this number of ticks. */
    public static final int WAIT_GRACE = 200;
    /** The distance of the area in front of the locomotive, in blocks. */
    public static final int AHEAD = 32;
    /** The smallest movement in one tick that counts as a movement, in blocks. */
    private static final double MIN_MOVE = 1.0E-4;
    /** The interval between two positions in front of the locomotive, in blocks. Less than the width of a chunk. */
    private static final int STEP = 4;
    /** The distance of the tickets: 2 makes the chunk of the ticket tick its entities. */
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("folkhold_balance_train", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN);
    /** The server tick of the last real movement of each locomotive that runs. */
    private static final Map<UUID, Integer> MOVED = new HashMap<>();
    /** The locomotives that give tickets, with the server tick of their last tickets. */
    private static final Map<UUID, Integer> LOADING = new HashMap<>();

    private TrainChunks() {
    }

    /**
     * Gives the tickets of a locomotive that runs and has fuel, when it moved in the last {@link #MOVE_GRACE} ticks
     * (in the last {@link #WAIT_GRACE} ticks when its train waits at the edge of the loaded area) and has a place.
     *
     * @param train the locomotive, then the minecarts behind it
     * @param moved   the movement of the locomotive in this tick, in blocks
     * @param waiting true when the train waits at the edge of the loaded area and the locomotive gives it a speed
     * @param dirX    the X part of the direction of travel
     * @param dirZ    the Z part of the direction of travel
     */
    static void keepLoaded(ServerLevel level, List<AbstractMinecart> train, double moved, boolean waiting, double dirX,
                           double dirZ) {
        if (!BalanceConfig.TRAIN_LOAD_CHUNKS.get() || train.isEmpty()) {
            return;
        }
        int now = level.getServer().getTickCount();
        MOVED.values().removeIf(tick -> now - tick > WAIT_GRACE);
        LOADING.values().removeIf(tick -> now - tick > LIFESPAN);
        UUID id = train.get(0).getUUID();
        if (moved > MIN_MOVE) {
            MOVED.put(id, now);
        }
        Integer last = MOVED.get(id);
        if (last == null || now - last > (waiting ? WAIT_GRACE : MOVE_GRACE)
                || (!LOADING.containsKey(id) && LOADING.size() >= BalanceConfig.TRAIN_MAX_LOADING.get())) {
            return;
        }
        LOADING.put(id, now);
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

    /** Tells if a locomotive gave its tickets in this server tick or in the last server tick. */
    public static boolean isLoading(Entity locomotive) {
        Integer tick = LOADING.get(locomotive.getUUID());
        return tick != null && locomotive.getServer() != null && locomotive.getServer().getTickCount() - tick <= 1;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        MOVED.clear();
        LOADING.clear();
    }
}
