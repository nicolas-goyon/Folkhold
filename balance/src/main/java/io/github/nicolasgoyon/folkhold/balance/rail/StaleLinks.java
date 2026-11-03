package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The links to remove from minecarts that are not in the level. When a minecart breaks a link and the level does not
 * give the other minecart, the other minecart keeps the link in its data. This file keeps the link until the other
 * minecart is in the level again and forgets it. File {@code data/folkhold_balance_stale_links.dat} of the Overworld.
 *
 * <p>An entry older than {@link #MAX_AGE_TICKS} is removed: the file does not grow without limit. A minecart that
 * loads after this time keeps its link to the destroyed minecart. Its train waits, and the player removes the link
 * with shears.
 */
public final class StaleLinks extends SavedData {
    /** The age of an entry before the file removes it: 7 game days, in ticks. */
    public static final long MAX_AGE_TICKS = 7L * 24000L;

    private static final String FILE_ID = "folkhold_balance_stale_links";
    private static final SavedData.Factory<StaleLinks> FACTORY =
            new SavedData.Factory<>(StaleLinks::new, StaleLinks::load, null);

    /**
     * The UUID of a minecart that is not in the level to the UUIDs of the minecarts that it must forget, each with the
     * game time of the Overworld when the link broke.
     */
    private final Map<UUID, Map<UUID, Long>> stale = new HashMap<>();

    public static StaleLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * Records a link to remove.
     *
     * @param cart     the minecart that is not in the level
     * @param partner  the minecart that the first minecart must forget
     * @param gameTime the game time of the Overworld
     */
    void add(UUID cart, UUID partner, long gameTime) {
        stale.computeIfAbsent(cart, id -> new HashMap<>()).put(partner, gameTime);
        setDirty();
    }

    /** Tells if a minecart has links to remove. */
    boolean has(UUID cart) {
        return stale.containsKey(cart);
    }

    /**
     * Removes one link to remove.
     *
     * @param cart    the minecart
     * @param partner the other minecart
     * @return true if the file had this link
     */
    boolean remove(UUID cart, UUID partner) {
        Map<UUID, Long> partners = stale.get(cart);
        if (partners == null || partners.remove(partner) == null) {
            return false;
        }
        if (partners.isEmpty()) {
            stale.remove(cart);
        }
        setDirty();
        return true;
    }

    /**
     * Removes and gives the links to remove from a minecart.
     *
     * @param cart the minecart
     * @return the minecarts that it must forget, or an empty set
     */
    Set<UUID> take(UUID cart) {
        Map<UUID, Long> partners = stale.remove(cart);
        if (partners == null) {
            return Set.of();
        }
        setDirty();
        return partners.keySet();
    }

    /**
     * Removes the entries older than {@link #MAX_AGE_TICKS}.
     *
     * @param gameTime the game time of the Overworld
     */
    void removeOld(long gameTime) {
        boolean changed = stale.values().removeIf(partners -> {
            partners.values().removeIf(time -> gameTime - time > MAX_AGE_TICKS);
            return partners.isEmpty();
        });
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stale.forEach((cart, partners) -> partners.forEach((partner, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("cart", cart);
            entry.putUUID("forget", partner);
            entry.putLong("time", time);
            list.add(entry);
        }));
        tag.put("stale", list);
        return tag;
    }

    private static StaleLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        StaleLinks data = new StaleLinks();
        for (Tag element : tag.getList("stale", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            data.stale.computeIfAbsent(entry.getUUID("cart"), id -> new HashMap<>())
                    .put(entry.getUUID("forget"), entry.getLong("time"));
        }
        return data;
    }
}
