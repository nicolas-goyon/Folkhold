package io.github.nicolasgoyon.folkhold.world;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.world.command.FolkholdCommands;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.ai.FriendlyFire;
import io.github.nicolasgoyon.folkhold.world.entity.ai.MonsterTargeting;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationEvents;
import io.github.nicolasgoyon.folkhold.world.faction.TerritoryMessages;
import io.github.nicolasgoyon.folkhold.world.faction.VillageRules;
import io.github.nicolasgoyon.folkhold.world.group.CaravanInteractions;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupSystem;
import io.github.nicolasgoyon.folkhold.world.group.VillageTrade;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.registry.ModItems;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationTicker;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
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
        ModProfessions.register(modEventBus);
        ModItems.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(MonsterTargeting::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(FriendlyFire::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ReputationEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(ReputationEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(FolkholdCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(VillageRules::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(VillageRules::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(BankChests::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(BankChests::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(BankChests::onExplosion);
        NeoForge.EVENT_BUS.addListener(Merchants::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(VillageFolk::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(Merchants::onTrade);
        NeoForge.EVENT_BUS.addListener(VillageFolk::onDeath);
        NeoForge.EVENT_BUS.addListener(VillageFolk::onBabySpawn);
        NeoForge.EVENT_BUS.addListener(TerritoryMessages::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TerritoryMessages::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(TerritoryMessages::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VillageSpawnProtection::onPositionCheck);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> VillageHearts.clear());
        NeoForge.EVENT_BUS.addListener(SettlementDiscovery::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SettlementDiscovery::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SettlementDiscovery::onServerTick);
        NeoForge.EVENT_BUS.addListener(SimulationTicker::onServerTick);
        NeoForge.EVENT_BUS.addListener(SimulationTicker::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onServerTick);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onDeath);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onServerStopping);
        NeoForge.EVENT_BUS.addListener(GroupMaterializer::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CaravanInteractions::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(CaravanInteractions::onTrade);
        NeoForge.EVENT_BUS.addListener(CaravanInteractions::onDamage);
        NeoForge.EVENT_BUS.addListener(LevelTables::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(EconomyData::onAddReloadListener);
        SimulationTicker.addSystem(Economies::tick);
        SimulationTicker.addSystem(SettlementLevels::tick);
        SimulationTicker.addWorldSystem(MobileGroupSystem::tick);
        SimulationTicker.addWorldSystem(VillageTrade::tick);
        SettlementLevels.addProsperitySource(Economies::prosperity);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
