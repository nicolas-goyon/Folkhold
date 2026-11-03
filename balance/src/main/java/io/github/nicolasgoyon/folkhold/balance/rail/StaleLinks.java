package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The links to remove from minecarts that are not in the level. When a minecart breaks a link and the level does not
 * give the other minecart, the other minecart keeps the link in its data. This file keeps the link until the other
 * minecart is in the level again and forgets it. File {@code data/folkhold_balance_stale_links.dat} of the Overworld.
 */
public final class StaleLinks extends SavedData {
    private static final String FILE_ID = "folkhold_balance_stale_links";
    private static final SavedData.Factory<StaleLinks> FACTORY =
            new SavedData.Factory<>(StaleLinks::new, StaleLinks::load, null);

    /** The UUID of a minecart that is not loaded to the UUIDs of the minecarts that it must forget. */
    private final Map<UUID, Set<UUID>> stale = new HashMap<>();

    public static StaleLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * Records a link to remove.
     *
     * @param cart    the minecart that is not loaded
     * @param partner the minecart that the first minecart must forget
     */
    void add(UUID cart, UUID partner) {
        if (stale.computeIfAbsent(cart, id -> new HashSet<>()).add(partner)) {
            setDirty();
        }
    }

    /** Tells if a minecart has links to remove. */
    boolean has(UUID cart) {
        return stale.containsKey(cart);
    }

    /**
     * Removes and gives the links to remove from a minecart.
     *
     * @param cart the minecart
     * @return the minecarts that it must forget, or an empty set
     */
    Set<UUID> take(UUID cart) {
        Set<UUID> partners = stale.remove(cart);
        if (partners == null) {
            return Set.of();
        }
        setDirty();
        return partners;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stale.forEach((cart, partners) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("cart", cart);
            ListTag ids = new ListTag();
            partners.forEach(id -> ids.add(NbtUtils.createUUID(id)));
            entry.put("forget", ids);
            list.add(entry);
        });
        tag.put("stale", list);
        return tag;
    }

    private static StaleLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        StaleLinks data = new StaleLinks();
        for (Tag element : tag.getList("stale", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            Set<UUID> partners = new HashSet<>();
            for (Tag id : entry.getList("forget", Tag.TAG_INT_ARRAY)) {
                partners.add(NbtUtils.loadUUID(id));
            }
            data.stale.put(entry.getUUID("cart"), partners);
        }
        return data;
    }
}
