package io.github.nicolasgoyon.folkhold.balance;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.enchantment.BlockedEnchantmentIds;
import io.github.nicolasgoyon.folkhold.balance.enchantment.VillagerOffers;
import io.github.nicolasgoyon.folkhold.balance.network.BalanceNetwork;
import io.github.nicolasgoyon.folkhold.balance.rail.ChainUse;
import io.github.nicolasgoyon.folkhold.balance.rail.Trains;
import io.github.nicolasgoyon.folkhold.balance.registry.ModAttachments;
import io.github.nicolasgoyon.folkhold.balance.registry.ModLootModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Balance mod.
 */
@Mod(FolkholdBalance.MOD_ID)
public final class FolkholdBalance {
    public static final String MOD_ID = "folkhold_balance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdBalance(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, BalanceConfig.SPEC);
        ModAttachments.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        modEventBus.addListener(BlockedEnchantmentIds::onConfigLoad);
        modEventBus.addListener(BlockedEnchantmentIds::onConfigReload);
        modEventBus.addListener(BalanceNetwork::register);
        NeoForge.EVENT_BUS.addListener(ChainUse::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ChainUse::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ChainUse::onServerStopped);
        NeoForge.EVENT_BUS.addListener(Trains::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Trains::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(Trains::onEntityLeave);
        // Lowest priority: the links break only when no other listener cancels the travel.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Trains::onTravelToDimension);
        NeoForge.EVENT_BUS.addListener(Trains::onStartTracking);
        NeoForge.EVENT_BUS.addListener(Trains::onStopTracking);
        NeoForge.EVENT_BUS.addListener(Trains::onServerStopped);
        // Lowest priority: the filter also wraps the trade listings that other mods add.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerOffers::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerOffers::onWandererTrades);
        NeoForge.EVENT_BUS.addListener(VillagerOffers::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(VillagerOffers::onConversion);
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
