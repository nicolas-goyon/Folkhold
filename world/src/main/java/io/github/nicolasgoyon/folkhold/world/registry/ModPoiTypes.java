package io.github.nicolasgoyon.folkhold.world.registry;

import com.google.common.collect.ImmutableSet;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Points of interest of the world mod: the places of the daily schedule of the workers (see
 * {@link io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains}).
 *
 * <p>The types have 0 tickets: no villager can claim them with the vanilla rules. The village heart sets the memories
 * {@code JOB_SITE} and {@code MEETING_POINT} of its workers directly.
 */
public final class ModPoiTypes {
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, FolkholdWorld.MOD_ID);

    /** The key of {@link #JOB_SIGN}. */
    public static final ResourceKey<PoiType> JOB_SIGN_KEY = key("job_sign");

    /** The key of {@link #MARKET_SIGN}. */
    public static final ResourceKey<PoiType> MARKET_SIGN_KEY = key("market_sign");

    /** All the states of the job sign: the job site of the household of a home. */
    public static final DeferredHolder<PoiType, PoiType> JOB_SIGN = POI_TYPES.register("job_sign",
            () -> type(ModBlocks.JOB_SIGN.get()));

    /** All the states of the market sign: the meeting point of the workers of a village with a market. */
    public static final DeferredHolder<PoiType, PoiType> MARKET_SIGN = POI_TYPES.register("market_sign",
            () -> type(ModBlocks.buildingSign(BuildingSignKind.MARKET)));

    private ModPoiTypes() {
    }

    public static void register(IEventBus modEventBus) {
        POI_TYPES.register(modEventBus);
    }

    private static ResourceKey<PoiType> key(String path) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path));
    }

    private static PoiType type(Block block) {
        return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 0, 1);
    }
}
