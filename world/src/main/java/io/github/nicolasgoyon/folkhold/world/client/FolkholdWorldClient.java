package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.client.map.XaeroMaps;
import io.github.nicolasgoyon.folkhold.world.client.renderer.AnimatedVillagerRenderer;
import io.github.nicolasgoyon.folkhold.world.client.renderer.FactionGolemModel;
import io.github.nicolasgoyon.folkhold.world.client.renderer.FactionGolemRenderer;
import io.github.nicolasgoyon.folkhold.world.client.renderer.FactionUnitRenderer;
import io.github.nicolasgoyon.folkhold.world.client.renderer.GolemArmbandLayer;
import io.github.nicolasgoyon.folkhold.world.config.ClientConfig;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of the world mod. The game loads this class only on the client.
 */
@Mod(value = FolkholdWorld.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdWorldClient {

    public FolkholdWorldClient(IEventBus modEventBus, ModContainer modContainer) {
        // Config screen in the mod list. Server values are editable only in a world.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modEventBus.addListener(FolkholdWorldClient::onRegisterRenderers);
        modEventBus.addListener(FolkholdWorldClient::onRegisterLayers);
        modEventBus.addListener(FolkholdWorldClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(FolkholdWorldClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(QuestTargetMarker::onRenderLiving);
        modEventBus.addListener(AnimatedVillagerRenderer::onAddLayers);
        // Lowest priority: the other listeners of the event see the villager before it changes renderer.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AnimatedVillagerRenderer::onRenderLiving);
        modEventBus.addListener(ConstructionKeys::register);
        modEventBus.addListener(JournalKeys::register);
        NeoForge.EVENT_BUS.addListener(JournalScreen::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientRouteView::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientRouteOverlay::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ConstructionGhost::onClientTick);
        NeoForge.EVENT_BUS.addListener(ConstructionGhostRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(XaeroMaps::onClientTick);
    }

    /** The player leaves a world or a server: the client forgets what it knows about the world. */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTerritory.clear();
        ClientTerritoryMap.clear();
        ClientMapTerritories.clear();
        ClientJournalMap.clear();
        ClientQuestLog.clear();
        TerritoryTitleHud.clear();
        AdminScreen.clear();
        JournalScreen.clear();
        ClientRouteView.clear();
        ClientRouteOverlay.clear();
        ConstructionScreen.clear();
        ConstructionGhost.clear();
        FactionGolemModel.forgetLevel();
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory_title"),
                TerritoryTitleHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory"),
                TerritoryHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quests"),
                QuestHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_ghost"),
                ConstructionGhost::renderHud);
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GolemArmbandLayer.LAYER, GolemArmbandLayer::createLayer);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUARD.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHER.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.CAPTAIN.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.HEALER.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.FACTION_GOLEM.get(), FactionGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.RIDER.get(), FactionUnitRenderer::new);
        event.registerEntityRenderer(ModEntities.FACTION_HORSE.get(), HorseRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGE_CAT.get(), CatRenderer::new);
        event.registerEntityRenderer(ModEntities.CARAVAN_MERCHANT.get(), WanderingTraderRenderer::new);
    }
}
