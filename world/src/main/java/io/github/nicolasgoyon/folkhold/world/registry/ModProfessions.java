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
 * <p>These professions have no job site block (see {@link io.github.nicolasgoyon.folkhold.world.village.VillageFolk}).
 * The trades of the merchant come from the stocks of its village (see
 * {@link io.github.nicolasgoyon.folkhold.world.economy.Merchants}). The other professions show the jobs of the
 * economy that no vanilla profession shows (the {@code profession} of a job file).
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

    /** Lumberjack, sawyer and carpenter: no trades, no job site block. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> LUMBERJACK = PROFESSIONS.register(
            "lumberjack", () -> new VillagerProfession("lumberjack", holder -> false, holder -> false,
                    ImmutableSet.of(), ImmutableSet.of(), null));

    /** Baker and pastry cook: no trades, no job site block. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BAKER = PROFESSIONS.register(
            "baker", () -> new VillagerProfession("baker", holder -> false, holder -> false,
                    ImmutableSet.of(), ImmutableSet.of(), null));

    /**
     * Builder: does the constructions of the village. No trades, no job site block. The model of a builder is
     * animated (see {@link io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers}).
     */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BUILDER = PROFESSIONS.register(
            "builder", () -> new VillagerProfession("builder", holder -> false, holder -> false,
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON));

    /**
     * Handyman: does the small work of the village. No trades, no job site block. The model of a handyman is
     * animated (see {@link io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers}).
     */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> HANDYMAN = PROFESSIONS.register(
            "handyman", () -> new VillagerProfession("handyman", holder -> false, holder -> false,
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_TOOLSMITH));

    private ModProfessions() {
    }

    public static void register(IEventBus modEventBus) {
        PROFESSIONS.register(modEventBus);
    }
}
