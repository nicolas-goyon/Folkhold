package io.github.nicolasgoyon.folkhold.balance;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Folkhold Balance mod.
 */
@Mod(FolkholdBalance.MOD_ID)
public final class FolkholdBalance {
    public static final String MOD_ID = "folkhold_balance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FolkholdBalance(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("{} {} loaded", MOD_ID, modContainer.getModInfo().getVersion());
    }
}
