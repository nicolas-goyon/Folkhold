package io.github.nicolasgoyon.folkhold.world.registry;

import com.google.common.collect.ImmutableSet;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Villager professions of the world mod.
 *
 * <p>The merchant and the inhabitant have no job site block (see
 * {@link io.github.nicolasgoyon.folkhold.world.village.VillageFolk}). Its trades come from the stocks of its village (see
 * {@link io.github.nicolasgoyon.folkhold.world.economy.Merchants}).
 */
public final class ModProfessions {
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, FolkholdWorld.MOD_ID);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> MERCHANT = PROFESSIONS.register(
            "merchant", () -> new VillagerProfession("merchant", holder -> false, holder -> false, ImmutableSet.of(),
                    ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    /** Inhabitant of a faction village: no trades, no job site block. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> INHABITANT = PROFESSIONS.register(
            "inhabitant", () -> new VillagerProfession("inhabitant", holder -> false, holder -> false,
                    ImmutableSet.of(), ImmutableSet.of(), null));

    private ModProfessions() {
    }

    public static void register(IEventBus modEventBus) {
        PROFESSIONS.register(modEventBus);
    }
}
