package io.github.nicolasgoyon.folkhold.world;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.FriendlyFire;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MonsterTargeting;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModItems;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageSpawnProtection;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold World mod.
 */
@Mod(FolkholdWorld.MOD_ID)
public final class FolkholdWorld {
    public static final String MOD_ID = "folkhold_world";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdWorld(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, WorldConfig.SPEC);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(MonsterTargeting::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FriendlyFire::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VillageSpawnProtection::onPositionCheck);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> VillageHearts.clear());
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
